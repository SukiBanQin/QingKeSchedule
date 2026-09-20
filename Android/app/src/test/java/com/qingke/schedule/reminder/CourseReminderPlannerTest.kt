package com.qingke.schedule.reminder

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A08: the pure reminder planner, aligned with the iOS notification planner rules. */
class CourseReminderPlannerTest {
    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val periods = listOf(
        Period(1, "08:00", "08:45"),
        Period(2, "09:00", "09:45"),
    )

    // 2026-03-02 is a Monday, so week 1 Monday is 2026-03-02 and week 1 Saturday is 2026-03-07.
    private fun semester(totalWeeks: Int = 4) = Semester("term", "测试学期", "2026-03-02", totalWeeks, periods)

    private fun schedule(
        id: String = "s1",
        dayOfWeek: Int = 1,
        startPeriod: Int = 1,
        endPeriod: Int = 1,
        startWeek: Int = 1,
        endWeek: Int = 4,
        repeatRule: RepeatRule = RepeatRule.EVERY,
        classroom: String = "",
    ) = CourseSchedule(id, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, repeatRule, classroom)

    private fun data(courses: List<Course>, semester: Semester? = semester()) =
        ScheduleData(1, semester, courses, "1970-01-01T00:00:00Z")

    private fun course(id: String = "c1", name: String = "高等数学", schedules: List<CourseSchedule> = listOf(schedule())) =
        Course(id, name, "王老师", "#287B74", schedules)

    private fun plan(
        data: ScheduleData,
        calendar: AcademicCalendarPreferences = AcademicCalendarPreferences.defaults,
        lead: Int = 10,
        now: Instant = Instant.parse("2026-03-01T00:00:00Z"),
        zone: ZoneId = shanghai,
        window: Duration = Duration.ofDays(60),
        limit: Int = CourseReminderPlanner.DEFAULT_LIMIT,
    ) = CourseReminderPlanner.plan(data, calendar, lead, now, zone, window, limit)

