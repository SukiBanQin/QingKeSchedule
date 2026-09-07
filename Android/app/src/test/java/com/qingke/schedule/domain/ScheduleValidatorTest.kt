package com.qingke.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleValidatorTest {
    @Test
    fun nullSemesterAllowsOnlyAnEmptyCourseList() {
        assertTrue(ScheduleValidator.validate(data(semester = null, courses = emptyList())).isEmpty())
        assertHasPath(ScheduleValidator.validate(data(semester = null, courses = listOf(course()))), "courses")
    }

    @Test
    fun validatesVersionDateAndColor() {
        assertHasPath(ScheduleValidator.validate(data(schemaVersion = 2)), "schemaVersion")
        assertHasPath(ScheduleValidator.validate(data(updatedAt = "2026-09-02T12:00:00+08:00")), "updatedAt")
        assertHasPath(ScheduleValidator.validate(data(courses = listOf(course(color = "287B74")))), "courses.0.color")
    }

    @Test
    fun validatesSemesterAndPeriodConstraints() {
        assertHasPath(ScheduleValidator.validate(data(semester = semester(startDate = "2026-02-30"))), "semester.startDate")
        assertHasPath(ScheduleValidator.validate(data(semester = semester(totalWeeks = 53))), "semester.totalWeeks")
        assertHasPath(
            ScheduleValidator.validate(data(semester = semester(periods = listOf(
                Period(1, "08:00", "08:45"), Period(2, "08:30", "09:10"),
            )))),
            "semester.periods.1",
        )
    }

    @Test
    fun validatesCourseSchedulesAgainstSemester() {
        assertHasPath(
            ScheduleValidator.validate(data(courses = listOf(course(schedule = schedule(dayOfWeek = 8))))),
            "courses.0.schedules.0.dayOfWeek",
        )
        assertHasPath(
            ScheduleValidator.validate(data(courses = listOf(course(schedule = schedule(startPeriod = 2))))),
            "courses.0.schedules.0.periods",
        )
        assertHasPath(
            ScheduleValidator.validate(data(courses = listOf(course(schedule = schedule(endWeek = 19))))),
            "courses.0.schedules.0.weeks",
        )
    }

    private fun assertHasPath(issues: List<ValidationIssue>, path: String) {
        assertTrue("Expected validation issue at $path but found $issues", issues.any { it.path == path })
    }

    private fun data(
        schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
        semester: Semester? = semester(),
        courses: List<Course> = emptyList(),
        updatedAt: String = "2026-09-02T12:00:00.000Z",
    ) = ScheduleData(schemaVersion, semester, courses, updatedAt)

    private fun semester(
        startDate: String = "2026-09-02",
        totalWeeks: Int = 18,
        periods: List<Period> = listOf(Period(1, "08:00", "08:45")),
    ) = Semester("semester", "秋季学期", startDate, totalWeeks, periods)

    private fun course(
        color: String = "#287B74",
        schedule: CourseSchedule = schedule(),
    ) = Course("course", "课程", "", color, listOf(schedule))

    private fun schedule(
        dayOfWeek: Int = 1,
        startPeriod: Int = 1,
        endPeriod: Int = 1,
        endWeek: Int = 18,
    ) = CourseSchedule("schedule", dayOfWeek, startPeriod, endPeriod, 1, endWeek, RepeatRule.EVERY, "")
}
