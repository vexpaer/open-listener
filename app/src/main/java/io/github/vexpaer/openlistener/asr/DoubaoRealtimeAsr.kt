package io.github.vexpaer.openlistener.asr

import io.github.vexpaer.openlistener.security.CredentialsStore
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/** No reconnect/replay: callers must explicitly resume a new stream with newly captured PCM. */
class DoubaoRealtimeAsr(
    private val credential: (String) -> String?,
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: String = ENDPOINT,
    private val startTimeoutMs: Long = 15_000,
    private val finalTimeoutMs: Long = 10_000,
    private val maxQueuedBytes: Long = 64_000,
    private val maxPcmFrameBytes: Int = 6_400
) : AsrEngine {
    constructor(credentials: CredentialsStore, client: OkHttpClient = defaultClient()) :
        this(
            { field -> credentials.get("doubao", field) },
            client,
            credentials.get("doubao", "endpoint")?.trim()?.takeIf { it.isNotEmpty() } ?: ENDPOINT
        ) {
            val uri = java.net.URI(endpoint)
            require(uri.scheme == "wss" && uri.host != null && uri.userInfo == null && uri.fragment == null) {
                "Doubao endpoint must be a secure wss URL without user info"
            }
        }

    private val lock = Any()
    private val lifecycle = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val eventQueue = Channel<TranscriptEvent>(capacity = 128)
    private val _errors = MutableStateFlow<DoubaoAsrException?>(null)
    // One pipeline consumer; retains text-only events even before collection starts.
    override val events: Flow<TranscriptEvent> = eventQueue.receiveAsFlow()
    val errors: StateFlow<DoubaoAsrException?> = _errors.asStateFlow()

    private var connection: Connection? = null
    private var activeSession = ""
    private var nextSegmentId = 1L
    private var audioBytes = 0L
    private var paused = false
    private var closed = false

    init {
        require(startTimeoutMs > 0 && finalTimeoutMs > 0)
        require(maxQueuedBytes > 0 && maxPcmFrameBytes > 0 && maxPcmFrameBytes % 2 == 0)
    }

    override val capabilities = AsrCapabilities(
        providerId = "doubao-sauc-v3",
        displayName = "Doubao realtime ASR 2.0",
        streaming = true,
        languages = setOf("zh-CN", "en"),
        uploadsAudio = true
    )

    override val availability: EngineAvailability
        get() = if (credential("api_key").isNullOrBlank() || credential("resource_id").isNullOrBlank()) {
            EngineAvailability.NeedsCredentials("请配置豆包 X-Api-Key 和 X-Api-Resource-Id。")
        } else EngineAvailability.Ready

    // Local validation only. start() performs the authenticated WebSocket handshake and ACK wait.
    override suspend fun prepare(): EngineAvailability = availability

    override suspend fun start(sessionId: String) = lifecycle.withLock {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        synchronized(lock) {
            check(!closed) { "Doubao ASR is closed" }
            check(connection == null && activeSession.isEmpty()) { "Doubao ASR has already started" }
            activeSession = sessionId
            nextSegmentId = 1
            audioBytes = 0
            paused = false
            _errors.value = null
        }
        connect()
    }

    private suspend fun connect() {
        if (availability !is EngineAvailability.Ready) {
            val error = DoubaoAsrException(DoubaoAsrErrorKind.CONFIGURATION, "豆包凭据未配置。")
            _errors.value = error
            throw error
        }
        val stream = synchronized(lock) {
            Connection(UUID.randomUUID().toString(), audioBytes / 32).also { connection = it }
        }
        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("X-Api-Key", credential("api_key")!!)
                .header("X-Api-Resource-Id", credential("resource_id")!!)
                .header("X-Api-Request-Id", stream.requestId)
                .build()
            val webSocket = client.newWebSocket(request, Listener(stream))
            synchronized(lock) {
                if (connection === stream && stream.failure == null) stream.socket = webSocket
                else webSocket.cancel()
            }
            withTimeout(startTimeoutMs) {
                stream.opened.await()
                synchronized(lock) {
                    stream.failure?.let { throw it }
                    send(stream, DoubaoProtocol.fullClientRequest(startPayload(), sequence = 1))
                    stream.nextSequence = 2
                }
                stream.acknowledged.await()
            }
            synchronized(lock) {
                stream.failure?.let { throw it }
                check(!stream.ended) { "Doubao stream ended during preparation" }
                stream.ready = true
            }
        } catch (error: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) {
                dispose(stream)
                throw error
            }
            val failure = DoubaoAsrException(
                DoubaoAsrErrorKind.TIMEOUT, "豆包连接或配置确认超时。", requestId = stream.requestId
            )
            fail(stream, failure)
            throw failure
        } catch (error: CancellationException) {
            dispose(stream)
            throw error
        } catch (error: Exception) {
            val failure = error as? DoubaoAsrException ?: DoubaoAsrException(
                DoubaoAsrErrorKind.NETWORK, "豆包连接失败。", requestId = stream.requestId
            )
            fail(stream, failure)
            throw failure
        }
    }

    override suspend fun acceptPcm(pcm16: ByteArray) {
        synchronized(lock) {
            val stream = connection ?: throw IllegalStateException("Doubao ASR is not started")
            stream.failure?.let { throw it }
            check(stream.ready && !stream.eosSent && !stream.ended && !paused) { "Doubao ASR is not accepting PCM" }
            require(pcm16.isNotEmpty() && pcm16.size % 2 == 0 && pcm16.size <= maxPcmFrameBytes) {
                "Expected 1..$maxPcmFrameBytes bytes of fresh PCM16LE mono at 16000 Hz, with even length"
            }
            val frame = DoubaoProtocol.audioFrame(pcm16, stream.nextSequence++)
            send(stream, frame)
            audioBytes += pcm16.size
        }
    }

    override suspend fun flush() = lifecycle.withLock { flushCurrent() }

    private suspend fun flushCurrent() {
        val stream = synchronized(lock) {
            val current = connection ?: return
            current.failure?.let { throw it }
            if (!current.ended && !current.eosSent) {
                check(current.ready) { "Doubao ASR is not ready" }
                send(current, DoubaoProtocol.audioFrame(ByteArray(0), current.nextSequence++, last = true))
                current.eosSent = true
            }
            current
        }
        try {
            withTimeout(finalTimeoutMs) { stream.finished.await() }
            synchronized(lock) { stream.failure?.let { throw it } }
        } catch (error: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) {
                dispose(stream)
                throw error
            }
            val failure = DoubaoAsrException(
                DoubaoAsrErrorKind.TIMEOUT, "豆包最终识别结果等待超时，未确认的文字不能视为已定稿。", requestId = stream.requestId
            )
            fail(stream, failure)
            throw failure
        } catch (error: CancellationException) {
            dispose(stream)
            throw error
        }
    }

    override suspend fun pause() = lifecycle.withLock {
        try {
            flushCurrent()
        } finally {
            synchronized(lock) {
                connection?.socket?.close(1000, "pause")
                connection = null
                paused = true
            }
        }
    }

    override suspend fun resume() = lifecycle.withLock {
        synchronized(lock) {
            check(!closed && paused && activeSession.isNotEmpty()) { "Doubao ASR is not paused" }
            paused = false
            _errors.value = null
        }
        connect()
    }

    override suspend fun stop() = lifecycle.withLock {
        try {
            flushCurrent()
        } finally {
            synchronized(lock) {
                connection?.socket?.close(1000, "stop")
                connection = null
                activeSession = ""
                paused = false
            }
        }
    }

    override suspend fun close() {
        try {
            stop()
        } finally {
            synchronized(lock) {
                closed = true
                eventQueue.close()
            }
        }
    }

    // Called while holding lock. OkHttp's own outgoing queue is the only PCM queue.
    private fun send(stream: Connection, frame: ByteArray) {
        val socket = stream.socket ?: throw IllegalStateException("Doubao WebSocket is not open")
        if (socket.queueSize() + frame.size > maxQueuedBytes) {
            val error = DoubaoAsrException(
                DoubaoAsrErrorKind.BACKPRESSURE, "豆包发送队列已满，识别已停止；不会缓存或重放音频。", requestId = stream.requestId
            )
            fail(stream, error)
            throw error
        }
        if (!socket.send(frame.toByteString())) {
            val error = DoubaoAsrException(
                DoubaoAsrErrorKind.NETWORK, "豆包 WebSocket 拒绝发送数据。", requestId = stream.requestId
            )
            fail(stream, error)
            throw error
        }
    }

    private fun startPayload(): ByteArray = buildJsonObject {
        put("user", buildJsonObject { put("uid", "open-listener") })
        put("audio", buildJsonObject {
            put("format", "pcm")
            put("codec", "raw")
            put("rate", 16000)
            put("bits", 16)
            put("channel", 1)
        })
        put("request", buildJsonObject {
            put("model_name", "bigmodel")
            put("enable_itn", true)
            put("enable_punc", true)
            put("enable_nonstream", true)
            put("show_utterances", true)
            put("result_type", "single")
        })
    }.toString().toByteArray(Charsets.UTF_8)

    private fun fail(stream: Connection, error: DoubaoAsrException) {
        synchronized(lock) {
            if (connection !== stream || stream.failure != null || stream.ended) return
            stream.failure = error
            stream.ready = false
            _errors.value = error
            stream.opened.completeExceptionally(error)
            stream.acknowledged.completeExceptionally(error)
            stream.finished.completeExceptionally(error)
            stream.socket?.cancel()
        }
    }

    private fun dispose(stream: Connection) {
        synchronized(lock) {
            if (connection === stream) {
                connection = null
                stream.socket?.cancel()
                stream.opened.cancel()
                stream.acknowledged.cancel()
                stream.finished.cancel()
            }
        }
    }

    private inner class Listener(private val stream: Connection) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (connection !== stream || stream.failure != null) {
                    webSocket.cancel()
                    return
                }
                stream.socket = webSocket
                stream.opened.complete(Unit)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            synchronized(lock) {
                if (connection !== stream || stream.failure != null || stream.ended) return
                try {
                    val frame = DoubaoProtocol.parseServerFrame(bytes.toByteArray())
                    if (frame.errorCode != null && frame.errorCode != 0) {
                        throw DoubaoAsrException(
                            DoubaoAsrErrorKind.SERVER,
                            "豆包服务拒绝请求（代码 ${frame.errorCode}），请检查鉴权、资源开通、计费及音频配置。",
                            code = frame.errorCode,
                            requestId = stream.requestId
                        )
                    }
                    check(frame.messageType == DoubaoProtocol.TYPE_FULL_SERVER_RESPONSE) { "Unexpected Doubao message type" }
                    check(frame.serialization == DoubaoProtocol.SERIALIZATION_JSON) { "Expected Doubao JSON response" }
                    val root = if (frame.payload.isEmpty()) JsonObject(emptyMap()) else {
                        json.parseToJsonElement(frame.payload.toString(Charsets.UTF_8)) as? JsonObject
                            ?: error("Expected Doubao response object")
                    }
                    val code = root["code"]?.jsonPrimitive?.longOrNull
                    if (code != null && code != 0L && code != 20_000_000L) {
                        throw DoubaoAsrException(
                            DoubaoAsrErrorKind.SERVER, "豆包识别失败（代码 $code）。",
                            code = code.toInt(), requestId = stream.requestId
                        )
                    }
                    publishUtterances(stream, root, frame.isLastPackage)
                    stream.acknowledged.complete(Unit)
                    if (frame.isLastPackage) {
                        stream.ended = true
                        stream.ready = false
                        stream.finished.complete(Unit)
                    }
                } catch (error: Exception) {
                    fail(stream, error as? DoubaoAsrException ?: DoubaoAsrException(
                        DoubaoAsrErrorKind.PROTOCOL, "豆包响应格式错误，识别已停止。", requestId = stream.requestId
                    ))
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            fail(stream, DoubaoAsrException(
                DoubaoAsrErrorKind.PROTOCOL, "豆包返回了非二进制消息。", requestId = stream.requestId
            ))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val status = response?.code
            response?.close()
            fail(stream, DoubaoAsrException(
                if (status == 401 || status == 403) DoubaoAsrErrorKind.AUTHENTICATION else DoubaoAsrErrorKind.NETWORK,
                when (status) {
                    401, 403 -> "豆包鉴权失败（HTTP $status），请检查 Key 和 Resource ID。"
                    429 -> "豆包请求受限（HTTP 429），请检查配额与并发额度。"
                    null -> "豆包网络连接中断；丢失音频不会被重放。"
                    else -> "豆包握手失败（HTTP $status）。"
                },
                httpStatus = status, requestId = stream.requestId
            ))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            unexpectedClose()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = unexpectedClose()

        private fun unexpectedClose() {
            synchronized(lock) {
                if (connection === stream && !stream.ended) {
                    fail(stream, DoubaoAsrException(
                        DoubaoAsrErrorKind.NETWORK, "豆包连接在最终识别结果前关闭。", requestId = stream.requestId
                    ))
                }
            }
        }
    }

    private fun publishUtterances(stream: Connection, root: JsonObject, finalPacket: Boolean) {
        val resultNode = root["result"] ?: return
        val results = when (resultNode) {
            is JsonObject -> listOf(resultNode)
            is JsonArray -> resultNode.map { it as? JsonObject ?: error("Invalid Doubao result") }
            else -> error("Invalid Doubao result")
        }
        for (result in results) {
            val utterances = result["utterances"] as? JsonArray
            if (utterances == null) {
                check(result["text"]?.jsonPrimitive?.content.isNullOrBlank()) {
                    "Doubao did not return requested utterance timing data"
                }
                continue
            }
            for (node in utterances) {
                val utterance = node as? JsonObject ?: error("Invalid Doubao utterance")
                val text = utterance["text"]?.jsonPrimitive?.content ?: continue
                if (text.isBlank()) continue
                val start = utterance["start_time"]?.jsonPrimitive?.longOrNull ?: error("Missing utterance start_time")
                val end = utterance["end_time"]?.jsonPrimitive?.longOrNull ?: error("Missing utterance end_time")
                check(start >= 0 && end >= start) { "Invalid utterance timing" }
                val definite = utterance["definite"]?.jsonPrimitive?.booleanOrNull == true || finalPacket
                val existing = stream.utterances[start]
                if (existing != null && existing.text == text && existing.end == end && existing.definite == definite) continue
                // Definite sentences cannot become provisional again.
                if (existing?.definite == true && !definite) continue
                val entry = Utterance(
                    id = existing?.id ?: nextSegmentId++,
                    text = text,
                    end = end,
                    definite = definite,
                    revision = (existing?.revision ?: -1) + 1
                )
                stream.utterances[start] = entry
                val event = TranscriptEvent(
                    sessionId = activeSession,
                    segmentId = entry.id,
                    partial = !definite,
                    text = text,
                    startOffsetMs = stream.offsetMs + start,
                    endOffsetMs = stream.offsetMs + end,
                    revision = entry.revision
                )
                if (eventQueue.trySend(event).isFailure) throw DoubaoAsrException(
                    DoubaoAsrErrorKind.BACKPRESSURE, "识别文字消费队列已满，识别已停止以避免静默丢段。", requestId = stream.requestId
                )
            }
        }
        // result_type=single only repeats recent sentences; PCM is never retained here.
        while (stream.utterances.size > 256) {
            val oldest = stream.utterances.keys.first()
            stream.utterances.remove(oldest)
        }
    }

    private class Connection(val requestId: String, val offsetMs: Long) {
        var socket: WebSocket? = null
        var nextSequence = 1
        var ready = false
        var ended = false
        var eosSent = false
        var failure: DoubaoAsrException? = null
        val opened = CompletableDeferred<Unit>()
        val acknowledged = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val utterances = linkedMapOf<Long, Utterance>()
    }

    private data class Utterance(val id: Long, val text: String, val end: Long, val definite: Boolean, val revision: Int)

    companion object {
        const val ENDPOINT = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

enum class DoubaoAsrErrorKind { CONFIGURATION, AUTHENTICATION, NETWORK, SERVER, PROTOCOL, TIMEOUT, BACKPRESSURE }

/** Deliberately excludes server response text, credentials and captured PCM. */
class DoubaoAsrException(
    val kind: DoubaoAsrErrorKind,
    message: String,
    val code: Int? = null,
    val httpStatus: Int? = null,
    val requestId: String? = null
) : IOException(message)
