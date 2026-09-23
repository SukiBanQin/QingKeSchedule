package com.qingke.schedule.transfer

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** P4／A10: a system document operation failed; the message is the user-facing Chinese feedback. */
class ScheduleFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * P4／A10: the only place that touches a system document. Reading runs through [ScheduleDataReader], so the
 * 5 MiB bound applies to the real ContentResolver stream; writing truncates the target so a shorter backup
 * never leaves the tail of an older file behind. Both entry points use SAF content URIs only - no file://,
 * no custom file browser and no broad storage permission. Every provider failure becomes one clear Chinese
 * message, while the bounded-read rejection keeps its own [ScheduleDataException.FileTooLarge] message.
 */
class ScheduleFileAccess(private val contentResolver: ContentResolver) {
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val stream = try {
            contentResolver.openInputStream(uri)
        } catch (error: FileNotFoundException) {
            throw ScheduleFileException(OPEN_FAILURE + (error.message ?: "文件不可读"), error)
        } catch (error: SecurityException) {
            throw ScheduleFileException(OPEN_FAILURE + "没有访问权限", error)
        } catch (error: RuntimeException) {
            throw ScheduleFileException(OPEN_FAILURE + (error.message ?: "系统文件面板拒绝了访问"), error)
        }
        if (stream == null) throw ScheduleFileException(OPEN_FAILURE + "内容提供商没有返回数据流")
        try {
            ScheduleDataReader.readBounded(stream)
        } catch (error: IOException) {
            throw ScheduleFileException(READ_FAILURE + (error.message ?: "读取中断"), error)
        }
    }

    suspend fun write(uri: Uri, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val stream = try {
            contentResolver.openOutputStream(uri, WRITE_TRUNCATE)
        } catch (error: FileNotFoundException) {
            throw ScheduleFileException(CREATE_FAILURE + (error.message ?: "文件不可写"), error)
        } catch (error: SecurityException) {
            throw ScheduleFileException(CREATE_FAILURE + "没有访问权限", error)
        } catch (error: RuntimeException) {
            throw ScheduleFileException(CREATE_FAILURE + (error.message ?: "系统文件面板拒绝了访问"), error)
        }
        if (stream == null) throw ScheduleFileException(WRITE_FAILURE + "内容提供商没有返回数据流")
        try {
            stream.use { output ->
                output.write(bytes)
                output.flush()
            }
        } catch (error: IOException) {
            throw ScheduleFileException(WRITE_FAILURE + (error.message ?: "写入中断"), error)
        }
    }

    /** Documented read-write-truncate mode, so the provider replaces the whole document. */
    private companion object {
        const val WRITE_TRUNCATE = "rwt"
        const val OPEN_FAILURE = "无法打开所选文件："
        const val READ_FAILURE = "无法读取所选文件："
        const val CREATE_FAILURE = "无法创建所选文件："
        const val WRITE_FAILURE = "无法写入所选文件："
    }
}
