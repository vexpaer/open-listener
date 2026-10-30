package io.github.vexpaer.openlistener.asr

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream

/** AST v2 uses bare protobuf messages, NOT the SAUC four-byte envelope.
 * Field numbers verified against the official Java demo's ast_service, rpcmeta and au_base schemas.
 * Only S2T fields are emitted; target_audio and speaker/TTS parameters are deliberately absent.
 */
object DoubaoAstProtocol {
    const val START = 100
    const val FINISH = 102
    const val STARTED = 150
    const val FINISHED = 152
    const val FAILED = 153
    const val AUDIO = 200
    const val SOURCE_START = 650
    const val SOURCE_DELTA = 651
    const val SOURCE_END = 652
    const val TARGET_START = 653
    const val TARGET_DELTA = 654
    const val TARGET_END = 655
    const val ENDPOINT = "wss://openspeech.bytedance.com/api/v4/ast/v2/translate"
    const val RESOURCE = "volc.service_type.10053"

    data class Response(val event: Int, val text: String = "", val start: Long? = null,
        val end: Long? = null, val code: Int = 0, val session: String = "")

    fun start(session: String, source: String, target: String): ByteArray = message {
        writeByteArray(1, meta(session)); writeInt32(2, START)
        writeByteArray(3, message { writeString(1, "open-listener"); writeString(3, "Android") })
        writeByteArray(4, message {
            writeString(4, "wav"); writeString(5, "raw"); writeInt32(7, 16000); writeInt32(8, 16); writeInt32(9, 1)
        })
        writeByteArray(6, message {
            writeString(1, "s2t"); writeString(2, language(source)); writeString(3, language(target))
        })
    }
    fun audio(session: String, pcm: ByteArray): ByteArray = message {
        writeByteArray(1, meta(session)); writeInt32(2, AUDIO)
        writeByteArray(4, message { writeByteArray(14, pcm) })
    }
    fun finish(session: String): ByteArray = message { writeByteArray(1, meta(session)); writeInt32(2, FINISH) }
    fun language(value: String): String = if (value.startsWith("zh")) "zh" else value.substringBefore('-')
    private fun meta(session: String) = message { writeString(6, session) }
    internal fun message(block: CodedOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().let { output ->
        CodedOutputStream.newInstance(output).apply { block(); flush() }; output.toByteArray()
    }
    fun parse(bytes: ByteArray): Response {
        require(bytes.size <= 4 * 1024 * 1024) { "AST response is too large" }
        var event = 0; var text = ""; var start: Long? = null; var end: Long? = null; var code = 0; var session = ""
        val input = CodedInputStream.newInstance(bytes).apply { setRecursionLimit(16) }
        while (!input.isAtEnd) {
            val tag = input.readTag()
            when (tag) {
                10 -> {
                    val meta = CodedInputStream.newInstance(input.readByteArray()).apply { setRecursionLimit(16) }
                    while (!meta.isAtEnd) {
                        val field = meta.readTag()
                        when (field) {
                            10 -> session = meta.readStringRequireUtf8()
                            24 -> code = meta.readInt32()
                            else -> require(meta.skipField(field)) { "Invalid AST metadata" }
                        }
                    }
                }
                16 -> event = input.readInt32()
                34 -> text = input.readStringRequireUtf8().also { require(it.length <= 131072) { "AST subtitle is too large" } }
                40 -> start = input.readInt32().toLong()
                48 -> end = input.readInt32().toLong()
                else -> require(input.skipField(tag)) { "Invalid AST response" } // Includes audio: never decode or play it.
            }
        }
        require(event != 0) { "AST response has no event" }
        return Response(event, text, start, end, code, session)
    }
}

/** Source/target streams may interleave. Pair by timestamp when provided, then by sentence order.
 * A finalized source is persisted immediately; later translation deltas update that same row.
 */
internal class AstSubtitles(private val session: String, private val offset: Long, private val nextId: () -> Long) {
    private data class Sentence(val id: Long, val ordinal: Int, var start: Long? = null, var end: Long? = null,
        var original: String = "", var target: String = "", var sourceStarted: Boolean = false, var targetStarted: Boolean = false,
        var sourceDone: Boolean = false, var targetDone: Boolean = false, var revision: Int = 0)
    private val sentences = mutableListOf<Sentence>()
    private var sourceCount = 0
    private var targetCount = 0
    private var source: Sentence? = null
    private var target: Sentence? = null

    private fun begin(original: Boolean, start: Long?): Sentence {
        val ordinal = if (original) ++sourceCount else ++targetCount
        fun available(s: Sentence) = if (original) !s.sourceStarted else !s.targetStarted
        val existing = start?.let { time -> sentences.firstOrNull { it.start == time && available(it) } }
            ?: sentences.firstOrNull { it.ordinal == ordinal && available(it) }
        val sentence = existing ?: Sentence(nextId(), ordinal, start).also {
            if (sentences.size >= 128) {
                val completed = sentences.firstOrNull { s -> s.sourceDone && s.targetDone }
                check(completed != null) { "AST subtitle backlog is full" }; sentences.remove(completed)
            }
            sentences += it
        }
        if (original) { sentence.sourceStarted = true; if (start != null) sentence.start = start }
        else sentence.targetStarted = true
        return sentence
    }
    fun accept(message: DoubaoAstProtocol.Response): TranscriptEvent? {
        when (message.event) {
            DoubaoAstProtocol.SOURCE_START -> {
                check(source == null) { "Overlapping AST source sentences" }
                source = begin(true, message.start).also { it.original = message.text }
            }
            DoubaoAstProtocol.TARGET_START -> {
                check(target == null) { "Overlapping AST translation sentences" }
                target = begin(false, message.start).also { it.target = message.text }
            }
        }
        val sentence = when (message.event) {
            DoubaoAstProtocol.SOURCE_START, DoubaoAstProtocol.SOURCE_DELTA, DoubaoAstProtocol.SOURCE_END -> checkNotNull(source) { "AST source sentence has not started" }
            DoubaoAstProtocol.TARGET_START, DoubaoAstProtocol.TARGET_DELTA, DoubaoAstProtocol.TARGET_END -> checkNotNull(target) { "AST translation sentence has not started" }
            else -> return null
        }
        when (message.event) {
            DoubaoAstProtocol.SOURCE_DELTA -> sentence.original += message.text
            DoubaoAstProtocol.TARGET_DELTA -> sentence.target += message.text
            DoubaoAstProtocol.SOURCE_END -> {
                if (message.text.isNotEmpty()) sentence.original = message.text
                sentence.start = message.start ?: sentence.start; sentence.end = message.end
                sentence.sourceDone = true; source = null
            }
            DoubaoAstProtocol.TARGET_END -> {
                if (message.text.isNotEmpty()) sentence.target = message.text
                sentence.targetDone = true; target = null
            }
        }
        check(sentence.original.length <= 131072 && sentence.target.length <= 131072) { "AST sentence is too large" }
        if (sentence.original.isBlank()) return null
        return TranscriptEvent(session, sentence.id, !sentence.sourceDone, sentence.original,
            offset + (sentence.start ?: 0), sentence.end?.let { offset + it }, ++sentence.revision,
            sentence.target.takeIf { it.isNotEmpty() }, sentence.targetDone)
    }
    fun verifyFinished() {
        check(sentences.all { it.sourceDone && it.targetDone }) { "豆包同传结束时仍有未确认的字幕，请检查连接与额度。" }
    }
}
