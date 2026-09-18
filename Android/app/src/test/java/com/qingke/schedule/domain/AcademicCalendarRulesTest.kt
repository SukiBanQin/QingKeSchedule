package com.qingke.schedule.domain

import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicCalendarRulesTest {
    private fun date(value: String) = LocalDate.parse(value)

    @Test
    fun resolutionPriorityIsNonTeachingThenMakeupThenWeekendThenWeekday() {
        val calendar = AcademicCalendarPreferences(
            weekendsAreNonTeachingDays = true,
            nonTeachingDates = listOf("2026-09-05"),
            makeupTeachingDays = listOf(MakeupTeachingDay("2026-09-05", 1), MakeupTeachingDay("2026-09-12", 3)),
        )

        assertEquals(AcademicDayResolution.NonTeachingDay("已设为停课日"), AcademicCalendarResolver.resolve(date("2026-09-05"), calendar))
        assertEquals(AcademicDayResolution.TeachingDay(3, isMakeup = true), AcademicCalendarResolver.resolve(date("2026-09-12"), calendar))
        assertEquals(AcademicDayResolution.NonTeachingDay("周末默认停课"), AcademicCalendarResolver.resolve(date("2026-09-06"), calendar))
        assertEquals(AcademicDayResolution.TeachingDay(1, isMakeup = false), AcademicCalendarResolver.resolve(date("2026-09-07"), calendar))
    }

    @Test
    fun weekendSwitchOffKeepsSaturdayAndSundayTeaching() {
        val calendar = AcademicCalendarPreferences(weekendsAreNonTeachingDays = false)
        assertEquals(AcademicDayResolution.TeachingDay(6, isMakeup = false), AcademicCalendarResolver.resolve(date("2026-09-05"), calendar))
        assertEquals(AcademicDayResolution.TeachingDay(7, isMakeup = false), AcademicCalendarResolver.resolve(date("2026-09-06"), calendar))
        val enabled = calendar.withWeekendsAreNonTeachingDays(true)
        assertEquals(AcademicDayResolution.NonTeachingDay("周末默认停课"), AcademicCalendarResolver.resolve(date("2026-09-05"), enabled))
        assertEquals(AcademicDayResolution.NonTeachingDay("周末默认停课"), AcademicCalendarResolver.resolve(date("2026-09-06"), enabled))
        assertTrue(enabled.weekendsAreNonTeachingDays)
        assertFalse(enabled.withWeekendsAreNonTeachingDays(false).weekendsAreNonTeachingDays)
    }

    @Test
    fun nonTeachingDateIsDeduplicatedSortedAndRemovesTheSameDateMakeup() {
        val calendar = AcademicCalendarPreferences(
            nonTeachingDates = listOf("2026-10-02", "2026-10-01"),
            makeupTeachingDays = listOf(MakeupTeachingDay("2026-10-02", 3)),
        )

        val alreadyStopped = calendar.withNonTeachingDate(date("2026-10-01"))
        assertEquals(listOf("2026-10-01", "2026-10-02"), alreadyStopped.nonTeachingDates)
        assertEquals(emptyList<MakeupTeachingDay>(), alreadyStopped.makeupTeachingDays)

        val added = AcademicCalendarPreferences().withNonTeachingDate(date("2026-09-30"))
        assertEquals(listOf("2026-09-30"), added.nonTeachingDates)
        val ordered = added.withNonTeachingDate(date("2026-09-02")).withNonTeachingDate(date("2026-12-24"))
        assertEquals(listOf("2026-09-02", "2026-09-30", "2026-12-24"), ordered.nonTeachingDates)

        val withoutOne = ordered.withoutNonTeachingDate("2026-09-30")
        assertEquals(listOf("2026-09-02", "2026-12-24"), withoutOne.nonTeachingDates)
        assertEquals(ordered, ordered.withoutNonTeachingDate("2027-01-01"))
    }

    @Test
    fun makeupTeachingDayReplacesItsSourceWeekdayAndRemovesTheSameDateNonTeaching() {
        val calendar = AcademicCalendarPreferences(
            nonTeachingDates = listOf("2026-09-05"),
            makeupTeachingDays = listOf(MakeupTeachingDay("2026-09-05", 1)),
        )

        val replaced = calendar.withMakeupTeachingDay(date("2026-09-05"), 4)
        assertEquals(listOf(MakeupTeachingDay("2026-09-05", 4)), replaced.makeupTeachingDays)
        assertEquals(emptyList<String>(), replaced.nonTeachingDates)

        val ordered = replaced
            .withMakeupTeachingDay(date("2026-09-19"), 2)
            .withMakeupTeachingDay(date("2026-09-12"), 6)
        assertEquals(listOf("2026-09-05", "2026-09-12", "2026-09-19"), ordered.makeupTeachingDays.map { it.date })
        assertEquals(listOf(4, 6, 2), ordered.makeupTeachingDays.map { it.followsDayOfWeek })

        val added = AcademicCalendarPreferences(nonTeachingDates = listOf("2026-10-01"))
            .withMakeupTeachingDay(date("2026-10-01"), 5)
        assertEquals(listOf(MakeupTeachingDay("2026-10-01", 5)), added.makeupTeachingDays)
        assertEquals(emptyList<String>(), added.nonTeachingDates)

        assertEquals(ordered, ordered.withoutMakeupTeachingDay("2027-01-01"))
        assertEquals(listOf("2026-09-12", "2026-09-19"), ordered.withoutMakeupTeachingDay("2026-09-05").makeupTeachingDays.map { it.date })
    }

    @Test
    fun invalidDatesAndWeekdaysAreIgnoredWithoutTouchingStoredValues() {
        val stored = AcademicCalendarPreferences(
            nonTeachingDates = listOf("2026-10-01"),
            makeupTeachingDays = listOf(MakeupTeachingDay("2026-10-10", 2)),
        )
        val yearZero = LocalDate.of(0, 1, 1)

        assertEquals(stored, stored.withNonTeachingDate(yearZero))
        assertEquals(stored, stored.withMakeupTeachingDay(yearZero, 3))
        listOf("2026-13-01", "2026-02-30", "not-a-date", "2026-1-05", "0000-01-01", "").forEach { value ->
            assertEquals(stored, stored.withoutNonTeachingDate(value))
            assertEquals(stored, stored.withoutMakeupTeachingDay(value))
        }
        assertEquals(stored.withoutNonTeachingDate("2026-10-01"), stored.copy(nonTeachingDates = emptyList()))
        assertEquals(stored, stored.withMakeupTeachingDay(date("2026-10-17"), 0))
        assertEquals(stored, stored.withMakeupTeachingDay(date("2026-10-17"), 8))
        assertEquals(stored, stored.withMakeupTeachingDay(date("2026-10-17"), -1))
        assertEquals(stored, stored.normalized())
    }

    @Test
    fun lunchBreakDefaultsStaysEnabledAndRejectsARangeThatIsNotStartBeforeEnd() {
        assertEquals(LunchBreakSettings(isEnabled = true, title = "午休", startTime = "11:40", endTime = "14:00"), LunchBreakSettings.defaults)
        assertEquals(LunchBreakSettings.defaults, AcademicCalendarPreferences.defaults.lunchBreak)

        val calendar = AcademicCalendarPreferences()
        assertEquals("12:00", calendar.withLunchBreakTimes("12:00", "13:30")!!.lunchBreak.startTime)
        assertEquals("13:30", calendar.withLunchBreakTimes("12:00", "13:30")!!.lunchBreak.endTime)
        assertNull(calendar.withLunchBreakTimes("14:00", "11:40"))
        assertNull(calendar.withLunchBreakTimes("12:00", "12:00"))
        assertNull(calendar.withLunchBreakTimes("8:00", "13:00"))
        assertNull(calendar.withLunchBreakTimes("12:00", "24:00"))
        assertNull(calendar.withLunchBreakTimes("", "13:00"))

        val disabled = calendar.withLunchBreakEnabled(false)
        assertFalse(disabled.lunchBreak.isEnabled)
        assertEquals("11:40", disabled.lunchBreak.startTime)
        assertTrue(disabled.withLunchBreakEnabled(true).lunchBreak.isEnabled)

        val blankTitle = AcademicCalendarPreferences(lunchBreak = LunchBreakSettings(true, "   ", "12:00", "13:00")).normalized()
        assertEquals("午休", blankTitle.lunchBreak.title)
        assertEquals(LunchBreakSettings.defaults, AcademicCalendarPreferences(lunchBreak = LunchBreakSettings(true, "午休", "13:00", "12:00")).normalized().lunchBreak)
        assertEquals(false, AcademicCalendarPreferences(lunchBreak = LunchBreakSettings(false, "午休", "13:00", "12:00")).normalized().lunchBreak.isEnabled)
    }

    @Test
    fun storedDuplicatesNormalizeToTheLastSourceWeekdayInDateOrder() {
        val calendar = AcademicCalendarPreferences(
            nonTeachingDates = listOf("2026-10-02", "2026-10-02", "broken"),
            makeupTeachingDays = listOf(
                MakeupTeachingDay("2026-10-10", 1),
                MakeupTeachingDay("2026-10-10", 5),
                MakeupTeachingDay("2026-10-05", 0),
                MakeupTeachingDay("2026-10-01", 2),
            ),
        ).normalized()

        assertEquals(listOf("2026-10-02"), calendar.nonTeachingDates)
        assertEquals(listOf(MakeupTeachingDay("2026-10-01", 2), MakeupTeachingDay("2026-10-10", 5)), calendar.makeupTeachingDays)
    }

    @Test
    fun normalizedCalendarIsIndependentFromResolutionCallers() {
        val calendar = AcademicCalendarPreferences(weekendsAreNonTeachingDays = true).normalized()
        assertEquals(AcademicDayResolution.NonTeachingDay("周末默认停课"), AcademicCalendarResolver.resolve(date("2026-09-05"), calendar))
        assertEquals(AcademicDayResolution.TeachingDay(5, isMakeup = false), AcademicCalendarResolver.resolve(date("2026-09-04"), calendar))
    }
}
