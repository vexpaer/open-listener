package io.github.vexpaer.openlistener

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.vexpaer.openlistener.service.MicrophoneRecognitionService
import io.github.vexpaer.openlistener.service.RecognitionRuntime
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual microphone-type FGS and Room pipeline with explicitly synthetic in-RAM input. */
class ForegroundSessionAndroidTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as TestListenerApplication

    @Test fun visibleStart_activityLeaves_lockScreen_notificationPauseResumeStop_preservesText() = runBlocking {
        app.recovery.await()
        app.preferences.setRetainAudio(false)
        app.syntheticSession = true
        val activity = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            activity.onActivity { current ->
                ContextCompat.startForegroundService(current, Intent(current, MicrophoneRecognitionService::class.java)
                    .setAction(MicrophoneRecognitionService.ACTION_START))
            }
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.RECORDING }
            val id = requireNotNull(RecognitionRuntime.sessionId.value)
            await { app.repository.listSegments(id).size >= 2 }
            val before = app.repository.listSegments(id).size
            val beforeElapsed = RecognitionRuntime.elapsedMs.value
            activity.moveToState(Lifecycle.State.CREATED)
            shell("input keyevent 223") // Sleep, not a toggle dependent on the current state.
            delay(2_500)
            assertEquals(MicrophoneRecognitionService.State.RECORDING, RecognitionRuntime.state.value)
            assertTrue(app.repository.listSegments(id).size > before)
            assertTrue(RecognitionRuntime.elapsedMs.value > beforeElapsed)
            val notification = app.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 1001 }.notification
            notification.actions.first().actionIntent.send()
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.PAUSED }
            val paused = app.repository.listSegments(id).size
            delay(800)
            assertEquals(paused, app.repository.listSegments(id).size)
            app.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 1001 }
                .notification.actions.first().actionIntent.send()
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.RECORDING }
            await { app.repository.listSegments(id).size > paused }
            app.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 1001 }
                .notification.actions.last().actionIntent.send()
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.FINISHED }
            val stored = app.repository.listSegments(id)
            assertTrue(stored.isNotEmpty())
            assertTrue(stored.all { it.translationStatus == "COMPLETED" })
            assertEquals("FINISHED", app.repository.getSession(id)!!.state)
            assertNull(app.repository.getSession(id)!!.audioPath)
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_STOP))
            delay(200)
            activity.close()
            app.syntheticSession = false
        }
    }
    @Test fun retainedAudioSurvivesPauseAndStop_andDeletionRemovesFile() = runBlocking {
        app.recovery.await()
        app.preferences.setRetainAudio(true)
        app.preferences.setLanguages("en", "zh-CN")
        app.syntheticSession = true
        val activity = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            activity.onActivity { current ->
                ContextCompat.startForegroundService(current, Intent(current, MicrophoneRecognitionService::class.java)
                    .setAction(MicrophoneRecognitionService.ACTION_START))
            }
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.RECORDING }
            val id = requireNotNull(RecognitionRuntime.sessionId.value)
            await { app.repository.listSegments(id).isNotEmpty() }
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_PAUSE))
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.PAUSED }
            val file = java.io.File(requireNotNull(app.repository.getSession(id)!!.audioPath))
            val pausedSize = file.length()
            delay(300); assertEquals(pausedSize, file.length())
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_RESUME))
            await { file.length() > pausedSize }
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_STOP))
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.FINISHED }
            val bytes = file.readBytes()
            assertTrue(bytes.size > 44)
            assertEquals(bytes.size - 44, java.nio.ByteBuffer.wrap(bytes, 40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
            app.repository.deleteSession(id)
            assertFalse(file.exists())
        } finally {
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_STOP))
            delay(200); activity.close(); app.syntheticSession = false; app.preferences.setRetainAudio(false)
        }
    }

    @Test fun integratedSpeechPersistsBothTexts_withoutAnySecondTranslator() = runBlocking {
        app.recovery.await()
        app.preferences.setRetainAudio(false)
        app.preferences.setLanguages("en", "zh-CN")
        app.preferences.setProviders("doubao_ast", "custom")
        app.syntheticSession = true; app.syntheticBilingual = true
        val activity = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            activity.onActivity { current -> ContextCompat.startForegroundService(current,
                Intent(current, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_START)) }
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.RECORDING }
            val id = requireNotNull(RecognitionRuntime.sessionId.value)
            await { app.repository.listSegments(id).any { it.translationStatus == "COMPLETED" } }
            val segment = app.repository.listSegments(id).first()
            assertEquals("仅测试构建中的同传译文", segment.translated)
            assertEquals("doubao_ast", app.repository.getSession(id)!!.translatorProvider)
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_STOP))
            await { RecognitionRuntime.state.value == MicrophoneRecognitionService.State.FINISHED }
        } finally {
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_STOP))
            delay(200); activity.close(); app.syntheticSession = false; app.syntheticBilingual = false
            app.preferences.setProviders("local", "mlkit")
        }
    }

    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }
    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(20_000) { while (!condition()) delay(50) }
}
