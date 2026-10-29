package io.github.vexpaer.openlistener.models

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
                    encoder = File(directory, manifest.files.first { it.path.startsWith("encoder") }.path).absolutePath,
                    decoder = File(directory, manifest.files.first { it.path.startsWith("decoder") }.path).absolutePath,
                    joiner = File(directory, manifest.files.first { it.path.startsWith("joiner") }.path).absolutePath
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

    /** Load and exercise the native graph before the staging directory becomes installed. */
    fun validate(directory: File, manifest: ModelManifest = ModelCatalog.models.first()) {
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
