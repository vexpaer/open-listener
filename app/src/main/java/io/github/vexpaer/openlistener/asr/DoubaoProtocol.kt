package io.github.vexpaer.openlistener.asr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The four-byte binary envelope used by Doubao SAUC v3.
 * All integer fields are big-endian. Payload lengths count transmitted bytes.
 */
object DoubaoProtocol {
    const val VERSION = 0x1
    const val HEADER_SIZE = 4

    const val TYPE_FULL_CLIENT_REQUEST = 0x1
    const val TYPE_AUDIO_ONLY = 0x2
    const val TYPE_FULL_SERVER_RESPONSE = 0x9
    const val TYPE_SERVER_ERROR_RESPONSE = 0xF

    const val SERIALIZATION_NONE = 0x0
    const val SERIALIZATION_JSON = 0x1
    const val COMPRESSION_NONE = 0x0
    const val COMPRESSION_GZIP = 0x1

    const val FLAG_NONE = 0x0
    const val FLAG_POSITIVE_SEQUENCE = 0x1
    const val FLAG_NEGATIVE_SEQUENCE = 0x2
    const val FLAG_LAST_AUDIO = FLAG_NEGATIVE_SEQUENCE
    const val FLAG_NEGATIVE_SEQUENCE_WITH_SEQUENCE = 0x3

    fun requestHeader(
        messageType: Int,
        flags: Int = FLAG_NONE,
        serialization: Int = SERIALIZATION_JSON,
        compression: Int = COMPRESSION_GZIP
    ): ByteArray {
        require(messageType in 0..0xF) { "messageType must fit one nibble" }
        require(flags in 0..0xF) { "flags must fit one nibble" }
        return byteArrayOf(
            ((VERSION shl 4) or (HEADER_SIZE / 4)).toByte(),
            ((messageType shl 4) or flags).toByte(),
            ((serialization shl 4) or compression).toByte(),
            0
        )
    }

    fun fullClientRequest(payload: ByteArray, sequence: Int = 1): ByteArray =
        sequencedFrame(
            requestHeader(
                TYPE_FULL_CLIENT_REQUEST,
                FLAG_POSITIVE_SEQUENCE,
                SERIALIZATION_JSON,
                COMPRESSION_GZIP
            ),
            sequence,
            payload
        )

    fun audioFrame(pcm16: ByteArray, sequence: Int, last: Boolean = false): ByteArray {
        require(sequence > 0) { "sequence must be positive before the final packet" }
        val flags = if (last) FLAG_NEGATIVE_SEQUENCE_WITH_SEQUENCE else FLAG_POSITIVE_SEQUENCE
        val signedSequence = if (last) -sequence else sequence
        return sequencedFrame(
            // The official sample keeps JSON serialization in the common header even though audio bytes are raw PCM.
            requestHeader(TYPE_AUDIO_ONLY, flags, SERIALIZATION_JSON, COMPRESSION_GZIP),
            signedSequence,
            pcm16
        )
    }

    /** Fixture/helper for protocol tests and local WebSocket mocks. */
    fun serverResponse(payload: ByteArray, sequence: Int = 1, last: Boolean = false): ByteArray =
        sequencedFrame(
            requestHeader(
                TYPE_FULL_SERVER_RESPONSE,
                if (last) FLAG_NEGATIVE_SEQUENCE_WITH_SEQUENCE else FLAG_POSITIVE_SEQUENCE,
                SERIALIZATION_JSON,
                COMPRESSION_GZIP
            ),
            if (last) -kotlin.math.abs(sequence) else kotlin.math.abs(sequence),
            payload
        )

    fun serverError(code: Int, message: String, sequence: Int? = null): ByteArray {
        val flags = if (sequence == null) FLAG_NONE else FLAG_POSITIVE_SEQUENCE
        val header = requestHeader(TYPE_SERVER_ERROR_RESPONSE, flags, SERIALIZATION_JSON, COMPRESSION_GZIP)
        val body = gzip(message.toByteArray(Charsets.UTF_8))
        val output = ByteArrayOutputStream()
        output.write(header)
        if (sequence != null) output.write(intBytes(sequence))
        output.write(intBytes(code))
        output.write(intBytes(body.size))
        output.write(body)
        return output.toByteArray()
    }

