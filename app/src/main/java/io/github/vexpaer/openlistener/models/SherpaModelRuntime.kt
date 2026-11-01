package io.github.vexpaer.openlistener.models

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/** Official Kotlin API from sherpa-onnx v2.0.1; no reflection or alternate API guesses. */
internal object SherpaModelRuntime {
    fun create(directory: File, manifest: ModelManifest = ModelCatalog.models.first()): OnlineRecognizer = OnlineRecognizer(
        assetManager = null,
        config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = requireNotNull(ModelFileMatcher.resolve(directory, manifest.files.first { it.path.startsWith("encoder") }.path)).absolutePath,
                    decoder = requireNotNull(ModelFileMatcher.resolve(directory, manifest.files.first { it.path.startsWith("decoder") }.path)).absolutePath,
                    joiner = requireNotNull(ModelFileMatcher.resolve(directory, manifest.files.first { it.path.startsWith("joiner") }.path)).absolutePath
                ),
                tokens = File(directory, "tokens.txt").absolutePath,
                modelType = "", // Let upstream inspect the ONNX metadata for the correct Zipformer generation.
                provider = "cpu",
                numThreads = 2,
                debug = false
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search"
        )
    )

    fun createOffline(directory: File, manifest: ModelManifest): OfflineRecognizer {
        fun file(name: String) = requireNotNull(ModelFileMatcher.resolve(directory, name)) {
            "Missing verified model file: $name"
        }.absolutePath
        val modelConfig = when (manifest.runtime) {
            "sensevoice" -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = file("model.int8.onnx"), language = "auto",
                    useInverseTextNormalization = true),
                tokens = file("tokens.txt"), numThreads = 2, provider = "cpu")
            "moonshine" -> OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = file("encoder_model.ort"),
                    mergedDecoder = file("decoder_model_merged.ort")),
                tokens = file("tokens.txt"), numThreads = 2, provider = "cpu")
            else -> error("Unsupported offline model runtime: ${manifest.runtime}")
        }
        return OfflineRecognizer(assetManager = null, config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = modelConfig))
    }

    /** Load and exercise the native graph before the staging directory becomes installed. */
    fun validate(directory: File, manifest: ModelManifest = ModelCatalog.models.first()) {
        if (manifest.runtime != "online") {
            val offline = createOffline(directory, manifest)
            try {
                val stream = offline.createStream()
                try {
                    stream.acceptWaveform(FloatArray(16_000), 16_000)
                    offline.decode(stream)
                    offline.getResult(stream)
                } finally { stream.release() }
            } finally { offline.release() }
            return
        }
        val recognizer = create(directory, manifest)
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(FloatArray(8_000), 16_000)
                stream.inputFinished()
                var steps = 0
                while (recognizer.isReady(stream)) {
                    check(++steps <= 512) { "Model validation decoder did not drain" }
                    recognizer.decode(stream)
                }
                recognizer.getResult(stream)
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }
}
