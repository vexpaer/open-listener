package io.github.vexpaer.openlistener.asr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A small UI tail only; the durable complete transcript belongs to Room. */
class SegmentAssembler {
    private val confirmed = linkedMapOf<Long, TranscriptEvent>()
    private var partial: TranscriptEvent? = null
    private val _state = MutableStateFlow<List<TranscriptEvent>>(emptyList())
    val state: StateFlow<List<TranscriptEvent>> = _state

    fun accept(event: TranscriptEvent): Boolean {
        if (event.text.isBlank()) return false
        val existing = confirmed[event.segmentId]
        if (event.partial) {
            if (existing != null || partial?.let { it.segmentId == event.segmentId && it.revision > event.revision } == true) return false
            partial = event
        } else {
            if (existing != null && (event.revision < existing.revision || event.text == existing.text)) return false
            confirmed[event.segmentId] = event
            if (partial?.segmentId == event.segmentId) partial = null
            while (confirmed.size > 128) confirmed.remove(confirmed.keys.first())
        }
        _state.value = (confirmed.values + listOfNotNull(partial)).sortedBy { it.segmentId }
        return true
    }
    fun clear() { confirmed.clear(); partial = null; _state.value = emptyList() }
}
