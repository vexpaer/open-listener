package io.github.vexpaer.openlistener.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSeekbarGeometryTest {
    @Test fun knobAndSeekUseExactSameHorizontalCoordinates() {
        val width = 300f
        val inset = 6f
        val durationMs = 100_000
        for (ms in listOf(0, 1_000, 25_000, 50_000, 75_000, 100_000)) {
            val x = AudioSeekbarGeometry.positionX(
                ms.toFloat(), durationMs.toFloat(), width, inset)
            val recovered = AudioSeekbarGeometry.seekPosition(x, width, inset, durationMs)
            assertEquals(ms, recovered, 1)
        }
    }

    @Test fun zeroDurationAndOutOfBoundsSeeksAreSafe() {
        assertEquals(0, AudioSeekbarGeometry.seekPosition(150f, 300f, 6f, 0))
        assertEquals(0, AudioSeekbarGeometry.seekPosition(-200f, 300f, 6f, 10_000))
        assertEquals(10_000, AudioSeekbarGeometry.seekPosition(900f, 300f, 6f, 10_000))
        assertEquals(6f, AudioSeekbarGeometry.positionX(0f, 0f, 300f, 6f), 0.0001f)
    }
}
