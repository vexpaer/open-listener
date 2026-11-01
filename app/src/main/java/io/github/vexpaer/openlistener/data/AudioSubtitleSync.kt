package io.github.vexpaer.openlistener.data

/** Find the last subtitle whose recorded start time has been reached. */
object AudioSubtitleSync {
    fun activeIndex(segments: List<SegmentEntity>, positionMs: Long): Int =
        segments.indexOfLast { it.startOffsetMs <= positionMs }
}
