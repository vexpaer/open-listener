package io.github.vexpaer.openlistener.models

data class ModelFile(val path: String, val bytes: Long, val sha256: String)

data class ModelManifest(
    val id: String,
    val displayName: String,
    val version: String,
    val languages: Set<String>,
    val streaming: Boolean,
    val license: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val archiveRoot: String,
    val files: List<ModelFile>
) {
    val installedBytes: Long get() = files.sumOf { it.bytes }
}

/** Hashes measured from the official release, not inferred from filenames. */
object ModelCatalog {
    const val DEFAULT_ID = "zipformer-bilingual-zh-en-2023-02-20-mobile"
    const val SHERPA_VERSION = "2.0.1"
    val models: List<ModelManifest> = listOf(
        ModelManifest(
            id = DEFAULT_ID,
            displayName = "Zipformer · 中文 / English (INT8)",
            version = "2023-02-20-mobile",
            languages = setOf("zh-CN", "en"),
            streaming = true,
            license = "Apache-2.0 (upstream model card)",
            licenseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/blob/main/README.md",
            sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile.tar.bz2",
            bytes = 346_965_352,
            sha256 = "45b8d04fe8faf5146397ff2e71d90ca8effdfcbe9adc30fdce769e92cabd6b03",
            archiveRoot = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile",
            files = listOf(
                ModelFile("encoder-epoch-99-avg-1.int8.onnx", 105_276_499,
                    "f559f422d970323e0c266aaa3c56cf6d1a3a4bc0c0822124727686c1e43e9447"),
                ModelFile("decoder-epoch-99-avg-1.onnx", 13_876_452,
                    "2e3b5ec371f8899ee6acd829fd753ba45772df57a91bdf37cde3136354e7db7d"),
                ModelFile("joiner-epoch-99-avg-1.int8.onnx", 3_228_404,
                    "1ed689c5ed19dbaa725d9d191bb4822b5f4855a39e1ffd28cbc1f340d25b2ee0"),
                ModelFile("tokens.txt", 56_317,
                    "a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3")
            )
        ),
        ModelManifest(
            id = "zipformer-en-2023-06-26-mobile",
            displayName = "Zipformer · English (INT8)",
            version = "2023-06-26-mobile",
            languages = setOf("en"), streaming = true,
            license = "Apache-2.0",
            licenseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/blob/main/README.md",
            sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26-mobile.tar.bz2",
            bytes = 305_410_008,
            sha256 = "4dad6f4849cad551042eb875505b3fdc0fac9f1c88d709165c48b921681e61dc",
            archiveRoot = "sherpa-onnx-streaming-zipformer-en-2023-06-26-mobile",
            files = listOf(
                ModelFile("joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx", 259416, "abd5e30f3f16fc510605c6029dba33f10e4386bd75c5bdc30cf94076864db10d"),
                ModelFile("tokens.txt", 5048, "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb"),
                ModelFile("encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx", 67963182, "ac01b68c61d1a716d90baea606b9683ddab07fbd097fc54398d19b027f390ef1"),
                ModelFile("decoder-epoch-99-avg-1-chunk-16-left-128.onnx", 2093080, "85914f840b71110487ece7a30ebd0bd4b8fb5c2623215bba3aa5ab4c138fc591")
            )
        ),
        ModelManifest(
            id = "zipformer-en-gigaspeech-2023-06-21-mobile",
            displayName = "Zipformer · English GigaSpeech (INT8)",
            version = "2023-06-21-mobile",
            languages = setOf("en"), streaming = true,
            license = "Apache-2.0",
            licenseUrl = "https://huggingface.co/marcoyang/icefall-libri-giga-pruned-transducer-stateless7-streaming-2023-04-04",
            sourceUrl = "https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-21-mobile.tar.bz2",
            bytes = 365_748_162,
            sha256 = "b97e7ff75a27136f4dd33c99bb7d5f493094397501be4739578c6cd95ea7f422",
            archiveRoot = "sherpa-onnx-streaming-zipformer-en-2023-06-21-mobile",
            files = listOf(
                ModelFile("joiner-epoch-99-avg-1.int8.onnx", 259335,
                    "831477d390e59a61f1b6a6f763b9903e6c6366ff6034f1ddba613be82637122f"),
                ModelFile("decoder-epoch-99-avg-1.onnx", 2092566,
                    "9da02b77cb08826756ec6a88635f35a40374e4164e7c6359121a9145958a6ceb"),
                ModelFile("tokens.txt", 5048,
                    "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb"),
                ModelFile("encoder-epoch-99-avg-1.int8.onnx", 111205459,
                    "c0391141c0faf05142004b96dd5e66830a737386b00a5e638d0a228f051a7b60")
            )
        )
    )
}
