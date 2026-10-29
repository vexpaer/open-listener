package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.asr.DoubaoAsrErrorKind
import io.github.vexpaer.openlistener.asr.DoubaoAsrException
import io.github.vexpaer.openlistener.asr.DoubaoProtocol
import io.github.vexpaer.openlistener.asr.DoubaoRealtimeAsr
import io.github.vexpaer.openlistener.asr.TranscriptEvent
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DoubaoMockTest {
    @Test fun waitsForAckChecksHeadersRevisesUtteranceAndWaitsForFinal() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        val received = CopyOnWriteArrayList<ByteArray>()
        val configurationSeen = CompletableDeferred<Unit>()
        val finalSeen = CompletableDeferred<Unit>()
        val peer = CompletableDeferred<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { peer.complete(webSocket) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val raw = bytes.toByteArray()
                received.add(raw)
                when ((raw[1].toInt() ushr 4) and 15) {
                    1 -> configurationSeen.complete(Unit)
                    2 -> if (raw[1].toInt() and 2 != 0) finalSeen.complete(Unit)
                    else -> fail("Unexpected client frame")
                }
            }
        }))
        server.start()
        val engine = engine(server, client)
        val events = CopyOnWriteArrayList<TranscriptEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { events.add(it) } }
        try {
            val starting = async { engine.start("session-a") }
            withTimeout(3000) { configurationSeen.await() }
            assertFalse("start must wait for server ACK", starting.isCompleted)
            val request = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("test-key", request.getHeader("X-Api-Key"))
            assertEquals("volc.seedasr.sauc.duration", request.getHeader("X-Api-Resource-Id"))
            assertTrue(!request.getHeader("X-Api-Request-Id").isNullOrBlank())
            assertEquals(0x11, received[0][0].toInt() and 255)
            assertEquals(1, ByteBuffer.wrap(received[0], 4, 4).int)
            val config = payload(received[0])
            assertTrue(config.contains("\"model_name\":\"bigmodel\""))
            assertTrue(config.contains("\"format\":\"pcm\""))
            assertTrue(config.contains("\"show_utterances\":true"))

            val socket = peer.await()
            socket.send(response("{}"))
            withTimeout(3000) { starting.await() }
            engine.acceptPcm(byteArrayOf(1, 2, 3, 4))
            socket.send(response(utterance("hel", false), sequence = 2))
            socket.send(response(utterance("hello", true), sequence = 3))
            socket.send(response(utterance("hello", true), sequence = 4)) // repeated definite is deduplicated
            val stopping = async { engine.flush() }
            withTimeout(3000) { finalSeen.await() }
            assertFalse("flush must wait for negative-sequence final response", stopping.isCompleted)
            socket.send(response(utterance("Hello.", true), sequence = 5, last = true))
            withTimeout(3000) { stopping.await() }
            withTimeout(3000) { while (events.size < 3) kotlinx.coroutines.yield() }
            assertEquals(3, events.size)
            assertEquals(listOf("hel", "hello", "Hello."), events.map { it.text })
            assertEquals(listOf(true, false, false), events.map { it.partial })
            assertEquals(listOf(0, 1, 2), events.map { it.revision })
            assertEquals(1, events.map { it.segmentId }.toSet().size)
            assertEquals("session-a", events.last().sessionId)
            assertEquals(100L, events.last().startOffsetMs)
            assertEquals(800L, events.last().endOffsetMs)
            assertEquals(2, ByteBuffer.wrap(received[1], 4, 4).int)
            assertEquals(-3, ByteBuffer.wrap(received.last(), 4, 4).int)
            engine.stop()
            assertEquals(null, engine.errors.value)
        } finally {
            collector.cancelAndJoin()
            cleanup(engine, server, client)
        }
    }

    @Test fun binaryErrorFailsStartAndRemainsObservable() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                webSocket.send(DoubaoProtocol.serverError(45000001, "private-server-body").toByteString())
            }
        }))
        server.start()
        val engine = engine(server, client)
        try {
            val failure = try { engine.start("error"); fail("Expected start failure"); null } catch (error: DoubaoAsrException) { error }
            assertEquals(DoubaoAsrErrorKind.SERVER, failure!!.kind)
            assertEquals(45000001, failure.code)
            assertEquals(failure, engine.errors.value)
            assertFalse(failure.message!!.contains("private-server-body"))
            try { engine.stop(); fail("stop must propagate existing failure") } catch (_: DoubaoAsrException) { }
        } finally { cleanup(engine, server, client) }
    }

    @Test fun missingFinalResponseTimesOutInsteadOfPretendingSuccess() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if ((bytes[1].toInt() ushr 4) and 15 == 1) webSocket.send(response("{}"))
            }
        }))
        server.start()
        val engine = engine(server, client, finalTimeoutMs = 150)
        try {
            engine.start("timeout")
            try { engine.flush(); fail("Expected final timeout") } catch (error: DoubaoAsrException) {
                assertEquals(DoubaoAsrErrorKind.TIMEOUT, error.kind)
                assertEquals(error, engine.errors.value)
            }
        } finally { cleanup(engine, server, client) }
    }

    @Test fun handshakeHttp401IsAnAuthenticationFailure() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(401).setBody("do not expose"))
        server.start()
        val engine = engine(server, client)
        try {
            try { engine.start("auth"); fail("Expected auth failure") } catch (error: DoubaoAsrException) {
                assertEquals(DoubaoAsrErrorKind.AUTHENTICATION, error.kind)
                assertEquals(401, error.httpStatus)
            }
        } finally { cleanup(engine, server, client) }
    }

    @Test fun startupTimeoutIsBoundedAndCancellationIsNotConvertedToSuccess() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        val configurationSeen = CountDownLatch(1)
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { configurationSeen.countDown() }
        }))
        server.start()
        val engine = engine(server, client, startTimeoutMs = 200)
        try {
            try { engine.start("no-ack"); fail("Expected ACK timeout") } catch (error: DoubaoAsrException) {
                assertEquals(DoubaoAsrErrorKind.TIMEOUT, error.kind)
            }
            assertTrue(configurationSeen.await(3, TimeUnit.SECONDS))
        } finally { cleanup(engine, server, client) }
    }

    @Test fun boundedWebSocketQueueFailsLoudly() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        server.start()
        val engine = DoubaoRealtimeAsr(
            credential = credentials(), client = client, endpoint = server.url("/").toString(),
            startTimeoutMs = 2000, finalTimeoutMs = 150, maxQueuedBytes = 1
        )
        try {
            try { engine.start("bounded"); fail("Expected queue failure") } catch (error: DoubaoAsrException) {
                assertEquals(DoubaoAsrErrorKind.BACKPRESSURE, error.kind)
            }
        } finally { cleanup(engine, server, client) }
    }

    @Test fun pauseResumeOpensNewStreamWithoutReplayingAudio() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        val frames = CopyOnWriteArrayList<ByteArray>()
        repeat(2) {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val raw = bytes.toByteArray()
                    frames.add(raw)
                    if (raw[1].toInt() and 2 != 0) webSocket.send(response("{}", last = true))
                    else if ((raw[1].toInt() ushr 4) and 15 == 1) webSocket.send(response("{}"))
                }
            }))
        }
        server.start()
        val engine = engine(server, client)
        try {
            engine.start("resume")
            engine.acceptPcm(byteArrayOf(1, 0))
            engine.pause()
            engine.resume()
            engine.stop()
            assertEquals(5, frames.size) // first config, audio, EOS; second config, EOS
            assertEquals(1, frames.count { (it[1].toInt() and 0xF0) == 0x20 && (it[1].toInt() and 2) == 0 })
            val first = server.takeRequest(3, TimeUnit.SECONDS)!!
            val second = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertNotEquals(first.getHeader("X-Api-Request-Id"), second.getHeader("X-Api-Request-Id"))
        } finally { cleanup(engine, server, client) }
    }

    private fun engine(server: MockWebServer, client: OkHttpClient, startTimeoutMs: Long = 3000, finalTimeoutMs: Long = 2000) =
        DoubaoRealtimeAsr(credentials(), client, server.url("/").toString(), startTimeoutMs, finalTimeoutMs)

    private fun credentials(): (String) -> String? = { field ->
        mapOf("api_key" to "test-key", "resource_id" to "volc.seedasr.sauc.duration")[field]
    }

    private fun response(body: String, sequence: Int = 1, last: Boolean = false): ByteString =
        DoubaoProtocol.serverResponse(body.toByteArray(), sequence, last).toByteString()

    private fun utterance(text: String, definite: Boolean): String =
        """{"result":{"text":"$text","utterances":[{"text":"$text","start_time":100,"end_time":800,"definite":$definite}]}}"""

    private fun payload(frame: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(frame, 12, frame.size - 12)).use { it.readBytes().toString(Charsets.UTF_8) }

    private suspend fun cleanup(engine: DoubaoRealtimeAsr, server: MockWebServer, client: OkHttpClient) {
        runCatching { engine.close() }
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
        runCatching { server.shutdown() }
    }
}
