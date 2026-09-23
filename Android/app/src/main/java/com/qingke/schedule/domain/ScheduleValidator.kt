package com.qingke.schedule.domain

import java.time.Instant

data class ValidationIssue(val path: String, val message: String)

object ScheduleValidator {
    fun validate(data: ScheduleData): List<ValidationIssue> = buildList {
        if (data.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            add(ValidationIssue("schemaVersion", "不支持的数据版本"))
        }
        if (!isUtcInstant(data.updatedAt)) {
            add(ValidationIssue("updatedAt", "更新时间格式无效"))
        }

        val semester = data.semester
        if (semester == null) {
            if (data.courses.isNotEmpty()) {
                add(ValidationIssue("courses", "没有学期时不能包含课程"))
            }
            return@buildList
        }

        addAll(validateSemester(semester))
        data.courses.forEachIndexed { index, course ->
            addAll(validateCourse(course, semester, index))
        }
    }

    private fun validateSemester(semester: Semester): List<ValidationIssue> = buildList {
        if (semester.id.isEmpty()) add(ValidationIssue("semester.id", "学期 ID 不能为空"))
        if (semester.name.isBlank()) add(ValidationIssue("semester.name", "请填写学期名称"))
        if (ScheduleRules.parseLocalDate(semester.startDate) == null) {
            add(ValidationIssue("semester.startDate", "请选择有效的开始日期"))
        }
        if (semester.totalWeeks !in 1..52) {
            add(ValidationIssue("semester.totalWeeks", "总周数需要在 1 到 52 之间"))
        }
        if (semester.periods.size !in 1..20) {
            add(ValidationIssue("semester.periods", "请设置 1 到 20 个节次"))
        }

        val numbers = mutableSetOf<Int>()
        semester.periods.forEachIndexed { index, period ->
            val path = "semester.periods.$index"
            val start = ScheduleRules.parseLocalTime(period.startTime)
            val end = ScheduleRules.parseLocalTime(period.endTime)
            if (period.number <= 0 || start == null || end == null || start >= end) {
                add(ValidationIssue(path, "节次时间无效"))
            }
            if (!numbers.add(period.number)) {
                add(ValidationIssue(path, "节次编号不能重复"))
            }
            if (index > 0 && semester.periods[index - 1].endTime > period.startTime) {
                add(ValidationIssue(path, "相邻节次的时间不能重叠"))
            }
        }
    }

    private fun validateCourse(course: Course, semester: Semester, index: Int): List<ValidationIssue> = buildList {
        val basePath = "courses.$index"
        val validPeriodNumbers = semester.periods.mapTo(mutableSetOf()) { it.number }
        if (course.id.isEmpty()) add(ValidationIssue("$basePath.id", "课程 ID 不能为空"))
        if (course.name.isBlank()) add(ValidationIssue("$basePath.name", "请填写课程名称"))
        if (!COLOR.matches(course.color)) {
            add(ValidationIssue("$basePath.color", "课程颜色需要使用 #RRGGBB 格式"))
        }
        if (course.schedules.isEmpty()) {
            add(ValidationIssue("$basePath.schedules", "至少需要一个上课安排"))
        }
        course.schedules.forEachIndexed { scheduleIndex, schedule ->
            val path = "$basePath.schedules.$scheduleIndex"
            if (schedule.id.isEmpty()) add(ValidationIssue("$path.id", "安排 ID 不能为空"))
            if (schedule.dayOfWeek !in 1..7) add(ValidationIssue("$path.dayOfWeek", "请选择星期"))
            if (schedule.startPeriod !in validPeriodNumbers || schedule.endPeriod !in validPeriodNumbers ||
                schedule.startPeriod > schedule.endPeriod) {
                add(ValidationIssue("$path.periods", "请选择有效的起止节次"))
            }
            if (schedule.startWeek < 1 || schedule.endWeek > semester.totalWeeks ||
                schedule.startWeek > schedule.endWeek) {
                add(ValidationIssue("$path.weeks", "请选择有效的起止周"))
            }
        }
    }

    private fun isUtcInstant(value: String): Boolean =
        value.endsWith("Z") && runCatching { Instant.parse(value) }.isSuccess

    private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")
}