    fun parseServerFrame(bytes: ByteArray): ServerFrame {
        require(bytes.size >= HEADER_SIZE) { "Doubao frame is shorter than its header" }
        val versionAndSize = bytes[0].toInt() and 0xFF
        val version = versionAndSize ushr 4
        val headerWords = versionAndSize and 0x0F
        require(version == VERSION) { "Unsupported Doubao protocol version: $version" }
        require(headerWords >= 1) { "Doubao header length must be positive" }
        val headerBytes = headerWords * 4
        require(bytes.size >= headerBytes) { "Doubao header is truncated" }

        val typeAndFlags = bytes[1].toInt() and 0xFF
        val type = typeAndFlags ushr 4
        val flags = typeAndFlags and 0x0F
        val serializationAndCompression = bytes[2].toInt() and 0xFF
        val serialization = serializationAndCompression ushr 4
        val compression = serializationAndCompression and 0x0F
        var offset = headerBytes
        var sequence: Int? = null
        var event: Int? = null
        if ((flags and FLAG_POSITIVE_SEQUENCE) != 0) {
            require(bytes.size >= offset + 4) { "Doubao sequence is truncated" }
            sequence = readInt(bytes, offset)
            offset += 4
        }
        if ((flags and 0x4) != 0) {
            require(bytes.size >= offset + 4) { "Doubao event is truncated" }
            event = readInt(bytes, offset)
            offset += 4
        }

        var errorCode: Int? = null
        var declaredLength: Int? = null
        when (type) {
            TYPE_FULL_SERVER_RESPONSE -> {
                require(bytes.size >= offset + 4) { "Doubao response length is truncated" }
                declaredLength = readInt(bytes, offset)
                require(declaredLength >= 0 && bytes.size >= offset + 4L + declaredLength) {
                    "Doubao response payload length is invalid: $declaredLength"
                }
                offset += 4
            }
            TYPE_SERVER_ERROR_RESPONSE -> {
                require(bytes.size >= offset + 8) { "Doubao error envelope is truncated" }
                errorCode = readInt(bytes, offset)
                declaredLength = readInt(bytes, offset + 4)
                require(declaredLength >= 0 && bytes.size >= offset + 8L + declaredLength) {
                    "Doubao error payload length is invalid: $declaredLength"
                }
                offset += 8
            }
        }
        val payloadLength = declaredLength ?: (bytes.size - offset)
        require(payloadLength >= 0 && offset + payloadLength <= bytes.size) { "Doubao payload is truncated" }
        val wirePayload = bytes.copyOfRange(offset, offset + payloadLength)
        val payload = when (compression) {
            COMPRESSION_NONE -> wirePayload
            COMPRESSION_GZIP -> gunzip(wirePayload)
            else -> throw IllegalArgumentException("Unsupported Doubao compression: $compression")
        }
        return ServerFrame(
            messageType = type,
            flags = flags,
            serialization = serialization,
            compression = compression,
            payload = payload,
            sequence = sequence,
            event = event,
            payloadLength = payloadLength,
            errorCode = errorCode,
            isLastPackage = (flags and FLAG_NEGATIVE_SEQUENCE) != 0
        )
    }

    private fun sequencedFrame(header: ByteArray, sequence: Int, payload: ByteArray): ByteArray {
        val wirePayload = gzip(payload)
        return ByteArrayOutputStream(header.size + 8 + wirePayload.size).apply {
            write(header)
            write(intBytes(sequence))
            write(intBytes(wirePayload.size))
            write(wirePayload)
        }.toByteArray()
    }

    private fun gzip(input: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(input) }
        output.toByteArray()
    }

    private fun gunzip(input: ByteArray): ByteArray {
        if (input.isEmpty()) return input
        return GZIPInputStream(ByteArrayInputStream(input)).use { gzip ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = gzip.read(buffer)
                if (count == -1) break
                require(output.size() + count <= 4 * 1024 * 1024) { "Doubao decompressed payload exceeds 4 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}

data class ServerFrame(
    val messageType: Int,
    val flags: Int,
    val serialization: Int,
    val compression: Int,
    val payload: ByteArray,
    val sequence: Int? = null,
    val event: Int? = null,
    val payloadLength: Int = payload.size,
    val errorCode: Int? = null,
    val isLastPackage: Boolean = false
)
