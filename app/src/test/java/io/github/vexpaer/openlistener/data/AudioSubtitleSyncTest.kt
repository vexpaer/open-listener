package io.github.vexpaer.openlistener.data

import org.junit.Assert.*
import org.junit.Test

class AudioSubtitleSyncTest {
    private fun row(sequence: Long, start: Long) =
        SegmentEntity("record", sequence, start, start + 2000, "sentence $sequence",
            "译文", "COMPLETED", 1)

    @Test fun followsRecordedStartsAndSupportsSeekingBackward() {
        val segments = listOf(row(1, 0), row(2, 1500), row(3, 4000))
        assertEquals(-1, AudioSubtitleSync.activeIndex(segments, -1))
        assertEquals(0, AudioSubtitleSync.activeIndex(segments, 0))
        assertEquals(0, AudioSubtitleSync.activeIndex(segments, 1499))
        assertEquals(1, AudioSubtitleSync.activeIndex(segments, 1500))
        assertEquals(2, AudioSubtitleSync.activeIndex(segments, 8000))
        assertEquals(0, AudioSubtitleSync.activeIndex(segments, 100))
    }
}
