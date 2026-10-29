package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.asr.DoubaoProtocol
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoProtocolTest {
    @Test fun configurationUsesFourByteHeaderThenSequenceThenLengthThenGzip() {
        val payload = "{\"audio\":{\"format\":\"pcm\"}}".toByteArray()
        val frame = DoubaoProtocol.fullClientRequest(payload)
        // Independent assertions; a broken encoder/decoder cannot mask each other's header layout.
        assertArrayEquals(byteArrayOf(0x11, 0x11, 0x11, 0), frame.copyOfRange(0, 4))
        assertEquals(1, ByteBuffer.wrap(frame, 4, 4).int)
        assertEquals(frame.size - 12, ByteBuffer.wrap(frame, 8, 4).int)
        assertArrayEquals(payload, GZIPInputStream(ByteArrayInputStream(frame, 12, frame.size - 12)).use { it.readBytes() })
    }

    @Test fun audioSequenceIsNotCountedAsPayloadAndFinalSequenceIsNegative() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val frame = DoubaoProtocol.audioFrame(pcm, sequence = 7)
        assertArrayEquals(byteArrayOf(0x11, 0x21, 0x11, 0), frame.copyOfRange(0, 4))
        assertEquals(7, ByteBuffer.wrap(frame, 4, 4).int)
        assertEquals(frame.size - 12, ByteBuffer.wrap(frame, 8, 4).int)
        assertArrayEquals(pcm, GZIPInputStream(ByteArrayInputStream(frame, 12, frame.size - 12)).use { it.readBytes() })
        val finalFrame = DoubaoProtocol.audioFrame(ByteArray(0), sequence = 8, last = true)
        assertArrayEquals(byteArrayOf(0x11, 0x23, 0x11, 0), finalFrame.copyOfRange(0, 4))
        assertEquals(-8, ByteBuffer.wrap(finalFrame, 4, 4).int)
        assertEquals(0, GZIPInputStream(ByteArrayInputStream(finalFrame, 12, finalFrame.size - 12)).use { it.readBytes() }.size)
    }

    @Test fun parsesIndependentGzipServerFixture() {
        // Artificial text, packed independently with Python struct.pack('>iI', 7, 94) + gzip.compress.
        val bytes = hex(
            "11 91 11 00 00 00 00 07 00 00 00 5e 1f 8b 08 00 00 00 00 00 02 ff " +
                "2d c8 41 0a 80 20 00 04 c0 bf ec d9 83 74 f4 2b 11 21 b9 91 60 06 ba 42 20 fe 3d 0f " +
                "1d 67 3a 0a 6b 4b 82 eb 68 12 8b cf 07 2b dc da 21 be b3 71 31 a5 07 06 55 be 68 57 " +
                "bc 09 67 0d 98 c3 8f c5 4e 06 9e 31 47 4d aa 34 8e 6d 8c 0f 4d f1 4b 68 5a 00 00 00"
        )
        val parsed = DoubaoProtocol.parseServerFrame(bytes)
        assertEquals(9, parsed.messageType)
        assertEquals(7, parsed.sequence)
        assertEquals(94, parsed.payloadLength)
        assertFalse(parsed.isLastPackage)
        assertEquals(
            "{\"result\":{\"utterances\":[{\"text\":\"hello\",\"start_time\":0,\"end_time\":200,\"definite\":true}]}}",
            parsed.payload.toString(Charsets.UTF_8)
        )
    }

    @Test fun parsesUncompressedNegativeFinalSequenceAndHeaderExtensions() {
        val frame = hex("12 93 10 00 aa bb cc dd ff ff ff fd 00 00 00 02 7b 7d")
        val parsed = DoubaoProtocol.parseServerFrame(frame)
        assertEquals(-3, parsed.sequence)
        assertEquals("{}", parsed.payload.toString(Charsets.UTF_8))
        assertTrue(parsed.isLastPackage)
    }

    @Test fun parsesErrorCodeBeforeLengthWithoutSequence() {
        val parsed = DoubaoProtocol.parseServerFrame(hex("11 f0 10 00 00 00 01 91 00 00 00 03 62 61 64"))
        assertEquals(401, parsed.errorCode)
        assertEquals(null, parsed.sequence)
        assertEquals("bad", parsed.payload.toString(Charsets.UTF_8))
    }

    @Test fun optionalEventAppearsAfterSequenceBeforePayloadLength() {
        val parsed = DoubaoProtocol.parseServerFrame(hex("11 95 10 00 00 00 00 01 00 00 00 64 00 00 00 02 7b 7d"))
        assertEquals(1, parsed.sequence)
        assertEquals(100, parsed.event)
        assertEquals("{}", parsed.payload.toString(Charsets.UTF_8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedLength() {
        DoubaoProtocol.parseServerFrame(hex("11 91 10 00 00 00 00 01 00 00 00 04 7b 7d"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroHeaderLength() {
        DoubaoProtocol.parseServerFrame(hex("10 90 10 00 00 00 00 00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedCompression() {
        DoubaoProtocol.parseServerFrame(hex("11 90 12 00 00 00 00 00"))
    }

    @Test(expected = java.io.IOException::class)
    fun corruptGzipIsNotSilentlyIgnored() {
        DoubaoProtocol.parseServerFrame(hex("11 90 11 00 00 00 00 02 7b 7d"))
    }

    private fun hex(value: String): ByteArray = value.split(' ').filter { it.isNotBlank() }
        .map { it.toInt(16).toByte() }.toByteArray()
}
