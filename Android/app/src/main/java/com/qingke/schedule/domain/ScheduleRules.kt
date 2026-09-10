package com.qingke.schedule.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class ScheduleConflict(
    val candidateCourse: Course,
    val candidateSchedule: CourseSchedule,
    val existingCourse: Course,
    val existingSchedule: CourseSchedule,
    val weeks: List<Int>,
)

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

    fun periodRangesOverlap(left: CourseSchedule, right: CourseSchedule): Boolean =
        left.startPeriod <= right.endPeriod && right.startPeriod <= left.endPeriod

    fun overlappingWeeks(left: CourseSchedule, right: CourseSchedule): List<Int> {
        val firstWeek = maxOf(left.startWeek, right.startWeek)
        val lastWeek = minOf(left.endWeek, right.endWeek)
        return if (firstWeek > lastWeek) emptyList() else (firstWeek..lastWeek).filter {
            scheduleApplies(left, it) && scheduleApplies(right, it)
        }
    }

    fun schedulesConflict(left: CourseSchedule, right: CourseSchedule): Boolean =
        left.dayOfWeek == right.dayOfWeek &&
            periodRangesOverlap(left, right) &&
            overlappingWeeks(left, right).isNotEmpty()

    fun conflicts(candidate: Course, existingCourses: List<Course>): List<ScheduleConflict> = buildList {
        candidate.schedules.forEach { candidateSchedule ->
            existingCourses.filter { it.id != candidate.id }.forEach { existingCourse ->
                existingCourse.schedules.forEach { existingSchedule ->
                    val weeks = overlappingWeeks(candidateSchedule, existingSchedule)
                    if (candidateSchedule.dayOfWeek == existingSchedule.dayOfWeek &&
                        periodRangesOverlap(candidateSchedule, existingSchedule) && weeks.isNotEmpty()
                    ) {
                        add(ScheduleConflict(candidate, candidateSchedule, existingCourse, existingSchedule, weeks))
                    }
                }
            }
        }
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
