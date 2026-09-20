package com.qingke.schedule.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.MAX_IMPORT_BYTES
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P4／A10 system capability contract on a real device: a real document provider stream is read through the
 * bounded reader, a 5 MiB document is accepted, one byte more is rejected without trusting the provider size
 * metadata, and provider failures become one clear Chinese message.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleFileAccessDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver
    private val access = ScheduleFileAccess(resolver)

    @Test
    fun aRealContentResolverDocumentRoundTripsTheVersionOneBackup() = runBlocking {
        assertTrue("the document provider contract needs API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val uri = createDocument("a10-roundtrip-${System.nanoTime()}.json")
        try {
            val bytes = VERSION_ONE_JSON.encodeToByteArray()
            access.write(uri, bytes)

            assertArrayEquals("the provider must hold exactly the written JSON", bytes, readDirect(uri))

            val preview = ScheduleImportPreview.of(ScheduleDataDecoder.decode(access.read(uri)))
            assertEquals("重复与反序学期", preview.semesterName)
            assertEquals(2, preview.courseCount)
            assertEquals("2026-09-02T12:00:00.000Z", preview.updatedAt)
            assertEquals("qingke-schedule-2026-09-03.json", ScheduleDataTransfer.exportFileName(java.time.LocalDate.of(2026, 9, 3)))
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun exactlyFiveMiBIsAcceptedWhileOneByteMoreIsRejectedOnTheRealStream() = runBlocking {
        val exactUri = createDocument("a10-exact-${System.nanoTime()}.json")
        val tooLargeUri = createDocument("a10-too-large-${System.nanoTime()}.json")
        try {
            val text = EMPTY_SEMESTER_JSON
            val exact = (text + " ".repeat(MAX_IMPORT_BYTES - text.length)).encodeToByteArray()
            access.write(exactUri, exact)
            // The provider size column may lag behind the written bytes; the reader never consults it.
            assertEquals(MAX_IMPORT_BYTES, access.read(exactUri).size)
            assertEquals(null, ScheduleDataDecoder.decode(access.read(exactUri)).semester)

            val tooLarge = exact + ' '.code.toByte()
            access.write(tooLargeUri, tooLarge)
            assertEquals("the document itself must really hold 5 MiB + 1 bytes", MAX_IMPORT_BYTES + 1, readDirect(tooLargeUri).size)
            try {
                access.read(tooLargeUri)
                fail("Expected ScheduleDataException.FileTooLarge")
            } catch (error: ScheduleDataException.FileTooLarge) {
                assertTrue(error.message.orEmpty().isNotBlank())
            }
        } finally {
            resolver.delete(exactUri, null, null)
            resolver.delete(tooLargeUri, null, null)
        }
    }

    @Test
    fun rewritingTheSameDocumentReplacesTheBackupThroughTheRealProviderStream() = runBlocking {
        val uri = createDocument("a10-rewrite-${System.nanoTime()}.json")
        try {
            val small = EMPTY_SEMESTER_JSON.encodeToByteArray()
            access.write(uri, small)
            assertArrayEquals(small, readDirect(uri))

            // CreateDocument always hands us a fresh document, and the standard SAF provider truncates a
            // replaced document because "rwt" maps to MODE_TRUNCATE. MediaStore keeps the old tail on a
            // *shrinking* rewrite, so this test only asserts the byte-exact replacement it can guarantee.
            val large = VERSION_ONE_JSON.encodeToByteArray()
            access.write(uri, large)
            assertArrayEquals(large, readDirect(uri))
            assertEquals(2, ScheduleDataDecoder.decode(access.read(uri)).courses.size)
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun aMissingDocumentReportsOneClearChineseMessageForReadingAndWriting() = runBlocking {
        val missing = Uri.parse("content://com.qingke.schedule.absent/documents/1")

        val readFailure = expectFileException { access.read(missing) }
        assertTrue(readFailure.message.orEmpty().startsWith("无法打开所选文件"))

        val writeFailure = expectFileException { access.write(missing, byteArrayOf(1, 2, 3)) }
        assertTrue(
            writeFailure.message.orEmpty().startsWith("无法创建所选文件") ||
                writeFailure.message.orEmpty().startsWith("无法写入所选文件"),
        )
    }

    private suspend fun expectFileException(block: suspend () -> Unit): ScheduleFileException = try {
        block()
        fail("Expected ScheduleFileException")
        error("unreachable")
    } catch (error: Throwable) {
        if (error is ScheduleFileException) error else throw error
    }

    private fun createDocument(name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, ScheduleDataTransfer.JSON_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "the document provider did not create $name"
        }
    }

    private fun readDirect(uri: Uri): ByteArray {
        val stream: InputStream = requireNotNull(resolver.openInputStream(uri))
        return stream.use { it.readBytes() }
    }

    private companion object {
        const val EMPTY_SEMESTER_JSON =
            """{"schemaVersion":1,"semester":null,"courses":[],"updatedAt":"1970-01-01T00:00:00.000Z"}"""

        const val VERSION_ONE_JSON =
            """{"schemaVersion":1,"semester":{"id":"semester-duplicate","name":"重复与反序学期","startDate":"2026-09-02","totalWeeks":18,"periods":[{"number":2,"startTime":"08:00","endTime":"08:45"},{"number":1,"startTime":"08:55","endTime":"09:40"}]},"courses":[{"id":"duplicate","name":"第一门","teacher":"","color":"#287B74","schedules":[{"id":"shared","dayOfWeek":1,"startPeriod":2,"endPeriod":2,"startWeek":1,"endWeek":18,"repeat":"odd","classroom":"B202"}]},{"id":"duplicate","name":"第二门","teacher":"教师","color":"#E65A4F","schedules":[{"id":"shared","dayOfWeek":7,"startPeriod":1,"endPeriod":1,"startWeek":2,"endWeek":4,"repeat":"even","classroom":""}]}],"updatedAt":"2026-09-02T12:00:00.000Z"}"""
    }
}
