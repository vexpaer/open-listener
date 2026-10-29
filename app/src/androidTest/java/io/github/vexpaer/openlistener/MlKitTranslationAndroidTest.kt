package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.asr.EngineAvailability
import io.github.vexpaer.openlistener.translation.MlKitTranslation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Requires real Google model download; network failures fail explicitly rather than skip. */
class MlKitTranslationAndroidTest {
    @Test fun downloadsChineseModelAndTranslatesBothDirections() = runBlocking {
        withTimeout(10 * 60_000L) {
            val forward = MlKitTranslation("en", "zh-CN")
            val reverse = MlKitTranslation("zh-CN", "en")
            try {
                forward.downloadModels("en", "zh-CN")
                assertEquals(EngineAvailability.Ready, forward.prepare())
                val chinese = forward.translate(1, "Good morning. Thank you.", "en", "zh-CN")
                assertEquals("COMPLETED", chinese.status)
                assertTrue(!chinese.text.isNullOrBlank())
                val english = reverse.translate(2, "早上好，谢谢。", "zh-CN", "en")
                assertEquals("COMPLETED", english.status)
                assertTrue(!english.text.isNullOrBlank())
            } finally { forward.close(); reverse.close() }
        }
    }
}
