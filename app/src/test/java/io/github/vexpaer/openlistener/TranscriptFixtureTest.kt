package io.github.vexpaer.openlistener

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranscriptFixtureTest {
    @Test
    fun sourceSetContainsNoAudioPersistenceContract() {
        val source = File("src/main/java/io/github/vexpaer/openlistener/audio/AudioPcmCapture.kt").readText()
        assertTrue(source.contains("Channel"))
        assertTrue(!source.contains("FileOutputStream"))
        assertTrue(!source.contains("MediaRecorder.AudioSource.MIC"))
    }
}
