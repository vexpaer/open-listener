package io.github.vexpaer.openlistener

import com.google.protobuf.CodedInputStream
import io.github.vexpaer.openlistener.asr.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.ByteString.Companion.decodeHex
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class DoubaoAstTest {
    private fun fields(bytes: ByteArray): Map<Int, Any> {
        val input = CodedInputStream.newInstance(bytes); val result = mutableMapOf<Int, Any>()
        while (!input.isAtEnd) {
            val tag = input.readTag()
            result[tag ushr 3] = when (tag and 7) { 0 -> input.readInt64(); 2 -> input.readByteArray(); else -> error("Unexpected fixture field") }
        }
        return result
    }
    private fun response(event: Int, text: String = "", start: Int? = null, end: Int? = null, code: Int = 0): ByteString =
        DoubaoAstProtocol.message {
            if (code != 0) writeByteArray(1, DoubaoAstProtocol.message { writeInt32(3, code); writeString(4, "do-not-expose") })
            writeInt32(2, event); if (text.isNotEmpty()) writeString(4, text)
            if (start != null) writeInt32(5, start); if (end != null) writeInt32(6, end)
        }.toByteString()

    @Test fun officialWireFields_s2tOnly_andUnknownAudioIsIgnored() {
        // Golden wire captures based on the official demo schema, independent of the request helper.
        assertEquals("0a0332017310c801220472020102", DoubaoAstProtocol.audio("s", byteArrayOf(1, 2)).toByteString().hex())
        assertEquals("0a033201731066", DoubaoAstProtocol.finish("s").toByteString().hex())
        val parsed = DoubaoAstProtocol.parse("108b0522026869".decodeHex().toByteArray())
        assertEquals(651, parsed.event); assertEquals("hi", parsed.text)
        val start = fields(DoubaoAstProtocol.start("s", "en", "zh-CN"))
        assertEquals(100L, start[2]); assertFalse(start.containsKey(5))
        assertEquals("s", String(fields(start[1] as ByteArray)[6] as ByteArray))
        val request = fields(start[6] as ByteArray)
        assertEquals("s2t", String(request[1] as ByteArray)); assertEquals("en", String(request[2] as ByteArray))
        assertEquals("zh", String(request[3] as ByteArray)); assertFalse(request.containsKey(4))
        val audio = fields(start[4] as ByteArray)
        assertEquals(16000L, audio[7]); assertEquals(16L, audio[8]); assertEquals(1L, audio[9])
        val unexpectedTts = DoubaoAstProtocol.message { writeInt32(2, 352); writeByteArray(3, byteArrayOf(-1, -2, -3)) }
        assertEquals("", DoubaoAstProtocol.parse(unexpectedTts).text)
        assertTrue(runCatching { DoubaoAstProtocol.parse(byteArrayOf(16, -1)) }.isFailure)
    }

    private fun mockConnection(original: String, translated: String, requests: MutableList<Map<Int, Any>>, finish: Boolean = true): MockResponse =
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, "closed") }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val request = fields(bytes.toByteArray()); requests += request
                when (request[2] as Long) {
                    100L -> webSocket.send(response(150))
                    200L -> {
                        assertArrayEquals(ByteArray(3200), fields(request[4] as ByteArray)[14] as ByteArray)
                        webSocket.send(response(650, start = 0))
                        webSocket.send(response(651, original.substring(0, 2)))
                        webSocket.send(response(653, start = 0))
                        webSocket.send(response(654, translated.take(1)))
                        webSocket.send(response(652, original, start = 0, end = 100))
                        webSocket.send(response(654, translated.drop(1)))
                        webSocket.send(response(655, translated, start = 0, end = 100))
                    }
                    102L -> if (finish) webSocket.send(response(152))
                }
            }
        })

    @Test fun bilingualDeltas_pauseResume_keepIdsOffsets_andNoTtsRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        val requests = CopyOnWriteArrayList<Map<Int, Any>>()
        server.enqueue(mockConnection("Hello", "你好", requests)); server.enqueue(mockConnection("Again", "再见", requests))
        val engine = DoubaoSimultaneousAsr({ "test-key" }, "en", "zh-CN", endpoint = server.url("/ast").toString(), timeoutMs = 3000)
        val received = mutableListOf<TranscriptEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { engine.events.toList(received) }
        try {
            engine.start("app-session"); engine.acceptPcm(ByteArray(3200)); engine.pause()
            engine.resume(); engine.acceptPcm(ByteArray(3200)); engine.flush(); engine.stop(); engine.close(); collector.join()
            val completed = received.filter { !it.partial && it.translationFinal }
            assertEquals(listOf("Hello", "Again"), completed.map { it.text })
            assertEquals(listOf("你好", "再见"), completed.map { it.translated })
            assertEquals(listOf(1L, 2L), completed.map { it.segmentId }); assertEquals(listOf(0L, 100L), completed.map { it.startOffsetMs })
            assertEquals(listOf(100L, 200L), completed.map { it.endOffsetMs })
            assertTrue(received.any { it.partial && it.translated == "你" })
            assertTrue(received.any { !it.partial && it.translated == "你" && !it.translationFinal })
            assertTrue(requests.filter { it[2] == 100L }.all { !it.containsKey(5) })
            val handshake = server.takeRequest()
            assertEquals("test-key", handshake.getHeader("X-Api-Key")); assertEquals("volc.service_type.10053", handshake.getHeader("X-Api-Resource-Id"))
        } finally { engine.close(); collector.cancelAndJoin(); server.shutdown() }
    }
    @Test fun startTimeoutIsAnError_notUncaughtCancellation_andServerErrorsAreSanitized() = runBlocking {
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { webSocket.send(response(153, code = 45000001)) }
        }))
        try {
            val timeout = DoubaoSimultaneousAsr({ "k" }, "en", "zh", endpoint = server.url("/ast").toString(), timeoutMs = 200)
            try { assertTrue(runCatching { timeout.start("timeout") }.exceptionOrNull() is IOException) } finally { timeout.close() }
            val rejected = DoubaoSimultaneousAsr({ "k" }, "en", "zh", endpoint = server.url("/ast").toString(), timeoutMs = 2000)
            try {
                val error = runCatching { rejected.start("rejected") }.exceptionOrNull()
                assertNotNull(error); assertFalse(error!!.message.orEmpty().contains("do-not-expose"))
            } finally { rejected.close() }
        } finally { server.shutdown() }
    }
    @Test fun independentSentenceStreams_doNotOverwriteNextSource() {
        var id = 0L; val subtitles = AstSubtitles("app", 0) { ++id }
        fun event(type: Int, text: String = "", start: Long? = null) = subtitles.accept(DoubaoAstProtocol.Response(type, text, start))
        event(650, start = 0); event(651, "first"); event(652, "first", 0)
        event(650, start = 100); event(651, "second"); event(652, "second", 100)
        event(653, start = 0); event(654, "一"); val first = event(655, "一")!!
        event(653, start = 100); event(654, "二"); val second = event(655, "二")!!
        assertEquals(1L, first.segmentId); assertEquals("first", first.text); assertEquals("一", first.translated)
        assertEquals(2L, second.segmentId); assertEquals("second", second.text); assertEquals("二", second.translated)
        subtitles.verifyFinished()
        val assembler = SegmentAssembler(); assertTrue(assembler.accept(first.copy(translationFinal = false)))
        assertTrue(assembler.accept(first.copy(revision = first.revision + 1)))
        assertFalse(assembler.accept(first.copy(revision = first.revision + 2)))
    }
    @Test fun credentialsAndLanguagePairValidation() = runBlocking {
        val missing = DoubaoSimultaneousAsr({ null }, "en", "zh-CN")
        val unsupported = DoubaoSimultaneousAsr({ "key" }, "ja", "fr")
        try {
            assertTrue(missing.availability is EngineAvailability.NeedsCredentials)
            assertTrue(unsupported.availability is EngineAvailability.Unavailable)
        } finally { missing.close(); unsupported.close() }
    }
}
