package io.github.vexpaer.openlistener.asr

import io.github.vexpaer.openlistener.audio.WavAudio
import io.github.vexpaer.openlistener.security.CredentialsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounded in-memory audio chunks; one ordered network worker, no PCM written to disk. */
class CloudChunkAsr(
    private val provider: String,
    private val credential: (String) -> String?,
    private val language: String,
    client: OkHttpClient = OkHttpClient(),
    private val allowLocalhost: Boolean = false
) : AsrEngine {
    constructor(provider: String, credentials: CredentialsStore, language: String) :
        this(provider, { credentials.get("asr_$provider", it) }, language)
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val output = Channel<TranscriptEvent>(16)
    override val events = output.receiveAsFlow()
    private val failures = MutableSharedFlow<Throwable>(replay = 1)
    val errors = failures.asSharedFlow()
    private data class Chunk(val pcm: ByteArray, val start: Long, val end: Long)
    private val chunks = Channel<Chunk>(3)
    private val buffer = ByteArrayOutputStream()
    private var worker: Job? = null
    private var session: String? = null
    private var sequence = 0L
    private var acceptedBytes = 0L
    private var silenceBytes = 0
    @Volatile private var failure: Throwable? = null
    @Volatile private var closed = false
    private var finished = false
    private var paused = false
    private fun model() = credential("model")?.takeIf { it.isNotBlank() }
        ?: when (provider) { "qwen" -> "qwen3-asr-flash"; "openai" -> "gpt-transcribe"; else -> "" }
    private fun endpoint(): HttpUrl? {
        val base = credential("base_url")?.takeIf { it.isNotBlank() }
            ?: when (provider) { "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"; "openai" -> "https://api.openai.com/v1"; else -> "" }
        val url = base.trimEnd('/').toHttpUrlOrNull() ?: return null
        if ((!url.isHttps && !(allowLocalhost && url.host in setOf("localhost", "127.0.0.1"))) ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
        return (base.trimEnd('/') + if (provider == "qwen") "/chat/completions" else "/audio/transcriptions").toHttpUrlOrNull()
    }
    override val capabilities = AsrCapabilities(provider, "$provider · Cloud speech", false,
        setOf("en", "zh-CN", "ja", "ko", "fr", "de", "es"), true)
    override val availability: EngineAvailability get() = when {
        closed -> EngineAvailability.Unavailable("Speech engine is closed")
        credential("api_key").isNullOrBlank() || model().isBlank() -> EngineAvailability.NeedsCredentials("Configure the speech API key and model in Settings")
        endpoint() == null -> EngineAvailability.Unavailable("Speech API requires a valid HTTPS Base URL")
        else -> EngineAvailability.Ready
    }
    override suspend fun prepare() = availability
    override suspend fun start(sessionId: String) {
        check(availability == EngineAvailability.Ready && session == null)
        session = sessionId
        worker = scope.launch {
            try {
                for (chunk in chunks) {
                    val text = transcribe(chunk.pcm)
                    if (text.isNotBlank()) output.send(TranscriptEvent(sessionId, sequence++, false, text, chunk.start, chunk.end))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error; chunks.close(error); failures.emit(error) }
        }
    }
    override suspend fun acceptPcm(pcm16: ByteArray) {
        check(session != null && !paused && !finished && !closed)
        failure?.let { throw it }
        require(pcm16.size % 2 == 0 && pcm16.size <= 32000)
        buffer.write(pcm16); acceptedBytes += pcm16.size
        var power = 0.0
        for (i in pcm16.indices step 2) {
            val sample = ((pcm16[i].toInt() and 255) or (pcm16[i + 1].toInt() shl 8)).toShort().toDouble()
            power += sample * sample
        }
        if (power / (pcm16.size / 2).coerceAtLeast(1) < 180.0 * 180.0) silenceBytes += pcm16.size else silenceBytes = 0
        if (buffer.size() >= 384000 || (buffer.size() >= 64000 && silenceBytes >= 16000)) enqueue()
    }
    private fun enqueue() {
        if (buffer.size() == 0) return
        val pcm = buffer.toByteArray()
        check(chunks.trySend(Chunk(pcm, (acceptedBytes - pcm.size) / 32, acceptedBytes / 32)).isSuccess) {
            "Speech API cannot keep up. Check the network or choose a faster model."
        }
        buffer.reset(); silenceBytes = 0
    }
    override suspend fun pause() { enqueue(); paused = true }
    override suspend fun resume() { check(!finished && !closed); paused = false }
    override suspend fun flush() {
        if (!finished) { enqueue(); chunks.close(); finished = true }
        worker?.join()
        failure?.let { throw it }
    }
    override suspend fun stop() = flush()
    override suspend fun close() {
        closed = true; scope.cancel(); worker?.join(); chunks.cancel(); output.close(); buffer.reset()
    }
    private suspend fun transcribe(pcm: ByteArray): String {
        val wav = WavAudio.encode(pcm)
        val body = if (provider == "qwen") buildJsonObject {
            put("model", model()); put("stream", false)
            putJsonArray("messages") { add(buildJsonObject {
                put("role", "user"); putJsonArray("content") { add(buildJsonObject {
                    put("type", "input_audio"); putJsonObject("input_audio") {
                        put("data", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav))
                    }
                }) }
            }) }
            putJsonObject("asr_options") { put("language", language.substringBefore('-')); put("enable_itn", true) }
        }.toString().toRequestBody("application/json".toMediaType()) else MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model()).addFormDataPart("language", language.substringBefore('-'))
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType())).build()
        val request = Request.Builder().url(checkNotNull(endpoint())).header("Authorization", "Bearer ${credential("api_key")}").post(body).build()
        val response = client.newCall(request).await()
        return response.use {
            check(it.isSuccessful) { "Speech API HTTP ${it.code}. Check credentials, model and quota." }
            val source = it.body?.source() ?: error("Empty speech API response")
            source.request(1_048_577)
            val bytes = source.buffer.readByteArray()
            check(bytes.size <= 1_048_576) { "Speech API response is too large" }
            val json = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            if (provider == "qwen") json["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content.trim()
            else json["text"]!!.jsonPrimitive.content.trim()
        }
    }
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Speech API network request failed")) }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) { _, value, _ -> value.close() } else response.close()
            }
        })
    }
}
