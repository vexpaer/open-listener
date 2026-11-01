package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.models.ModelCatalog
import io.github.vexpaer.openlistener.models.ModelFileMatcher
import org.junit.Assert.*
import org.junit.Test

class ModelSelectionCatalogTest {
    @Test fun modernModelsHaveOfficialArchiveIntegrityAndValidRuntime() {
        val expected = mapOf(
            "sensevoice-small-int8-2024-07-17" to "sensevoice",
            "x-asr-480ms-zh-en-punct-int8-2026-06-05" to "online",
            "moonshine-v2-en-tiny-2026-02-27" to "moonshine",
            "moonshine-v2-en-base-2026-02-27" to "moonshine"
        )
        expected.forEach { (id, runtime) ->
            val model = ModelCatalog.models.single { it.id == id }
            assertEquals(runtime, model.runtime)
            assertTrue(model.sha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(model.bytes > 1_000_000)
            assertTrue(model.files.isNotEmpty())
            assertTrue(model.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/"))
        }
    }

    @Test fun wildcardFilenameCannotEscapeRoleOrMatchUnrelatedWeight() {
        assertTrue(ModelFileMatcher.matches("encoder*.onnx", "encoder-epoch-99-avg-1.int8.onnx"))
        assertTrue(ModelFileMatcher.matches("encoder*.onnx", "encoder.onnx"))
        assertFalse(ModelFileMatcher.matches("encoder*.onnx", "decoder.onnx"))
        assertFalse(ModelFileMatcher.matches("encoder*.onnx", "../encoder.onnx"))
        assertFalse(ModelFileMatcher.matches("encoder*.onnx", "subdir/encoder.onnx"))
        assertTrue(ModelFileMatcher.matches("tokens.txt", "tokens.txt"))
        assertFalse(ModelFileMatcher.matches("tokens.txt", "tokens2.txt"))
    }
}
