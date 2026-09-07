package com.qingke.schedule.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SUPPORTED_SCHEMA_VERSION = 1
const val MAX_IMPORT_BYTES = 5 * 1_048_576

@Serializable
enum class RepeatRule {
    @SerialName("every") EVERY,
    @SerialName("odd") ODD,
    @SerialName("even") EVEN,
}

@Serializable
data class Period(
    val number: Int,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class Semester(
    val id: String,
    val name: String,
    val startDate: String,
    val totalWeeks: Int,
    val periods: List<Period>,
)

@Serializable
data class CourseSchedule(
    val id: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    @SerialName("repeat") val repeatRule: RepeatRule,
    val classroom: String,
)

@Serializable
data class Course(
    val id: String,
    val name: String,
    val teacher: String,
    val color: String,
    val schedules: List<CourseSchedule>,
)

@Serializable
data class ScheduleData(
    val schemaVersion: Int,
    val semester: Semester?,
    val courses: List<Course>,
    val updatedAt: String,
)
