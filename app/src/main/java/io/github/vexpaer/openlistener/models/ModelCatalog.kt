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
        )
    )
}
