package io.github.vexpaer.openlistener.ui

/** Shared track geometry keeps drawing and touch seeking on the identical coordinates. */
internal object AudioSeekbarGeometry {
    fun positionX(progress: Float, total: Float, width: Float, inset: Float): Float {
        val length = (width - 2f * inset).coerceAtLeast(0f)
        val fraction = if (total > 0f) (progress / total).coerceIn(0f, 1f) else 0f
        return inset + length * fraction
    }

    fun seekPosition(x: Float, width: Float, inset: Float, durationMs: Int): Int {
        if (durationMs <= 0) return 0
        val length = (width - 2f * inset).coerceAtLeast(1f)
        return (((x - inset).coerceIn(0f, length) / length) * durationMs)
            .toInt().coerceIn(0, durationMs)
    }
}
