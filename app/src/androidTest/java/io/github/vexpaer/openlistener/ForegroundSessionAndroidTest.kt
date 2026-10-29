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
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_STOP))
            delay(200)
            activity.close()
            app.syntheticSession = false
        }
    }
    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }
    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(20_000) { while (!condition()) delay(50) }
}
