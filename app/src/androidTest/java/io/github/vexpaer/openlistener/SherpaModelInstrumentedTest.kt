package io.github.vexpaer.openlistener

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vexpaer.openlistener.asr.EngineAvailability
import io.github.vexpaer.openlistener.asr.SherpaStreamingAsr
import io.github.vexpaer.openlistener.asr.TranscriptEvent
import io.github.vexpaer.openlistener.models.ModelPhase
import io.github.vexpaer.openlistener.models.ModelStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Network + JNI integration test, not a mock and never silently skipped. */
@RunWith(AndroidJUnit4::class)
class SherpaModelInstrumentedTest {
    @Test
    fun realBilingualModelDownloadsInitializesAndDecodesPcmWithoutAudioFiles() = runBlocking {
        withTimeout(15 * 60_000L) {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val models = ModelStore(context)
            models.download()
            assertEquals(ModelPhase.INSTALLED, models.status().phase)
            assertTrue(models.isInstalled())
            val directory = requireNotNull(models.modelDirectory())
            assertEquals(5, directory.listFiles()!!.size) // Four weights + activation marker.
            assertFalse(directory.walkTopDown().any { it.extension == "wav" || it.extension == "pcm" })

            val engine = SherpaStreamingAsr(models)
            val received = mutableListOf<TranscriptEvent>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { received += it } }
            try {
                assertEquals(EngineAvailability.Ready, engine.prepare())
                assertTrue(models.status().inUse)
                val blockedDeletion = runCatching { models.delete() }.exceptionOrNull()
                assertNotNull("In-use model deletion must fail", blockedDeletion)
                assertTrue(models.isInstalled())
                engine.start("native-model-ci")
                // Bounded in-memory silence exercises PCM16 conversion and actual online
                // JNI inference. It intentionally does not assert fabricated recognized text.
                repeat(10) { engine.acceptPcm(ByteArray(3_200)) }
                engine.pause()
                engine.resume()
                repeat(5) { engine.acceptPcm(ByteArray(3_200)) }
                engine.flush()
                engine.stop()
                assertTrue(received.all { it.sessionId == "native-model-ci" })
                assertTrue(received.all { it.startOffsetMs >= 0 && (it.endOffsetMs == null || it.endOffsetMs >= it.startOffsetMs) })
            } finally {
                engine.close()
                collector.cancelAndJoin()
            }
            assertFalse(models.status().inUse)
        }
    }
}
