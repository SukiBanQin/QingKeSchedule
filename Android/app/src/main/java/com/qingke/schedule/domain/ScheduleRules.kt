package com.qingke.schedule.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object ScheduleRules {
    fun teachingWeek(date: LocalDate, semester: Semester): Int {
        val semesterMonday = semester.startDateAsLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return ChronoUnit.WEEKS.between(semesterMonday, dateMonday).toInt() + 1
    }

    fun isTeachingWeekInSemester(week: Int, semester: Semester): Boolean =
        week in 1..semester.totalWeeks

    fun scheduleApplies(schedule: CourseSchedule, week: Int): Boolean =
        week in schedule.startWeek..schedule.endWeek && when (schedule.repeatRule) {
            RepeatRule.EVERY -> true
            RepeatRule.ODD -> week % 2 != 0
            RepeatRule.EVEN -> week % 2 == 0
        }

    fun parseLocalDate(value: String): LocalDate? =
        runCatching {
            if (!LOCAL_DATE.matches(value) || value.startsWith("0000-")) null else LocalDate.parse(value)
        }.getOrNull()

    fun parseLocalTime(value: String): LocalTime? =
        runCatching {
            if (!LOCAL_TIME.matches(value)) null else LocalTime.parse(value)
        }.getOrNull()

    private fun Semester.startDateAsLocalDate(): LocalDate =
        requireNotNull(parseLocalDate(startDate)) { "Semester startDate must be valid before calculating a teaching week." }

    private val LOCAL_DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val LOCAL_TIME = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
}
