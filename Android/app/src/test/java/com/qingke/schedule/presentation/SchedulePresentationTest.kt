package com.qingke.schedule.presentation

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseOccurrence
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.OccurrenceKey
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import com.qingke.schedule.transfer.ScheduleDataDecoder
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePresentationTest {
    private val fixtureRoot = File(requireNotNull(System.getProperty("sharedFixturesDirectory")))

    @Test
    fun todayUsesSharedFixtureSortingStatusesNextAndProgress() {
        val data = fixture()
        val presentation = TodaySchedulePresentation.create(
            semester(), data.courses, LocalDateTime.parse("2026-08-31T09:41"),
        )

        assertEquals(1, presentation.teachingWeek)
        assertEquals(listOf("schedule-every", "schedule-odd", "schedule-alpha", "schedule-beta"), presentation.items.map { it.occurrence.schedule.id })
        assertEquals(listOf("FINISHED", "ONGOING", "UPCOMING", "UPCOMING"), presentation.items.map { it.status.name })
        assertEquals(listOf("schedule-alpha"), presentation.items.filter { it.isNext }.map { it.occurrence.schedule.id })
        assertEquals(CourseTimingProgress(46, 64, 46.0 / 110.0), presentation.items[1].timingProgress)
        assertNull(presentation.items.first().timingProgress)

        val before = TodaySchedulePresentation.create(semester(), data.courses, LocalDateTime.parse("2026-08-30T09:00"))
        val after = TodaySchedulePresentation.create(semester(), data.courses, LocalDateTime.parse("2027-01-04T09:00"))
        val noClass = TodaySchedulePresentation.create(semester(), emptyList(), LocalDateTime.parse("2026-09-01T09:00"))
        assertTrue(before.items.isEmpty())
        assertEquals("当前日期不在这个学期内。", before.emptyMessage)
        assertEquals("当前日期不在这个学期内。", after.emptyMessage)
        assertEquals("今天没有课程，享受空闲时间吧。", noClass.emptyMessage)
    }

    @Test
    fun academicCalendarPriorityIsSharedByTodayAndWeekAndMakeupUsesSourceDay() {
        val data = fixture()
        val calendar = AcademicCalendarPreferences(
            weekendsAreNonTeachingDays = true,
            nonTeachingDates = listOf("2026-08-31", "2026-09-05"),
            makeupTeachingDays = listOf(
                MakeupTeachingDay("2026-08-31", 2),
                MakeupTeachingDay("2026-09-05", 1),
            ),
        )
        assertEquals(AcademicDayResolution.NonTeachingDay("已设为停课日"), AcademicCalendarResolver.resolve(LocalDate.parse("2026-09-05"), calendar))
        assertEquals(AcademicDayResolution.NonTeachingDay("周末默认停课"), AcademicCalendarResolver.resolve(LocalDate.parse("2026-09-06"), calendar))
        assertEquals(AcademicDayResolution.TeachingDay(2, false), AcademicCalendarResolver.resolve(LocalDate.parse("2026-09-01"), calendar))

        val stoppedToday = TodaySchedulePresentation.create(semester(), data.courses, LocalDateTime.parse("2026-08-31T09:00"), calendar)
        val stoppedWeek = WeekSchedulePresentation.create(1, semester(), data.courses, LocalDateTime.parse("2026-08-31T09:00"), calendar)
        assertTrue(stoppedToday.isNonTeachingDay)
        assertTrue(stoppedToday.items.isEmpty())
        assertEquals("已设为停课日，今日不显示课程。", stoppedToday.emptyMessage)
        assertTrue(stoppedWeek.days.first().isNonTeachingDay)
        assertTrue(stoppedWeek.days.first().items.isEmpty())

        val makeupCalendar = calendar.copy(nonTeachingDates = listOf("2026-08-31"))
        val makeupWeek = WeekSchedulePresentation.create(1, semester(), data.courses, LocalDateTime.parse("2026-08-31T09:00"), makeupCalendar)
        val saturday = makeupWeek.days[5]
        assertEquals(LocalDate.parse("2026-09-05"), saturday.date)
        assertEquals(1, saturday.scheduleSourceDayOfWeek)
        assertFalse(saturday.isNonTeachingDay)
        assertTrue(saturday.items.any { it.occurrence.schedule.id == "schedule-every" })
        assertTrue(saturday.items.all { it.displayDayOfWeek == 6 })
    }

    @Test
    fun weekClampsDatesCurrentWeekOddEvenConflictsAndRepeatedIdsWithoutLoss() {
        val data = fixture()
        val odd = WeekSchedulePresentation.create(0, semester(), data.courses, LocalDateTime.parse("2026-09-07T09:00"))
        val even = WeekSchedulePresentation.create(2, semester(), data.courses, LocalDateTime.parse("2026-09-07T09:00"))
        val outside = WeekSchedulePresentation.create(99, semester(), data.courses, LocalDateTime.parse("2027-01-04T09:00"))

        assertEquals(1, odd.week)
        assertEquals(2, even.currentWeek)
        assertEquals(18, outside.week)
        assertNull(outside.currentWeek)
        assertEquals(1, WeekSchedulePresentation.initialWeek(semester(), LocalDateTime.parse("2026-08-01T09:00")))
        assertEquals(2, WeekSchedulePresentation.initialWeek(semester(), LocalDateTime.parse("2026-09-07T09:00")))
        assertEquals(18, WeekSchedulePresentation.initialWeek(semester(), LocalDateTime.parse("2027-02-01T09:00")))
        assertEquals(LocalDate.parse("2026-08-31"), odd.days.first().date)
        assertEquals(LocalDate.parse("2026-09-06"), odd.days.last().date)
        assertEquals(7, odd.days.last().dayOfWeek)
        assertTrue(odd.days.first().items.any { it.occurrence.course.id == "course-odd" })
        assertFalse(odd.days.first().items.any { it.occurrence.course.id == "course-even" })
        assertTrue(even.days.first().items.any { it.occurrence.course.id == "course-even" })
        assertFalse(even.days.first().items.any { it.occurrence.course.id == "course-odd" })
        assertEquals(listOf("schedule-wednesday"), even.days[2].items.map { it.occurrence.schedule.id })
        assertTrue(odd.days.first().items.first { it.occurrence.course.id == "course-every" }.isConflicting)
        assertTrue(odd.days.first().items.first { it.occurrence.course.id == "course-odd" }.isConflicting)

        val duplicatedSchedule = data.courses.first().schedules.first().copy(id = "duplicate")
        val duplicateCourse = data.courses.first().copy(id = "duplicate-course", schedules = listOf(duplicatedSchedule, duplicatedSchedule))
        val duplicateWeek = WeekSchedulePresentation.create(1, semester(), listOf(duplicateCourse), LocalDateTime.parse("2026-08-31T09:00"))
        assertEquals(2, duplicateWeek.days.first().items.size)
        assertEquals(listOf(OccurrenceKey(0, 0), OccurrenceKey(0, 1)), duplicateWeek.days.first().items.map { it.occurrence.key })
        assertTrue(duplicateWeek.days.first().items.none { it.isConflicting })
    }

    @Test
    fun matrixUsesClosedRowsStableLanesAndSafeInvalidPeriodMapping() {
        val matrixSemester = Semester("semester", "测试", "2026-09-01", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"),
            Period(4, "10:55", "11:40"), Period(5, "14:00", "14:45"), Period(6, "14:55", "15:40"),
        ))
        val first = occurrence("first", "a", 1, 2, 0)
        val second = occurrence("second", "b", 2, 3, 1)
        val third = occurrence("third", "c", 3, 4, 2)
        val separated = occurrence("separated", "d", 6, 6, 3)
        val invalid = occurrence("invalid", "x", 99, 99, 4)
        val monday = WeekDayPresentation(1, LocalDate.parse("2026-08-31"), listOf(
            WeekCourseItem(first, true, 1), WeekCourseItem(second, true, 1), WeekCourseItem(third, true, 1),
            WeekCourseItem(separated, false, 1), WeekCourseItem(invalid, false, 1),
        ), false, 1)
        val sunday = WeekDayPresentation(7, LocalDate.parse("2026-09-06"), listOf(WeekCourseItem(separated, false, 7)), false, 7)
        val matrix = WeekMatrixPresentation.create(matrixSemester, listOf(monday, sunday))

        assertEquals(5, matrix.items.size)
        assertEquals(2, matrix.items.first { it.occurrence.key == first.key }.rowSpan)
        assertEquals(2, matrix.items.first { it.occurrence.key == second.key }.laneCount)
        assertEquals(2, matrix.items.first { it.occurrence.key == third.key }.laneCount)
        assertEquals(0, matrix.items.first { it.occurrence.key == first.key }.lane)
        assertEquals(1, matrix.items.first { it.occurrence.key == second.key }.lane)
        assertEquals(0, matrix.items.first { it.occurrence.key == third.key }.lane)
        assertEquals(0, matrix.items.first { it.occurrence.key == separated.key && it.dayColumn == 0 }.lane)
        assertEquals(1, matrix.items.first { it.occurrence.key == separated.key && it.dayColumn == 0 }.laneCount)
        assertEquals(6, matrix.items.first { it.dayColumn == 6 }.dayColumn)
        assertTrue(matrix.items.none { it.occurrence.key == invalid.key })
        assertEquals(listOf(first.key, second.key, third.key, separated.key, separated.key), matrix.items.map { it.occurrence.key })
    }

    @Test
    fun matrixLunchBreakAndDisplayTextKeepPresentationOnlyRules() {
        val data = fixture()
        val week = WeekSchedulePresentation.create(1, semester(), data.courses, LocalDateTime.parse("2026-08-31T09:00"))
        val valid = AcademicCalendarPreferences(lunchBreak = LunchBreakSettings(true, "午休", "09:40", "10:00"))
        val matrix = WeekMatrixPresentation.create(semester(), week.days, valid)
        assertEquals(WeekMatrixBreak("午休", "09:40", "10:00", 2), matrix.scheduleBreak)
        listOf(
            LunchBreakSettings(false, "午休", "09:40", "10:00"),
            LunchBreakSettings(true, "午休", "09:30", "10:05"),
            LunchBreakSettings(true, "午休", "07:00", "07:50"),
            LunchBreakSettings(true, "午休", "12:00", "13:00"),
        ).forEach { settings ->
            assertNull(WeekMatrixPresentation.create(semester(), week.days, AcademicCalendarPreferences(lunchBreak = settings)).scheduleBreak)
        }

        val schedule = data.courses.first().schedules.first()
        assertEquals(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"), (1..7).map(ScheduleDisplayText::weekdayName))
        assertEquals("第 1–2 节", ScheduleDisplayText.periodRange(schedule))
        assertEquals("08:00–09:40", ScheduleDisplayText.timeRange(schedule, semester()))
        assertEquals("MON–SUN / 4 PERIODS", ScheduleDisplayText.weekMatrixSummary(4))
        assertEquals("A101 · 陈老师", ScheduleDisplayText.compactCourseDetails(data.courses.first(), schedule))
        assertEquals("", ScheduleDisplayText.compactCourseDetails(data.courses.first().copy(teacher = ""), schedule.copy(classroom = "")))
    }

    private fun fixture(): ScheduleData = ScheduleDataDecoder.decode(
        fixtureRoot.resolve("valid/complete-schedule.json").readBytes(),
    )

    private fun semester(): Semester = requireNotNull(fixture().semester)

    private fun occurrence(courseId: String, scheduleId: String, start: Int, end: Int, source: Int): CourseOccurrence {
        val schedule = CourseSchedule(scheduleId, 1, start, end, 1, 18, RepeatRule.EVERY, "")
        return CourseOccurrence(Course(courseId, courseId, "", "#287B74", listOf(schedule)), schedule, OccurrenceKey(source, 0))
    }
}
