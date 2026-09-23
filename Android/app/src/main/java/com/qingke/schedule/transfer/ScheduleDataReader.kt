package com.qingke.schedule.transfer

import com.qingke.schedule.domain.MAX_IMPORT_BYTES
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * P4／A10: the one bounded reader behind every import entry point. It never trusts DISPLAY_NAME, SIZE,
 * available() or any provider metadata: it reads at most [MAX_IMPORT_BYTES] + 1 bytes, accepts exactly
 * 5 MiB, and rejects the input on the single probe byte past that limit instead of reading on.
 */
object ScheduleDataReader {
    fun readBounded(input: InputStream): ByteArray = input.use { stream ->
        val limit = MAX_IMPORT_BYTES + 1
        val output = ByteArrayOutputStream(INITIAL_CAPACITY)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (total < limit) {
            val count = stream.read(buffer, 0, minOf(buffer.size, limit - total))
            if (count < 0) break
            total += count
            output.write(buffer, 0, count)
        }
        if (total >= limit) throw ScheduleDataException.FileTooLarge
        output.toByteArray()
    }

    private const val INITIAL_CAPACITY = 64 * 1024
}