    @Test fun weeklySchedulePlansEveryWeekInsideItsRangeWithTitleAndBody() {
        val reminders = plan(data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 2, classroom = " A101 "))))) )

        assertEquals(2, reminders.size)
        assertEquals(Instant.parse("2026-03-01T23:50:00Z"), reminders[0].fireAt)
        assertEquals(Instant.parse("2026-03-08T23:50:00Z"), reminders[1].fireAt)
        assertEquals("高等数学", reminders[0].title)
        assertEquals("08:00–08:45 · A101", reminders[0].body)
        assertEquals(LocalDate.parse("2026-03-02"), reminders[0].identity.date)
        assertEquals(1, reminders[0].identity.week)
        assertEquals(false, reminders[0].identity.isMakeup)
    }

    @Test fun oddAndEvenRepeatRulesSelectTheMatchingWeeks() {
        val odd = course(id = "odd", name = "单周课", schedules = listOf(schedule(id = "odd", repeatRule = RepeatRule.ODD)))
        val even = course(id = "even", name = "双周课", schedules = listOf(schedule(id = "even", repeatRule = RepeatRule.EVEN)))

        val reminders = plan(data(listOf(odd, even)))

        val oddDates = reminders.filter { it.title == "单周课" }.map { it.identity.week }
        val evenDates = reminders.filter { it.title == "双周课" }.map { it.identity.week }
        assertEquals(listOf(1, 3), oddDates)
        assertEquals(listOf(2, 4), evenDates)
    }

    @Test fun firstAndLastWeekBoundsAreHonouredAndClampedByTheSemester() {
        val inside = course(id = "inside", schedules = listOf(schedule(id = "inside", startWeek = 2, endWeek = 3)))
        val beyond = course(id = "beyond", schedules = listOf(schedule(id = "beyond", startWeek = 3, endWeek = 9)))

        val reminders = plan(data(listOf(inside, beyond), semester(totalWeeks = 4)))

        assertEquals(listOf(2, 3), reminders.filter { it.title == "高等数学" && it.identity.courseIndex == 0 }.map { it.identity.week })
        assertEquals(listOf(3, 4), reminders.filter { it.identity.courseIndex == 1 }.map { it.identity.week })
    }

    @Test fun nonTeachingDateSuppressesThatOccurrenceOnly() {
        val hidden = LocalDate.parse("2026-03-02")
        val calendar = AcademicCalendarPreferences(nonTeachingDates = listOf(hidden.toString()))

        val reminders = plan(data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 2))))), calendar)

        assertEquals(listOf(2), reminders.map { it.identity.week })
        assertTrue(reminders.none { it.identity.date == hidden })
    }

    @Test fun weekendClosureSuppressesSaturdaySchedules() {
        val saturday = course(id = "sat", name = "周六课", schedules = listOf(schedule(id = "sat", dayOfWeek = 6, startWeek = 1, endWeek = 1)))

        val open = plan(data(listOf(saturday)))
        assertEquals(1, open.size)
        assertEquals(LocalDate.parse("2026-03-07"), open.single().identity.date)

        val closed = plan(
            data(listOf(saturday)),
            AcademicCalendarPreferences(weekendsAreNonTeachingDays = true),
        )
        assertTrue(closed.isEmpty())
    }

    @Test fun makeupDayMatchesItsSourceWeekdayAndItsOwnTeachingWeek() {
        // Saturday of week 3 follows a Wednesday: the Wednesday course gains that date.
        val makeup = MakeupTeachingDay(LocalDate.parse("2026-03-21").toString(), 3)
        val calendar = AcademicCalendarPreferences(makeupTeachingDays = listOf(makeup), weekendsAreNonTeachingDays = true)
        val wednesday = course(id = "wed", name = "周三课", schedules = listOf(schedule(id = "wed", dayOfWeek = 3, startWeek = 1, endWeek = 3)))
        val monday = course(id = "mon", name = "周一课", schedules = listOf(schedule(id = "mon", dayOfWeek = 1, startWeek = 1, endWeek = 3)))

        val reminders = plan(data(listOf(wednesday, monday)), calendar)

        val makeupReminder = reminders.single { it.identity.isMakeup }
        assertEquals(LocalDate.parse("2026-03-21"), makeupReminder.identity.date)
        assertEquals(3, makeupReminder.identity.week)
        assertEquals("周三课", makeupReminder.title)
        assertTrue(reminders.none { it.title == "周一课" && it.identity.date == LocalDate.parse("2026-03-21") })
    }

    @Test fun makeupDayFollowsTheWeekOfItsOwnDateForOddAndEvenRules() {
        val makeup = MakeupTeachingDay(LocalDate.parse("2026-03-21").toString(), 3)
        val calendar = AcademicCalendarPreferences(makeupTeachingDays = listOf(makeup))
        // 2026-03-21 belongs to week 3 (odd), so an even-week Wednesday schedule must not produce it.
        val evenWednesday = course(
            id = "wed",
            name = "双周周三课",
            schedules = listOf(schedule(id = "wed", dayOfWeek = 3, startWeek = 2, endWeek = 4, repeatRule = RepeatRule.EVEN)),
        )

        val reminders = plan(data(listOf(evenWednesday)), calendar)

        assertEquals(listOf(2, 4), reminders.filter { !it.identity.isMakeup }.map { it.identity.week })
        assertTrue(reminders.none { it.identity.isMakeup })
    }

    @Test fun zeroLeadFiresAtTheStartAndMaximumLeadCrossesToThePreviousDay() {
        val instant = Instant.parse("2026-03-01T00:00:00Z")
        val singleWeek = course(schedules = listOf(schedule(startWeek = 1, endWeek = 1)))
        val zero = plan(data(listOf(singleWeek)), lead = 0, now = instant).single()
        assertEquals(zero.startAt, zero.fireAt)

        val early = plan(data(listOf(singleWeek)), lead = 180, now = instant).single()
        assertEquals(Instant.parse("2026-03-01T21:00:00Z"), early.fireAt)

        // A 01:00 class with 180 minutes of lead fires at 22:00 on the previous local day.
        val nightSemester = Semester("term", "测试学期", "2026-03-02", 4, listOf(Period(1, "01:00", "01:45")))
        val night = plan(data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 1)))), nightSemester), lead = 180, now = instant).single()
        assertEquals(LocalDate.parse("2026-03-02"), night.identity.date)
        assertEquals(LocalDate.parse("2026-03-01"), night.fireAt.atZone(shanghai).toLocalDate())
    }

    @Test fun pastRemindersAndRemindersOutsideTheWindowAreSkipped() {
        val afterFirstWeek = plan(data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 2))))), now = Instant.parse("2026-03-02T01:00:00Z"))
        assertEquals(listOf(2), afterFirstWeek.map { it.identity.week })

        val shortWindow = plan(
            data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 4))))),
            window = Duration.ofDays(3),
        )
        assertEquals(listOf(1), shortWindow.map { it.identity.week })
    }

    @Test fun planIsCappedByTheLimit() {
        val reminders = plan(
            data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 4))))),
            limit = 2,
        )
        assertEquals(2, reminders.size)
        assertEquals(listOf(1, 2), reminders.map { it.identity.week })
    }

    @Test fun sameInstantCoursesAreOrderedDeterministically() {
        val first = course(id = "b", name = "B 课", schedules = listOf(schedule(id = "b", dayOfWeek = 1)))
        val second = course(id = "a", name = "A 课", schedules = listOf(schedule(id = "a", dayOfWeek = 1)))

        val planOne = plan(data(listOf(first, second)))
        val planTwo = plan(data(listOf(first, second)))

        assertEquals(planOne.map { it.identity.uri }, planTwo.map { it.identity.uri })
        assertEquals(planOne.map { it.fireAt }, planOne.map { it.fireAt }.sorted())
        assertTrue(planOne[0].identity.uri < planOne[1].identity.uri)
    }

    @Test fun duplicateCourseAndScheduleIdsStillProduceDistinctIdentities() {
        val duplicateOne = course(id = "same", name = "第一门", schedules = listOf(schedule(id = "shared", startWeek = 1, endWeek = 1)))
        val duplicateTwo = course(id = "same", name = "第二门", schedules = listOf(schedule(id = "shared", startWeek = 1, endWeek = 1)))

        val reminders = plan(data(listOf(duplicateOne, duplicateTwo)))

        assertEquals(2, reminders.size)
        assertNotEquals(reminders[0].identity.uri, reminders[1].identity.uri)
        assertEquals(setOf(0, 1), reminders.map { it.identity.courseIndex }.toSet())
        assertTrue(reminders.all { it.identity.uri.startsWith("qingke://reminder/") })
    }

    @Test fun identityUriEscapesSeparatorsSoDifferentIdsCannotCollide() {
        assertEquals("a%2Fb", CourseReminderIdentity.encode("a/b"))
        val slash = CourseReminderIdentity(0, 0, "a/b", "s", 1, LocalDate.parse("2026-03-02"), false)
        val escaped = CourseReminderIdentity(0, 0, "a%2Fb", "s", 1, LocalDate.parse("2026-03-02"), false)
        assertNotEquals(slash.uri, escaped.uri)
        assertTrue(CourseReminderIdentity.encode("中").all { it == '%' || it.isDigit() || it in 'A'..'F' })
    }

    @Test fun lunchBreakNeverAffectsThePlan() {
        val baseline = plan(data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 2))))))
        val enabled = plan(
            data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 2))))),
            AcademicCalendarPreferences(lunchBreak = LunchBreakSettings(isEnabled = true, startTime = "11:40", endTime = "14:00")),
        )
        val disabled = plan(
            data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 2))))),
            AcademicCalendarPreferences(lunchBreak = LunchBreakSettings(isEnabled = false, startTime = "12:00", endTime = "12:30")),
        )
        assertEquals(baseline.map { it.identity.uri }, enabled.map { it.identity.uri })
        assertEquals(enabled.map { it.identity.uri }, disabled.map { it.identity.uri })
    }

    @Test fun timeZoneChangesShiftTheFireInstants() {
        val target = data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 1)))))

        val shanghaiPlan = plan(target, zone = shanghai)
        val utcPlan = plan(target, zone = ZoneId.of("UTC"))

        assertEquals(Instant.parse("2026-03-01T23:50:00Z"), shanghaiPlan.single().fireAt)
        assertEquals(Instant.parse("2026-03-02T07:50:00Z"), utcPlan.single().fireAt)
        assertNotEquals(shanghaiPlan.single().fireAt, utcPlan.single().fireAt)
    }

    @Test fun daylightSavingTransitionKeepsTheWallClockStartTime() {
        val newYork = ZoneId.of("America/New_York")
        val dstSemester = Semester("term", "测试学期", "2026-03-02", 4, periods)
        val reminders = plan(
            data(listOf(course(schedules = listOf(schedule(startWeek = 1, endWeek = 2)))), dstSemester),
            now = Instant.parse("2026-03-01T00:00:00Z"),
            zone = newYork,
        )

        // Week 1 Monday 2026-03-02 is EST (-05:00); week 2 Monday 2026-03-09 is EDT (-04:00) after the DST switch.
        assertEquals(Instant.parse("2026-03-02T13:00:00Z"), reminders[0].startAt)
        assertEquals(Instant.parse("2026-03-09T12:00:00Z"), reminders[1].startAt)
        assertEquals(Duration.ofHours(167), Duration.between(reminders[0].startAt, reminders[1].startAt))
    }

    @Test fun missingSemesterOrCoursesProduceNoPlan() {
        assertTrue(plan(data(listOf(course()), semester = null)).isEmpty())
        assertTrue(plan(data(emptyList())).isEmpty())
        assertTrue(plan(data(listOf(course()), semester = Semester("term", "空学期", "2026-03-02", 4, emptyList()))).isEmpty())
    }

    @Test fun schedulesReferencingUnknownPeriodsAreSkipped() {
        val broken = course(schedules = listOf(schedule(startPeriod = 9, endPeriod = 9)))
        assertTrue(plan(data(listOf(broken))).isEmpty())
    }
}
