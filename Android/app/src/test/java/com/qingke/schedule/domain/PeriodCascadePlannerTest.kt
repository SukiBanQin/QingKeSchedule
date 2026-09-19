package com.qingke.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P3-06-R7: the period-identity map and the course cascade plan behind the settings save. */
class PeriodCascadePlannerTest {
    private val periods = listOf(
        Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"),
        Period(4, "10:55", "11:40"), Period(5, "14:00", "14:45"),
    )
    private val semester = Semester("term", "测试学期", "2026-09-01", 18, periods)

    @Test
    fun deletingAnUnreferencedPeriodKeepsEveryCourseUntouched() {
        val courses = listOf(course("one", schedule("s1", 3, 3)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout(5))

        assertFalse(plan.hasImpact)
        assertEquals(courses, plan.courses)
        assertTrue(plan.removedSchedules.isEmpty())
        assertTrue(plan.remappedSchedules.isEmpty())
        assertTrue(plan.summaryLines.isEmpty())
    }

    @Test
    fun directlyReferencedPeriodRemovesOnlyThatSchedule() {
        val courses = listOf(course("one", schedule("s1", 2, 2), schedule("s2", 4, 4)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout(2))

        assertTrue(plan.hasImpact)
        assertEquals(listOf("s2"), plan.courses.single().schedules.map { it.id })
        val removed = plan.removedSchedules.single()
        assertEquals("s1", removed.scheduleId)
        assertEquals(CascadeRemovalReason.DIRECT_REFERENCE, removed.reason)
        assertEquals(1, plan.coursesWithPartialRemoval.single().keptSchedules)
        assertTrue(plan.deletedCourses.isEmpty())
    }

    @Test
    fun rangeSpanningADeletedMiddlePeriodIsRemovedNotRenumbered() {
        val courses = listOf(course("one", schedule("s1", 2, 4)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout(3))

        assertEquals(CascadeRemovalReason.SPANNED_RANGE, plan.removedSchedules.single().reason)
        assertTrue(plan.remappedSchedules.isEmpty())
        assertTrue(plan.courses.isEmpty())
        assertEquals(listOf("one"), plan.deletedCourses.map { it.courseId })
    }

    @Test
    fun frontDeletionRenumbersSurvivingSchedulesByPeriodIdentity() {
        val courses = listOf(course("one", schedule("s1", 2, 3), schedule("s2", 4, 4)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout(1))

        assertTrue(plan.hasImpact)
        assertTrue(plan.removedSchedules.isEmpty())
        assertEquals(
            listOf("s1" to (1 to 2), "s2" to (3 to 3)),
            plan.courses.single().schedules.map { it.id to (it.startPeriod to it.endPeriod) },
        )
        assertEquals(listOf("s1", "s2"), plan.remappedSchedules.map { it.scheduleId })
        assertEquals(2, plan.remappedSchedules.first().previousStartPeriod)
        assertEquals(3, plan.remappedSchedules.first().previousEndPeriod)
        assertTrue(plan.summaryLines.single().contains("仅重新编号"))
    }

    @Test
    fun deletingTheLastPeriodOfAScheduleRangeRemovesOnlyThatRange() {
        val courses = listOf(course("one", schedule("s1", 4, 4), schedule("s2", 1, 2)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout(4))

        assertEquals(listOf("s2"), plan.courses.single().schedules.map { it.id })
        assertEquals(CascadeRemovalReason.DIRECT_REFERENCE, plan.removedSchedules.single().reason)
        assertEquals(1, plan.coursesWithPartialRemoval.size)
        assertTrue(plan.summaryLines.single().contains("保留 1 个安排"))
    }

    @Test
    fun aCourseLosingEveryScheduleIsDeletedWhileOthersSurvive() {
        val courses = listOf(course("one", schedule("s1", 1, 1)), course("two", schedule("s2", 5, 5)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout(1))

        assertEquals(listOf("two"), plan.courses.map { it.id })
        assertEquals(listOf("one"), plan.deletedCourses.map { it.courseId })
        assertEquals(1, plan.removedSchedules.size)
        assertTrue(plan.summaryLines.any { it.startsWith("整门删除：one") })
    }

    @Test
    fun oneSummaryCoversEveryCourseThatChanges() {
        val courses = listOf(
            course("deleted", schedule("a", 2, 2)),
            course("partial", schedule("b", 1, 1), schedule("c", 2, 2)),
            course("remapped", schedule("d", 3, 3)),
            course("untouched", schedule("e", 1, 1)),
        )
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout(2))

        assertEquals(listOf("partial", "remapped", "untouched"), plan.courses.map { it.id })
        assertEquals(listOf("deleted"), plan.deletedCourses.map { it.courseId })
        assertEquals(listOf("partial"), plan.coursesWithPartialRemoval.map { it.courseId })
        assertEquals(listOf("remapped"), plan.remappedSchedules.map { it.courseId })
        assertEquals(3, plan.summaryLines.size)
        assertTrue(plan.summaryLines[0].contains("整门删除：deleted"))
        assertTrue(plan.summaryLines[1].contains("partial"))
        assertTrue(plan.summaryLines[2].contains("remapped"))
        assertFalse(plan.summaryLines.any { it.contains("untouched") })
    }

    @Test
    fun firstBootAndUnchangedPeriodsHaveNoImpact() {
        val courses = listOf(course("one", schedule("s1", 1, 1)))
        val untouched = SemesterCascadePlanner.plan(semester, courses, identitiesWithout())
        val onboarding = SemesterCascadePlanner.plan(null, courses, identitiesWithout())

        assertFalse(onboarding.hasImpact)
        assertEquals(courses, onboarding.courses)
        assertFalse(untouched.hasImpact)
        assertEquals(courses, untouched.courses)
    }

    @Test
    fun newlyAddedPeriodsAndTimeOnlyEditsHaveNoImpact() {
        val courses = listOf(course("one", schedule("s1", 2, 2)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout() + PeriodIdentity(null, 6))

        assertFalse(plan.hasImpact)
        assertEquals(2, plan.courses.single().schedules.single().startPeriod)
    }

    @Test
    fun aScheduleReferencingAnUnknownNumberIsRemovedInsteadOfSilentlyKept() {
        val courses = listOf(course("one", schedule("s1", 9, 9)))
        val plan = SemesterCascadePlanner.plan(semester, courses, identitiesWithout())

        assertEquals(listOf("s1"), plan.removedSchedules.map { it.scheduleId })
        assertTrue(plan.courses.isEmpty())
    }

    /** Persisted periods minus the deleted numbers, renumbered exactly like the settings draft does. */
    private fun identitiesWithout(vararg deleted: Int): List<PeriodIdentity> {
        val removed = deleted.toSet()
        return periods.filterNot { it.number in removed }
            .mapIndexed { index, period -> PeriodIdentity(period.number, index + 1) }
    }

    private fun course(id: String, vararg schedules: CourseSchedule) =
        Course(id, id, "教师", "#287B74", schedules.toList())

    private fun schedule(id: String, start: Int, end: Int) =
        CourseSchedule(id, 3, start, end, 1, 18, RepeatRule.EVERY, "A101")
}
