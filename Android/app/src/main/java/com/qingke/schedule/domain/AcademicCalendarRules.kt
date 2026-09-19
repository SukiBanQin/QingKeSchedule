package com.qingke.schedule.domain

import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import java.time.LocalDate

/**
 * Pure academic-calendar rules shared by the today page, the week matrix and the future reminder
 * planner (A08), so no consumer has to depend on the presentation layer.
 */
sealed interface AcademicDayResolution {
    data class TeachingDay(val sourceDayOfWeek: Int, val isMakeup: Boolean) : AcademicDayResolution
    data class NonTeachingDay(val reason: String) : AcademicDayResolution
}

object AcademicCalendarResolver {
    /** Priority: explicit non-teaching date, makeup teaching day, weekend switch, ordinary weekday. */
    fun resolve(date: LocalDate, preferences: AcademicCalendarPreferences): AcademicDayResolution {
        val value = date.toString()
        if (value in preferences.nonTeachingDates) {
            return AcademicDayResolution.NonTeachingDay("已设为停课日")
        }
        preferences.makeupTeachingDays.firstOrNull { it.date == value }?.let { makeup ->
            return AcademicDayResolution.TeachingDay(makeup.followsDayOfWeek, isMakeup = true)
        }
        val dayOfWeek = date.dayOfWeek.value
        if (preferences.weekendsAreNonTeachingDays && dayOfWeek >= 6) {
            return AcademicDayResolution.NonTeachingDay("周末默认停课")
        }
        return AcademicDayResolution.TeachingDay(dayOfWeek, isMakeup = false)
    }
}

/** One date can only be a non-teaching day or a makeup teaching day, never both. */
fun AcademicCalendarPreferences.withNonTeachingDate(date: LocalDate): AcademicCalendarPreferences {
    val value = date.toString()
    if (!AcademicCalendarPreferences.isStoredDate(value)) return this
    return copy(
        nonTeachingDates = nonTeachingDates.filterNot { it == value } + value,
        makeupTeachingDays = makeupTeachingDays.filterNot { it.date == value },
    ).normalized()
}

fun AcademicCalendarPreferences.withoutNonTeachingDate(date: String): AcademicCalendarPreferences =
    copy(nonTeachingDates = nonTeachingDates.filterNot { it == date }).normalized()

/** A repeated date replaces its previous source weekday instead of appending a duplicate. */
fun AcademicCalendarPreferences.withMakeupTeachingDay(date: LocalDate, followsDayOfWeek: Int): AcademicCalendarPreferences {
    val value = date.toString()
    if (!AcademicCalendarPreferences.isStoredDate(value) || followsDayOfWeek !in 1..7) return this
    return copy(
        makeupTeachingDays = makeupTeachingDays.filterNot { it.date == value } + MakeupTeachingDay(value, followsDayOfWeek),
        nonTeachingDates = nonTeachingDates.filterNot { it == value },
    ).normalized()
}

fun AcademicCalendarPreferences.withoutMakeupTeachingDay(date: String): AcademicCalendarPreferences =
    copy(makeupTeachingDays = makeupTeachingDays.filterNot { it.date == date }).normalized()

fun AcademicCalendarPreferences.withWeekendsAreNonTeachingDays(enabled: Boolean): AcademicCalendarPreferences =
    copy(weekendsAreNonTeachingDays = enabled).normalized()

fun AcademicCalendarPreferences.withLunchBreakEnabled(enabled: Boolean): AcademicCalendarPreferences =
    copy(lunchBreak = lunchBreak.copy(isEnabled = enabled)).normalized()

/**
 * Periods whose time range intersects the lunch break range. Periods win: the week matrix hides the
 * lunch break row whenever this list is not empty, and the settings page warns before saving it.
 * Touching ranges (a period ending exactly when the break starts, or starting exactly when it ends)
 * do not count as an overlap, so the break may sit in a gap, above all periods or below all of them.
 */
fun lunchBreakOverlappingPeriods(startTime: String, endTime: String, periods: List<Period>): List<Period> {
    val start = ScheduleRules.minutes(startTime) ?: return emptyList()
    val end = ScheduleRules.minutes(endTime) ?: return emptyList()
    if (start >= end) return emptyList()
    return periods.filter { period ->
        val periodStart = ScheduleRules.minutes(period.startTime) ?: return@filter false
        val periodEnd = ScheduleRules.minutes(period.endTime) ?: return@filter false
        periodStart < end && start < periodEnd
    }
}

/** Matches iOS `setLunchBreak`: a range that is not start < end is rejected and never persisted. */
fun AcademicCalendarPreferences.withLunchBreakTimes(startTime: String, endTime: String): AcademicCalendarPreferences? {
    if (!LunchBreakSettings.isValidRange(startTime, endTime)) return null
    return copy(lunchBreak = lunchBreak.copy(startTime = startTime, endTime = endTime)).normalized()
}
