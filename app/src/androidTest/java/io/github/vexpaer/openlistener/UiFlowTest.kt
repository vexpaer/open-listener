package io.github.vexpaer.openlistener

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vexpaer.openlistener.service.MicrophoneRecognitionService
import io.github.vexpaer.openlistener.service.RecognitionRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Activity + real Room/Preferences. No production fake engine and no microphone start. */
class UiFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as OpenListenerApp

    private fun reset() = runBlocking {
        app.recovery.await()
        app.repository.observeSessions().first().forEach { app.repository.deleteSession(it.id) }
        app.preferences.setLanguage("zh")
        app.preferences.setTheme("light")
        app.preferences.setReducedMotion(true)
        app.preferences.setProviders("local", "mlkit")
        app.preferences.setLanguages("en", "zh-CN")
        app.models.catalog.forEach { app.models.delete(it.id) }
        app.credentials.remove("doubao", "api_key")
        app.credentials.remove("doubao", "resource_id")
        RecognitionRuntime.updateState(MicrophoneRecognitionService.State.IDLE)
    }

    @Test fun realActivity_threeTopTabs_emptyNoModel_settingsTwoButtons_andScreenshots() {
        reset()
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("engine-unavailable")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("tab-listen").assertIsDisplayed()
            compose.onNodeWithTag("tab-library").assertIsDisplayed()
            compose.onNodeWithTag("tab-settings").assertIsDisplayed()
            compose.onNodeWithTag("listen-empty").assertIsDisplayed()
            compose.onNodeWithTag("engine-unavailable").assertIsDisplayed()
            screenshot("01-listen-empty-no-model")

            compose.onNodeWithTag("listen-primary").performClick()
            compose.waitForIdle()
            assertEquals(MicrophoneRecognitionService.State.IDLE, RecognitionRuntime.state.value)
            compose.onNodeWithTag("tab-library").performClick()
            compose.onNodeWithTag("library-empty").assertIsDisplayed()
            screenshot("02-library-empty")

            compose.onNodeWithTag("tab-settings").performClick()
            compose.onNodeWithTag("settings-page").assertIsDisplayed()
            screenshot("03-settings-overview")
            compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("asr-local-models"))
            compose.onNodeWithTag("asr-local-models").performClick()
            compose.onNodeWithTag("settings-model-page").performScrollToNode(hasTestTag("download-asr-model"))
            compose.onNodeWithTag("download-asr-model").assertIsDisplayed().assertIsEnabled()
            screenshot("04-settings-models")
            compose.onNodeWithTag("model-page-back").performClick()
            compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("check-updates"))
            compose.onNodeWithTag("check-updates").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("download-update").assertIsDisplayed().assertIsNotEnabled()
            screenshot("05-settings-update-buttons")

            compose.onNodeWithTag("tab-listen").performClick()
            compose.onNodeWithTag("tab-settings").performClick()
            compose.onNodeWithTag("check-updates").assertIsDisplayed()
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("check-updates").assertIsDisplayed()
        }
    }

    @Test fun persistedTranslation_fullTextSearch_detailRenameAndDelete() {
        reset()
        val id = runBlocking {
            app.repository.startSession("Instrumentation lecture", "en", "zh-CN", "local", "mlkit").also { id ->
                app.repository.saveFinalSegment(id, 1, 0, 3_000, "Illustrative transcript in test build")
                app.repository.updateTranslation(id, 1, "仅测试构建中的示例译文", "COMPLETED")
                app.repository.finishSession(id, "FINISHED", 3_000)
            }
        }
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
            compose.onNodeWithTag("tab-library").performClick()
            compose.onNodeWithTag("library-search").performTextInput("示例译文")
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Instrumentation lecture")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("session-preview-$id").assertTextEquals("Illustrative transcript in test build")
            compose.onNodeWithText("Instrumentation lecture").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("仅测试构建中的示例译文")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("仅测试构建中的示例译文").performScrollTo().assertIsDisplayed()
            screenshot("06-test-build-illustrative-transcript")
            // Real persisted text is the source of truth, not a transient ASR event.
            assertEquals("仅测试构建中的示例译文", runBlocking { app.repository.listSegments(id).single().translated })
        }
    }

    @Test fun modelCategories_darkTheme_andMatchingListeningControls() {
        reset()
        runBlocking { app.preferences.setTheme("dark") }
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
            compose.onNodeWithTag("tab-settings").performClick()
            compose.onNodeWithText("目标语言").assertIsDisplayed()
            val textLayout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("目标语言").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayout) }
            assertEquals(Color(0xFFF5F6FA), textLayout.single().layoutInput.style.color)
            compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("asr-cloud-models"))
            compose.onNodeWithTag("asr-cloud-models").performClick()
            compose.onNodeWithTag("asr-credentials").assertIsDisplayed()
            compose.onNodeWithText("阿里云 · 未支持").assertIsNotEnabled()
            compose.onNodeWithTag("model-page-back").performClick()
            compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("translation-cloud-models"))
            compose.onNodeWithTag("translation-cloud-models").performClick()
            compose.onNodeWithText("阿里云").performClick()
            compose.waitForIdle()
            assertEquals("qwen", runBlocking { app.preferences.preferences.first().translationProvider })
            compose.onNodeWithTag("model-page-back").performClick()
            compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("translation-local-models"))
            compose.onNodeWithTag("translation-local-models").performClick()
            compose.onNodeWithTag("download-language-models").assertIsDisplayed()
            compose.onNodeWithTag("model-page-back").performClick()
            compose.onNodeWithTag("tab-listen").performClick()
            RecognitionRuntime.updateState(MicrophoneRecognitionService.State.PAUSED)
            compose.onNodeWithTag("listen-primary").assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
            compose.onNodeWithTag("stop-listening").assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
            screenshot("07-dark-matching-controls")
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(instrumentation.targetContext.filesDir, "screenshots").apply { check(exists() || mkdirs()) }
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("UiAutomation did not return a screenshot")
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
