package com.qingke.schedule.transfer

import com.qingke.schedule.domain.ScheduleData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * P4／A10: the preview of one validated version-1 file. Building it only decodes and validates; the committed
 * schedule, the local preferences and the reminder registry are untouched until the user confirms.
 */
data class ScheduleImportPreview(
    val data: ScheduleData,
    val semesterName: String,
    val courseCount: Int,
    val updatedAt: String,
) {
    val summary: String get() = "学期：$semesterName\n课程：$courseCount 门\n更新时间：$updatedAt"

    companion object {
        const val NO_SEMESTER = "未设置学期"

        fun of(data: ScheduleData) = ScheduleImportPreview(
            data = data,
            semesterName = data.semester?.name ?: NO_SEMESTER,
            courseCount = data.courses.size,
            updatedAt = data.updatedAt,
        )
    }
}

/** P4／A10: the shared version-1 transfer copy, naming and system document MIME type. */
object ScheduleDataTransfer {
    const val JSON_MIME_TYPE = "application/json"
    const val NO_SEMESTER_EXPORT_HINT = "设置学期后可导出备份"
    const val EXPORT_SUCCESS_MESSAGE = "已导出备份文件"
    const val IMPORT_BLOCKED_NO_SEMESTER = "请先设置学期，再导出课表备份"
    const val REPLACE_NOTICE = "将整体替换当前课表"

    private const val FILE_NAME_PREFIX = "qingke-schedule-"
    private const val FILE_NAME_SUFFIX = ".json"

    fun exportFileName(date: LocalDate): String =
        FILE_NAME_PREFIX + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + FILE_NAME_SUFFIX

    fun importSuccessMessage(courseCount: Int): String = "已导入 $courseCount 门课程"

    fun previewMessage(preview: ScheduleImportPreview): String =
        preview.summary + "\n\n" + REPLACE_NOTICE
}
