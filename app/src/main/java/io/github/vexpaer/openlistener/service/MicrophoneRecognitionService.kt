package io.github.vexpaer.openlistener.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.vexpaer.openlistener.MainActivity
import io.github.vexpaer.openlistener.OpenListenerApp
import io.github.vexpaer.openlistener.R
import io.github.vexpaer.openlistener.asr.*
import io.github.vexpaer.openlistener.audio.AudioPcmCapture
import io.github.vexpaer.openlistener.translation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Locale

/** Capture is owned here, never by Activity. No restart from boot/background/START_STICKY. */
class MicrophoneRecognitionService : Service() {
    enum class State { IDLE, PREPARING, RECORDING, PAUSED, STOPPING, FINISHED, ERROR, INTERRUPTED }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val control = Mutex()
    private lateinit var capture: io.github.vexpaer.openlistener.audio.PcmCapture
    private val assembler = SegmentAssembler()
    private lateinit var app: OpenListenerApp
    private var engine: AsrEngine? = null
    private var translator: TranslationEngine? = null
    private var queue: TranslationQueue? = null
    private var id: String? = null
    private var consumer: Job? = null
    private var events: Job? = null
    private var errors: Job? = null
    private var ticker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var state = State.IDLE
    @Volatile private var terminating = false

    override fun onCreate() {
        super.onCreate()
        app = application as OpenListenerApp
        capture = app.newCapture()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote immediately after a visible start; engine/model/network preparation can take seconds.
        if (intent?.action == ACTION_START && state == State.IDLE) {
            try {
                check(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    "Microphone permission denied; grant it from the visible Listen page"
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Preparing"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } catch (e: Exception) { RecognitionRuntime.fail(e.message ?: "Foreground service could not start"); stopSelf(); return START_NOT_STICKY }
        }
        scope.launch {
            control.withLock {
                try {
                    when (intent?.action) {
                        ACTION_START -> if (state == State.IDLE) start()
                        ACTION_PAUSE -> if (state == State.RECORDING) pause()
                        ACTION_RESUME -> if (state == State.PAUSED) resume()
                        ACTION_STOP -> if (!terminating) finish(State.FINISHED)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { finish(State.ERROR, e.message ?: "Listening failed; restart from the visible app") }
                catch (_: LinkageError) { finish(State.ERROR, "Native runtime is missing or incompatible; reinstall a verified APK") }
            }
        }
        return START_NOT_STICKY
    }
    private suspend fun start() {
        setState(State.PREPARING)
        app.recovery.await()
        val prefs = app.preferences.preferences.first()
        engine = app.newAsr(prefs)
        require(prefs.sourceLanguage in engine!!.capabilities.languages) { "Selected speech engine does not support this source language" }
        translator = app.newTranslator(prefs)
        check(engine!!.prepare() == EngineAvailability.Ready) { "Speech engine needs a model or credentials; open Settings" }
        check(translator!!.prepare() == EngineAvailability.Ready) { "Translation engine needs language models or credentials; open Settings" }
        id = app.repository.startSession("Lecture · " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(System.currentTimeMillis()),
            prefs.sourceLanguage, prefs.targetLanguage, prefs.asrProvider, prefs.translationProvider)
        RecognitionRuntime.begin(id!!)
        queue = TranslationQueue(translator!!, app.repository, prefs.sourceLanguage, prefs.targetLanguage, scope)
        events = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                engine!!.events.collect { event ->
                    if (assembler.accept(event)) {
                        if (event.partial) RecognitionRuntime.updatePartial(event.takeIf { it.text.isNotBlank() })
                        else if (event.text.isBlank()) {
                            if (RecognitionRuntime.partial.value?.segmentId == event.segmentId) RecognitionRuntime.updatePartial(null)
                        } else {
                            if (RecognitionRuntime.partial.value?.segmentId == event.segmentId) RecognitionRuntime.updatePartial(null)
                            if (app.repository.saveFinalSegment(event.sessionId, event.segmentId, event.startOffsetMs,
                                event.endOffsetMs, event.text, event.revision)) queue?.offer(event)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportFailure(e) }
        }
        (engine as? DoubaoRealtimeAsr)?.let { cloud ->
            errors = scope.launch { cloud.errors.collect { error -> if (error != null && !terminating) reportFailure(error) } }
        }
        engine!!.start(id!!)
        consumer = scope.launch {
            try { for (frame in capture.audioFrames) engine!!.acceptPcm(frame) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportFailure(e) }
        }
        acquireWakeLock()
        capture.start(scope, RecognitionRuntime::updateLevel, ::reportFailure)
        setState(State.RECORDING)
        ticker = scope.launch {
            var ticks = 0
            while (isActive) {
                RecognitionRuntime.tick()
                if (++ticks % 10 == 0) id?.let { app.repository.finishSession(it, state.name, RecognitionRuntime.elapsedMs.value, null) }
                delay(1000)
            }
        }
        app.repository.finishSession(id!!, "RECORDING", 0, null)
    }
    private suspend fun pause() {
        capture.stop()
        engine!!.pause()
        releaseWakeLock()
        setState(State.PAUSED)
        id?.let { app.repository.finishSession(it, "PAUSED", RecognitionRuntime.elapsedMs.value, null) }
    }
    private suspend fun resume() {
        check(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission was revoked" }
        engine!!.resume()
        acquireWakeLock()
        capture.start(scope, RecognitionRuntime::updateLevel, ::reportFailure)
        setState(State.RECORDING)
    }
    private fun reportFailure(error: Throwable) {
        scope.launch { control.withLock { if (!terminating) finish(State.ERROR, error.message ?: "Capture interrupted") } }
    }
    private suspend fun finish(finalState: State, message: String? = null) {
        if (terminating) return
        terminating = true
        setState(State.STOPPING)
        ticker?.cancelAndJoin()
        var completed = finalState == State.FINISHED
        var failure = message
        withContext(NonCancellable) {
            suspend fun cleanup(block: suspend () -> Unit) {
                try { withTimeout(12_000) { block() } }
                catch (error: Exception) {
                    completed = false
                    if (failure == null) failure = error.message ?: "Session cleanup interrupted; confirmed text is retained"
                }
            }
            try {
                cleanup { capture.stop() }
                // Finish the one bounded memory queue; no final PCM is discarded in a normal stop.
                capture.audioFrames.close()
                cleanup { consumer?.join() }
                consumer?.cancelAndJoin()
                cleanup { engine?.flush() }
                cleanup { engine?.stop() }
                // Closing each engine closes its text-event channel. Join the collector to
                // persist every final event before draining translations, rather than sleeping.
                cleanup { engine?.close() }
                cleanup { events?.join() }
                events?.cancelAndJoin()
                errors?.cancelAndJoin()
                cleanup { queue?.drain(8_000) }
                cleanup { translator?.close() }
                RecognitionRuntime.end()
                cleanup {
                    id?.let { app.repository.finishSession(it, if (completed) "FINISHED" else "INTERRUPTED", RecognitionRuntime.elapsedMs.value) }
                }
            } finally {
                releaseWakeLock()
                setState(if (completed) State.FINISHED else State.INTERRUPTED)
                failure?.let { RecognitionRuntime.fail(it) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    private fun setState(value: State) { state = value; RecognitionRuntime.updateState(value); if (value !in listOf(State.FINISHED, State.ERROR, State.INTERRUPTED)) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(value.name)) }
    private fun notification(status: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val pause = state == State.RECORDING
        val controlIntent = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(if (pause) ACTION_PAUSE else ACTION_RESUME), flags)
        val stop = PendingIntent.getService(this, 2, Intent(this, javaClass).setAction(ACTION_STOP), flags)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Open Listener").setContentText(status).setContentIntent(open).setOngoing(true)
            .setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, if (pause) "Pause" else "Resume", controlIntent).addAction(0, "Stop", stop).build()
    }
    private fun acquireWakeLock() {
        releaseWakeLock()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:listener").apply { setReferenceCounted(false); acquire() }
    }
    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
    override fun onDestroy() {
        releaseWakeLock()
        queue?.cancel()
        scope.cancel()
        CoroutineScope(Dispatchers.IO).launch { runCatching { capture.stop() }; runCatching { engine?.close() }; runCatching { translator?.close() } }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_START = "io.github.vexpaer.openlistener.START"
        const val ACTION_PAUSE = "io.github.vexpaer.openlistener.PAUSE"
        const val ACTION_RESUME = "io.github.vexpaer.openlistener.RESUME"
        const val ACTION_STOP = "io.github.vexpaer.openlistener.STOP"
        const val EXTRA_SESSION_ID = "session_id"
        private const val CHANNEL_ID = "open_listener_recording"
        private const val NOTIFICATION_ID = 1001
    }
}
