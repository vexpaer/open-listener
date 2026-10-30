package io.github.vexpaer.openlistener.asr

import io.github.vexpaer.openlistener.security.CredentialsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Service-owned, text-only AST session. Pause drains the connection; resume starts a new
 * wire session with continuous app sentence IDs and audio offsets. No output audio is requested.
 */
class DoubaoSimultaneousAsr(
    private val credential: () -> String?, private val source: String, private val target: String,
    private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build(),
    private val endpoint: String = DoubaoAstProtocol.ENDPOINT,
    private val timeoutMs: Long = 15000
) : AsrEngine {
    constructor(credentials: CredentialsStore, source: String, target: String) : this(
        { credentials.get("doubao_ast", "api_key") }, source, target)
    override val capabilities = AsrCapabilities("doubao-ast-v2", "Doubao simultaneous translation · S2T", true,
        setOf("zh-CN", "en", "ja", "ko", "fr", "de", "es"), true, providesTranslation = true)
    override val availability: EngineAvailability get() = when {
        closed -> EngineAvailability.Unavailable("豆包同传已关闭。")
        credential().isNullOrBlank() -> EngineAvailability.NeedsCredentials("请配置豆包同声传译 API Key，并开通同传服务。")
        DoubaoAstProtocol.language(source) !in languages || DoubaoAstProtocol.language(target) !in languages ->
            EngineAvailability.Unavailable("豆包同传不支持所选语言。")
        DoubaoAstProtocol.language(source) !in setOf("zh", "en") && DoubaoAstProtocol.language(target) !in setOf("zh", "en") ->
            EngineAvailability.Unavailable("豆包同传的源语言或目标语言须为中文或英语。")
        else -> EngineAvailability.Ready
    }
    private val output = Channel<TranscriptEvent>(128)
    override val events = output.receiveAsFlow()
    private val _errors = MutableStateFlow<Throwable?>(null)
    val errors = _errors.asStateFlow()
    private val lock = Any()
    private val lifecycle = Mutex()
    private var active: Connection? = null
    private var appSession: String? = null
    private var nextId = 1L
    private var sentBytes = 0L
    private var paused = false
    private var closed = false
    private inner class Connection {
        val id = UUID.randomUUID().toString()
        val opened = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val subtitles = AstSubtitles(checkNotNull(appSession), sentBytes / 32) { nextId++ }
        var socket: WebSocket? = null
        var ending = false
        var ready = false
        var failure: Throwable? = null
    }
    override suspend fun prepare() = availability
    override suspend fun start(sessionId: String) = lifecycle.withLock {
        check(availability == EngineAvailability.Ready && appSession == null)
        appSession = sessionId; connect()
    }
    private suspend fun connect() {
        val connection = synchronized(lock) { Connection().also { active = it } }
        try {
            val request = Request.Builder().url(endpoint).header("X-Api-Key", checkNotNull(credential()))
                .header("X-Api-Resource-Id", DoubaoAstProtocol.RESOURCE).build()
            val socket = client.newWebSocket(request, Listener(connection))
            synchronized(lock) { if (active === connection) connection.socket = socket else socket.cancel() }
            withTimeout(timeoutMs) {
                connection.opened.await()
                synchronized(lock) { send(connection, DoubaoAstProtocol.start(connection.id, source, target)) }
                connection.started.await()
            }
            synchronized(lock) { connection.failure?.let { throw it }; connection.ready = true }
        } catch (timeout: TimeoutCancellationException) {
            connection.socket?.cancel()
            if (!currentCoroutineContext().isActive) throw timeout
            val failure = IOException("豆包同传连接确认超时，请检查网络与服务权限。")
            fail(connection, failure); throw failure
        } catch (cancelled: CancellationException) {
            connection.socket?.cancel(); throw cancelled
        } catch (error: Exception) {
            fail(connection, IOException("豆包同传连接失败，请检查网络、API Key 与服务权限。")); throw checkNotNull(connection.failure)
        }
    }
    override suspend fun acceptPcm(pcm16: ByteArray) {
        synchronized(lock) {
            val connection = checkNotNull(active)
            connection.failure?.let { throw it }
            check(connection.ready && !connection.ending && !paused && !closed)
            require(pcm16.isNotEmpty() && pcm16.size % 2 == 0 && pcm16.size <= 6400)
            send(connection, DoubaoAstProtocol.audio(connection.id, pcm16)); sentBytes += pcm16.size
        }
    }
    private fun send(connection: Connection, bytes: ByteArray) {
        connection.failure?.let { throw it }
        val socket = checkNotNull(connection.socket)
        check(socket.queueSize() + bytes.size <= 64000 && socket.send(bytes.toByteString())) { "豆包同传发送过慢，请检查网络。" }
    }
    private suspend fun drain() {
        val connection = synchronized(lock) {
            active?.also {
                it.failure?.let { error -> throw error }
                if (!it.ending) { send(it, DoubaoAstProtocol.finish(it.id)); it.ending = true }
            }
        } ?: return
        try { withTimeout(timeoutMs) { connection.finished.await() } }
        catch (error: Exception) {
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            val failure = IOException("豆包同传最终字幕确认超时或失败，已确认文字已保留。")
            fail(connection, failure); throw failure
        }
    }
    override suspend fun flush() = lifecycle.withLock { drain() }
    override suspend fun pause() = lifecycle.withLock {
        drain()
        synchronized(lock) { active?.socket?.close(1000, "pause"); active = null; paused = true }
    }
    override suspend fun resume() = lifecycle.withLock {
        check(paused && !closed && appSession != null); paused = false; connect()
    }
    override suspend fun stop() = lifecycle.withLock {
        drain()
        synchronized(lock) { active?.socket?.close(1000, "stop"); active = null; appSession = null }
    }
    override suspend fun close() {
        synchronized(lock) { closed = true; active?.let { it.socket?.cancel(); it.opened.cancel(); it.started.cancel(); it.finished.cancel() }; active = null; output.close() }
    }
    private fun fail(connection: Connection, error: Throwable) = synchronized(lock) {
        if (active !== connection || connection.failure != null) return@synchronized
        connection.failure = error; _errors.value = error
        connection.opened.completeExceptionally(error); connection.started.completeExceptionally(error)
        connection.finished.completeExceptionally(error); connection.socket?.cancel()
    }
    private inner class Listener(private val connection: Connection) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { connection.opened.complete(Unit) }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            synchronized(lock) {
                if (active !== connection || closed || connection.failure != null) return
                try {
                    val message = DoubaoAstProtocol.parse(bytes.toByteArray())
                    check(message.session.isEmpty() || message.session == connection.id) { "AST session mismatch" }
                    check(message.code == 0 || message.code == 20000000) { "豆包同传服务错误 ${message.code}，请检查配置与额度。" }
                    when (message.event) {
                        DoubaoAstProtocol.STARTED -> connection.started.complete(Unit)
                        DoubaoAstProtocol.FINISHED -> {
                            check(connection.ending) { "豆包同传会话提前结束。" }
                            connection.subtitles.verifyFinished(); connection.finished.complete(Unit)
                        }
                        DoubaoAstProtocol.FAILED -> error("豆包同传服务拒绝请求，请检查 API Key、权限及所选语言。")
                        else -> connection.subtitles.accept(message)?.let { event ->
                            check(output.trySend(event).isSuccess) { "豆包同传字幕处理过慢。" }
                        }
                    }
                } catch (error: Exception) { fail(connection, IOException(error.message?.takeIf { error is IllegalStateException } ?: "豆包同传返回了无效的协议数据。")) }
            }
        }
        override fun onMessage(webSocket: WebSocket, text: String) { fail(connection, IOException("豆包同传返回了不支持的数据格式。")) }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(connection, IOException("豆包同传连接中断${response?.code?.let { " (HTTP $it)" }.orEmpty()}，请检查网络与服务权限。"))
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (!connection.finished.isCompleted) fail(connection, IOException("豆包同传连接在最终字幕确认前关闭。"))
            webSocket.close(1000, "closed")
        }
    }
    companion object {
        private val languages = setOf("zh", "en", "ja", "ko", "fr", "de", "es", "pt", "id", "ru", "it", "ar", "tr", "ms", "vi", "th", "nl", "ro", "pl", "cs")
    }
}
