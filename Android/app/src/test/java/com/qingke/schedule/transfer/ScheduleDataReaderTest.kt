package com.qingke.schedule.transfer

import com.qingke.schedule.domain.MAX_IMPORT_BYTES
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * P4／A10: the bounded read contract. The limit must be enforced on the real stream, never on file metadata,
 * and exactly 5 MiB stays acceptable.
 */
class ScheduleDataReaderTest {
    @Test
    fun exactlyFiveMiBIsAcceptedAndTheNextByteIsRejected() {
        val exact = ByteArray(MAX_IMPORT_BYTES) { ' '.code.toByte() }
        assertEquals(MAX_IMPORT_BYTES, ScheduleDataReader.readBounded(ByteArrayInputStream(exact)).size)

        val onePast = CountingStream(MAX_IMPORT_BYTES + 1)
        expectFileTooLarge { ScheduleDataReader.readBounded(onePast) }
        assertEquals("only the probe byte past 5 MiB may be read", MAX_IMPORT_BYTES + 1, onePast.read)
    }

    @Test
    fun anEndlessStreamStopsAtTheProbeByteInsteadOfReadingOn() {
        val endless = EndlessStream()
        expectFileTooLarge { ScheduleDataReader.readBounded(endless) }
        assertEquals(MAX_IMPORT_BYTES + 1, endless.read)
    }

    @Test
    fun theReaderConsumesBytesVerbatimAndClosesTheStream() {
        val payload = byteArrayOf(0, 1, 2, 3, -1, -2, 127, -128)
        val stream = TrackingStream(payload)
        val read = ScheduleDataReader.readBounded(stream)
        assertTrue("bytes must survive without text replacement", payload.contentEquals(read))
        assertTrue("the system document stream must be closed", stream.closed)
    }

    private fun expectFileTooLarge(block: () -> Unit) {
        try {
            block()
            fail("Expected ScheduleDataException.FileTooLarge")
        } catch (error: ScheduleDataException.FileTooLarge) {
            assertTrue(error.message!!.isNotBlank())
        }
    }

    private class CountingStream(private val size: Int) : InputStream() {
        var read = 0
        override fun read(): Int = if (read >= size) -1 else { read++; 0 }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (read >= size) return -1
            val count = minOf(length, size - read)
            read += count
            return count
        }
    }

    private class EndlessStream : InputStream() {
        var read = 0
        override fun read(): Int { read++; return 0 }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int { read += length; return length }
    }

    private class TrackingStream(private val payload: ByteArray) : InputStream() {
        var closed = false
        private var position = 0
        override fun read(): Int = if (position >= payload.size) -1 else payload[position++].toInt()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= payload.size) return -1
            val count = minOf(length, payload.size - position)
            payload.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun close() { closed = true }
    }
}
