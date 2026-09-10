package com.qingke.schedule.domain

import java.time.LocalDate
import java.time.LocalDateTime
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

    @Test
    fun conflictsUseClosedPeriodsWeeksRepeatRulesAndStableInputOrder() {
        val every = schedule(RepeatRule.EVERY).copy(id = "candidate", startPeriod = 2, endPeriod = 3)
        val odd = schedule(RepeatRule.ODD).copy(id = "odd", startPeriod = 3, endPeriod = 4, startWeek = 1, endWeek = 5)
        val even = schedule(RepeatRule.EVEN).copy(id = "even", startPeriod = 3, endPeriod = 4, startWeek = 1, endWeek = 5)
        assertTrue(ScheduleRules.periodRangesOverlap(every, odd))
        assertEquals(listOf(1, 3, 5), ScheduleRules.overlappingWeeks(every, odd))
        assertTrue(ScheduleRules.schedulesConflict(every, odd))
        assertFalse(ScheduleRules.schedulesConflict(odd, even))

        val candidate = Course("candidate-course", "候选", "", "#287B74", listOf(every, every.copy(id = "candidate-2")))
        val first = Course("first", "第一门", "", "#287B74", listOf(odd))
        val second = Course("second", "第二门", "", "#287B74", listOf(odd.copy(id = "second")))
        assertEquals(
            listOf("first", "second", "first", "second"),
            ScheduleRules.conflicts(candidate, listOf(first, second)).map { it.existingCourse.id },
        )
        assertTrue(ScheduleRules.conflicts(candidate, listOf(candidate)).isEmpty())
    }

    @Test
    fun conflictMatrixKeepsReferencesWeeksAndInputOrder() {
        val candidateSchedule = schedule(RepeatRule.EVERY).copy(
            id = "candidate-schedule", dayOfWeek = 1, startPeriod = 2, endPeriod = 3, startWeek = 2, endWeek = 6,
        )
        val differentDay = candidateSchedule.copy(id = "different-day", dayOfWeek = 2)
        val touchingEndpoint = candidateSchedule.copy(id = "touching", startPeriod = 3, endPeriod = 4)
        val separatePeriods = candidateSchedule.copy(id = "separate-periods", startPeriod = 4, endPeriod = 5)
        val separateWeeks = candidateSchedule.copy(id = "separate-weeks", startWeek = 7, endWeek = 8)
        val odd = candidateSchedule.copy(id = "odd", repeatRule = RepeatRule.ODD, startWeek = 1, endWeek = 6)
        val even = candidateSchedule.copy(id = "even", repeatRule = RepeatRule.EVEN, startWeek = 1, endWeek = 6)

        assertFalse(ScheduleRules.schedulesConflict(candidateSchedule, differentDay))
        assertTrue(ScheduleRules.schedulesConflict(candidateSchedule, touchingEndpoint))
        assertFalse(ScheduleRules.schedulesConflict(candidateSchedule, separatePeriods))
        assertFalse(ScheduleRules.schedulesConflict(candidateSchedule, separateWeeks))
        assertEquals(listOf(3, 5), ScheduleRules.overlappingWeeks(candidateSchedule, odd))
        assertEquals(listOf(2, 4, 6), ScheduleRules.overlappingWeeks(candidateSchedule, even))
        assertFalse(ScheduleRules.schedulesConflict(odd, even))

        val candidate = Course("candidate", "候选", "", "#287B74", listOf(candidateSchedule, candidateSchedule.copy(id = "candidate-second")))
        val first = Course("first", "第一门", "", "#287B74", listOf(odd, even))
        val second = Course("second", "第二门", "", "#287B74", listOf(touchingEndpoint))
        val conflicts = ScheduleRules.conflicts(candidate, listOf(first, second, candidate))

        assertEquals(
            listOf("odd", "even", "touching", "odd", "even", "touching"),
            conflicts.map { it.existingSchedule.id },
        )
        assertEquals(listOf("first", "first", "second", "first", "first", "second"), conflicts.map { it.existingCourse.id })
        assertEquals(candidate, conflicts.first().candidateCourse)
        assertEquals(candidateSchedule, conflicts.first().candidateSchedule)
        assertEquals(first, conflicts.first().existingCourse)
        assertEquals(odd, conflicts.first().existingSchedule)
        assertEquals(listOf(3, 5), conflicts.first().weeks)
        assertEquals(listOf(2, 4, 6), conflicts[1].weeks)
    }

    @Test
    fun presentationRulesKeepDatesOccurrencePositionsMinutesAndMinuteBoundaries() {
        val occurrenceSchedule = schedule(RepeatRule.EVERY).copy(id = "same", startPeriod = 1, endPeriod = 1)
        val duplicated = Course("same-course", "重复", "", "#287B74", listOf(occurrenceSchedule, occurrenceSchedule))
        val presentationSemester = semester.copy(periods = listOf(Period(1, "08:00", "09:40")))

        assertEquals(LocalDate.parse("2026-08-31"), ScheduleRules.dateForTeachingWeek(1, 1, presentationSemester))
        assertEquals(LocalDate.parse("2026-09-06"), ScheduleRules.dateForTeachingWeek(1, 7, presentationSemester))
        assertEquals(null, ScheduleRules.dateForTeachingWeek(1, 8, presentationSemester))
        assertEquals(480, ScheduleRules.minutes("08:00"))
        assertEquals(null, ScheduleRules.minutes("8:00"))

        val occurrences = ScheduleRules.occurrencesForWeek(1, listOf(duplicated))
        assertEquals(listOf(OccurrenceKey(0, 0), OccurrenceKey(0, 1)), occurrences.map { it.key })
        assertEquals(CourseStatus.UPCOMING, ScheduleRules.occurrenceStatus(occurrences.first(), presentationSemester, LocalDateTime.parse("2026-08-31T07:59")))
        assertEquals(CourseStatus.ONGOING, ScheduleRules.occurrenceStatus(occurrences.first(), presentationSemester, LocalDateTime.parse("2026-08-31T08:00")))
        assertEquals(CourseStatus.ONGOING, ScheduleRules.occurrenceStatus(occurrences.first(), presentationSemester, LocalDateTime.parse("2026-08-31T09:40")))
        assertEquals(CourseStatus.FINISHED, ScheduleRules.occurrenceStatus(occurrences.first(), presentationSemester, LocalDateTime.parse("2026-08-31T09:41")))
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
