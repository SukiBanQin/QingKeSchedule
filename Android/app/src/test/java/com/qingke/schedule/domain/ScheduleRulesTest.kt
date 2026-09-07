package com.qingke.schedule.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRulesTest {
    private val semester = Semester(
        id = "semester",
        name = "秋季学期",
        startDate = "2026-09-02", // Wednesday: its Monday begins teaching week 1.
        totalWeeks = 18,
        periods = listOf(Period(1, "08:00", "08:45")),
    )

    @Test
    fun teachingWeekUsesMondayContainingSemesterStart() {
        assertEquals(1, ScheduleRules.teachingWeek(LocalDate.parse("2026-08-31"), semester))
        assertEquals(1, ScheduleRules.teachingWeek(LocalDate.parse("2026-09-06"), semester))
        assertEquals(2, ScheduleRules.teachingWeek(LocalDate.parse("2026-09-07"), semester))
        assertEquals(0, ScheduleRules.teachingWeek(LocalDate.parse("2026-08-30"), semester))
        assertEquals(19, ScheduleRules.teachingWeek(LocalDate.parse("2027-01-04"), semester))
        assertTrue(ScheduleRules.isTeachingWeekInSemester(1, semester))
        assertTrue(ScheduleRules.isTeachingWeekInSemester(18, semester))
        assertFalse(ScheduleRules.isTeachingWeekInSemester(0, semester))
        assertFalse(ScheduleRules.isTeachingWeekInSemester(19, semester))
    }

    @Test
    fun oddEvenAndEveryRepeatRulesUseTeachingWeekNumbers() {
        val every = schedule(RepeatRule.EVERY)
        val odd = schedule(RepeatRule.ODD)
        val even = schedule(RepeatRule.EVEN)

        assertTrue(ScheduleRules.scheduleApplies(every, 1))
        assertTrue(ScheduleRules.scheduleApplies(every, 2))
        assertTrue(ScheduleRules.scheduleApplies(odd, 1))
        assertFalse(ScheduleRules.scheduleApplies(odd, 2))
        assertFalse(ScheduleRules.scheduleApplies(even, 1))
        assertTrue(ScheduleRules.scheduleApplies(even, 2))
        assertFalse(ScheduleRules.scheduleApplies(every.copy(startWeek = 2), 1))
        assertFalse(ScheduleRules.scheduleApplies(every.copy(endWeek = 2), 3))
    }

    @Test
    fun localDateRejectsYearZeroAndInvalidDatesWhileKeepingGregorianLeapYears() {
        assertEquals(null, ScheduleRules.parseLocalDate("0000-01-01"))
        assertEquals(null, ScheduleRules.parseLocalDate("2025-02-29"))
        assertEquals(null, ScheduleRules.parseLocalDate("2026-04-31"))
        assertEquals(LocalDate.parse("2024-02-29"), ScheduleRules.parseLocalDate("2024-02-29"))
        assertEquals(LocalDate.parse("2000-02-29"), ScheduleRules.parseLocalDate("2000-02-29"))
        assertEquals(null, ScheduleRules.parseLocalDate("1900-02-29"))
    }

    @Test
    fun teachingWeekRetainsBoundaryBehaviorAcrossLeapDay() {
        val leapSemester = semester.copy(startDate = "2024-02-28", totalWeeks = 2)
        assertEquals(1, ScheduleRules.teachingWeek(LocalDate.parse("2024-02-26"), leapSemester))
        assertEquals(1, ScheduleRules.teachingWeek(LocalDate.parse("2024-02-29"), leapSemester))
        assertEquals(2, ScheduleRules.teachingWeek(LocalDate.parse("2024-03-04"), leapSemester))
        assertTrue(ScheduleRules.isTeachingWeekInSemester(2, leapSemester))
        assertFalse(ScheduleRules.isTeachingWeekInSemester(3, leapSemester))
    }

    private fun schedule(repeatRule: RepeatRule) = CourseSchedule(
        id = "schedule",
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 1,
        startWeek = 1,
        endWeek = 18,
        repeatRule = repeatRule,
        classroom = "",
    )
}
