package io.github.vexpaer.openlistener.service

import android.os.SystemClock
import io.github.vexpaer.openlistener.asr.TranscriptEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI projections only. The service and Room remain the owners of capture and confirmed text. */
object RecognitionRuntime {
    private val _state = MutableStateFlow(MicrophoneRecognitionService.State.IDLE)
    private val _sessionId = MutableStateFlow<String?>(null)
    private val _level = MutableStateFlow(0f)
    private val _elapsedMs = MutableStateFlow(0L)
    private val _error = MutableStateFlow<String?>(null)
    private val _partial = MutableStateFlow<TranscriptEvent?>(null)
    val state = _state.asStateFlow()
    val sessionId = _sessionId.asStateFlow()
    val level = _level.asStateFlow()
    val elapsedMs = _elapsedMs.asStateFlow()
    val error = _error.asStateFlow()
    val partial = _partial.asStateFlow()
    private var startedElapsed = 0L

    fun begin(id: String) {
        _sessionId.value = id
        _partial.value = null
        _error.value = null
        _elapsedMs.value = 0L
        startedElapsed = SystemClock.elapsedRealtime()
    }
    fun tick() { if (startedElapsed > 0) _elapsedMs.value = SystemClock.elapsedRealtime() - startedElapsed }
    fun updateState(value: MicrophoneRecognitionService.State) { _state.value = value; if (value != MicrophoneRecognitionService.State.RECORDING) _level.value = 0f }
    fun updateLevel(value: Float) { _level.value = .7f * _level.value + .3f * value }
    fun updatePartial(value: TranscriptEvent?) { _partial.value = value }
    fun fail(message: String) { _error.value = message; updateState(MicrophoneRecognitionService.State.ERROR) }
    fun end() { tick(); _partial.value = null; _level.value = 0f }
}
