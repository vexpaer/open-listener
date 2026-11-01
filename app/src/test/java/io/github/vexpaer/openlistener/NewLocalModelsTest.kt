package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.models.ModelCatalog
import org.junit.Assert.*
import org.junit.Test

class NewLocalModelsTest {
    @Test fun allFourModelFamiliesHavePinnedOfficialArchivesAndRuntimeRoutes() {
        val ids = listOf(
            "sensevoice-small-int8-2024-07-17",
            "x-asr-480ms-zh-en-punct-int8-2026-06-05",
            "moonshine-v2-tiny-en-2026-02-27",
            "moonshine-v2-base-en-2026-02-27"
        )
        ids.forEach { id ->
            val model = ModelCatalog.models.single { it.id == id }
            assertEquals(64, model.sha256.length)
            assertTrue(model.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"))
            assertTrue(model.files.any { it.path == "tokens.txt" })
            assertTrue(model.files.all { it.path.isNotBlank() && !it.path.startsWith("/") && !it.path.contains("..") })
        }
        assertEquals(1, ids.count { id -> ModelCatalog.models.single { it.id == id }.streaming })
    }

    @Test fun offlineMoonshineAndSenseVoiceUseDifferentNativeConfigurations() {
        assertEquals("offline-sensevoice", ModelCatalog.models.single { it.id.startsWith("sensevoice") }.backend)
        assertEquals(2, ModelCatalog.models.count { it.backend == "offline-moonshine-v2" })
        assertTrue(ModelCatalog.models.single { it.id.startsWith("x-asr") }.streaming)
    }
}
