package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.asr.*
import io.github.vexpaer.openlistener.audio.WavAudio
import io.github.vexpaer.openlistener.audio.WavRecording
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CloudChunkAsrTest {
    @Test fun openaiAndCustomUploadWav_flushFinalTail_andPreserveOrder() = runBlocking {
        for (provider in listOf("openai", "custom")) {
            val server = MockWebServer()
            server.start()
            repeat(2) { server.enqueue(MockResponse().setBody("""{"text":"segment $it"}""")) }
            val engine = CloudChunkAsr(provider, { when (it) {
                "api_key" -> "test-key"; "model" -> "test-speech"; "base_url" -> server.url("/v1").toString(); else -> null
            } }, "en", allowLocalhost = true)
            val received = mutableListOf<TranscriptEvent>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { engine.events.toList(received) }
            try {
                assertEquals(EngineAvailability.Ready, engine.prepare())
                engine.start("mock-session")
                repeat(20) { engine.acceptPcm(ByteArray(3200)) }
                engine.pause(); engine.resume()
                engine.acceptPcm(ByteArray(3200))
                engine.flush(); engine.stop(); engine.close(); collector.join()
                assertEquals(listOf("segment 0", "segment 1"), received.map { it.text })
                assertEquals(listOf(0L, 2000L), received.map { it.startOffsetMs })
                assertEquals(2100L, received.last().endOffsetMs)
                val request = server.takeRequest()
                assertEquals("/v1/audio/transcriptions", request.path)
                assertEquals("Bearer test-key", request.getHeader("Authorization"))
                val body = request.body.readUtf8()
                assertTrue(body.contains("test-speech")); assertTrue(body.contains("RIFF")); assertTrue(body.contains("audio/wav"))
            } finally { engine.close(); collector.cancelAndJoin(); server.shutdown() }
        }
    }
    @Test fun qwenUsesAudioChatProtocol_andHttpFailureReachesFlush() = runBlocking {
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"你好"}}]}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("sensitive provider body"))
        val engine = CloudChunkAsr("qwen", { if (it == "base_url") server.url("/v1").toString() else "test" }, "zh-CN", allowLocalhost = true)
        val received = mutableListOf<TranscriptEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { engine.events.toList(received) }
        try {
            engine.start("qwen-session"); repeat(20) { engine.acceptPcm(ByteArray(3200)) }
            engine.pause(); engine.resume(); engine.acceptPcm(ByteArray(3200))
            val error = runCatching { engine.flush() }.exceptionOrNull()
            assertNotNull(error); assertTrue(error!!.message!!.contains("401"))
            assertFalse(error.message!!.contains("sensitive"))
            engine.close(); collector.join()
            assertEquals("你好", received.single().text)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("input_audio")); assertTrue(body.contains("data:audio/wav;base64,"))
        } finally { engine.close(); collector.cancelAndJoin(); server.shutdown() }
    }
    @Test fun wavHeaderAndProcessDeathRecoveryPreserveSamples() {
        val file = File.createTempFile("recording", ".wav")
        try {
            val samples = ByteArray(3200) { (it % 127).toByte() }
            WavRecording(file).use { it.append(samples); it.append(samples) }
            var data = file.readBytes()
            assertArrayEquals(WavAudio.encode(samples + samples), data)
            file.appendBytes(samples) // Simulate crash before header update.
            WavAudio.repair(file); data = file.readBytes()
            assertEquals(9600, ByteBuffer.wrap(data, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
            assertArrayEquals(WavAudio.encode(samples + samples + samples), data)
        } finally { file.delete() }
    }
}
