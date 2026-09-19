package com.qingke.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P3-06-R7: the period-identity map and the course cascade plan behind the settings save. */
class PeriodCascadePlannerTest {
    private val periods = listOf(
        Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"),
        Period(4, "10:55", "11:40"), Period(5, "14:00", "14:45"),
    )
    private val semester = Semester("term", "测试学期", "2026-09-01", 18, periods)

    /** Version 1 legally stores reversed or sparse numbers: the row order 9, 4, 20 is a valid semester. */
    private val reversedSemester = Semester("reversed", "反序学期", "2026-09-01", 18, listOf(
        Period(9, "08:00", "08:45"), Period(4, "08:55", "09:40"), Period(20, "10:00", "10:45"),
    ))

    @Test
    fun deletingAnUnreferencedPeriodKeepsEveryCourseUntouched() {
        val courses = listOf(course("one", schedule("s1", 3, 3)))
        val plan = planOf(semester, courses, identitiesWithout(5))

        assertFalse(plan.hasImpact)
        assertEquals(courses, plan.courses)
        assertTrue(plan.removedSchedules.isEmpty())
        assertTrue(plan.remappedSchedules.isEmpty())
        assertTrue(plan.summaryLines.isEmpty())
        assertWritable(plan, 4)
    }

    @Test
    fun directlyReferencedPeriodRemovesOnlyThatSchedule() {
        val courses = listOf(course("one", schedule("s1", 2, 2), schedule("s2", 4, 4)))
        val plan = planOf(semester, courses, identitiesWithout(2))

        assertTrue(plan.hasImpact)
        assertEquals(listOf("s2"), plan.courses.single().schedules.map { it.id })
        val removed = plan.removedSchedules.single()
        assertEquals("s1", removed.scheduleId)
        assertEquals(CascadeRemovalReason.DIRECT_REFERENCE, removed.reason)
        assertEquals(1, plan.coursesWithPartialRemoval.single().keptSchedules)
        assertTrue(plan.deletedCourses.isEmpty())
        assertWritable(plan, 4)
    }

    @Test
    fun rangeSpanningADeletedMiddlePeriodIsRemovedNotRenumbered() {
        val courses = listOf(course("one", schedule("s1", 2, 4)))
        val plan = planOf(semester, courses, identitiesWithout(3))

        assertEquals(CascadeRemovalReason.SPANNED_RANGE, plan.removedSchedules.single().reason)
        assertTrue(plan.remappedSchedules.isEmpty())
        assertTrue(plan.courses.isEmpty())
        assertEquals(listOf("one"), plan.deletedCourses.map { it.courseId })
        assertWritable(plan, 4)
    }

    @Test
    fun frontDeletionRenumbersSurvivingSchedulesByPeriodIdentity() {
        val courses = listOf(course("one", schedule("s1", 2, 3), schedule("s2", 4, 4)))
        val plan = planOf(semester, courses, identitiesWithout(1))

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
        assertWritable(plan, 4)
    }

    @Test
    fun deletingTheLastPeriodOfAScheduleRangeRemovesOnlyThatRange() {
        val courses = listOf(course("one", schedule("s1", 4, 4), schedule("s2", 1, 2)))
        val plan = planOf(semester, courses, identitiesWithout(4))

        assertEquals(listOf("s2"), plan.courses.single().schedules.map { it.id })
        assertEquals(CascadeRemovalReason.DIRECT_REFERENCE, plan.removedSchedules.single().reason)
        assertEquals(1, plan.coursesWithPartialRemoval.size)
        assertTrue(plan.summaryLines.single().contains("保留 1 个安排"))
        assertWritable(plan, 4)
    }

    @Test
    fun aCourseLosingEveryScheduleIsDeletedWhileOthersSurvive() {
        val courses = listOf(course("one", schedule("s1", 1, 1)), course("two", schedule("s2", 5, 5)))
        val plan = planOf(semester, courses, identitiesWithout(1))

        assertEquals(listOf("two"), plan.courses.map { it.id })
        assertEquals(listOf("one"), plan.deletedCourses.map { it.courseId })
        assertEquals(1, plan.removedSchedules.size)
        assertTrue(plan.summaryLines.any { it.startsWith("整门删除：one") })
        assertWritable(plan, 4)
    }

    @Test
    fun oneSummaryCoversEveryCourseThatChanges() {
        val courses = listOf(
            course("deleted", schedule("a", 2, 2)),
            course("partial", schedule("b", 1, 1), schedule("c", 2, 2)),
            course("remapped", schedule("d", 3, 3)),
            course("untouched", schedule("e", 1, 1)),
        )
        val plan = planOf(semester, courses, identitiesWithout(2))

        assertEquals(listOf("partial", "remapped", "untouched"), plan.courses.map { it.id })
        assertEquals(listOf("deleted"), plan.deletedCourses.map { it.courseId })
        assertEquals(listOf("partial"), plan.coursesWithPartialRemoval.map { it.courseId })
        assertEquals(listOf("remapped"), plan.remappedSchedules.map { it.courseId })
        assertEquals(3, plan.summaryLines.size)
        assertTrue(plan.summaryLines[0].contains("整门删除：deleted"))
        assertTrue(plan.summaryLines[1].contains("partial"))
        assertTrue(plan.summaryLines[2].contains("remapped"))
        assertFalse(plan.summaryLines.any { it.contains("untouched") })
        assertWritable(plan, 4)
    }

    @Test
    fun firstBootAndUnchangedPeriodsHaveNoImpact() {
        val courses = listOf(course("one", schedule("s1", 1, 1)))
        val untouched = planOf(semester, courses, identitiesWithout())
        val onboarding = planOf(null, courses, identitiesWithout())

        assertFalse(onboarding.hasImpact)
        assertEquals(courses, onboarding.courses)
        assertFalse(untouched.hasImpact)
        assertEquals(courses, untouched.courses)
        assertWritable(untouched, 5)
    }

    @Test
    fun newlyAddedPeriodsAndTimeOnlyEditsHaveNoImpact() {
        val courses = listOf(course("one", schedule("s1", 2, 2)))
        val plan = planOf(semester, courses, identitiesWithout() + PeriodIdentity(null, 6))

        assertFalse(plan.hasImpact)
        assertEquals(2, plan.courses.single().schedules.single().startPeriod)
        assertWritable(plan, 6)
    }

    @Test
    fun aScheduleReferencingAnUnknownNumberIsRemovedInsteadOfSilentlyKept() {
        val courses = listOf(course("one", schedule("s1", 9, 9)))
        val plan = planOf(semester, courses, identitiesWithout())

        assertEquals(listOf("s1"), plan.removedSchedules.map { it.scheduleId })
        assertTrue(plan.courses.isEmpty())
        assertWritable(plan, 5)
    }

    @Test
    fun reversedPersistedOrderRefusesASpanningArrangementInsteadOfWritingTwoToOne() {
        val courses = listOf(course("数学", schedule("span", 4, 9)))

        val evaluation = SemesterCascadePlanner.evaluate(reversedSemester, courses, reversedIdentitiesAfterDeleting(20))

        val blocked = evaluation as SemesterCascadeEvaluation.Blocked
        assertNull(evaluation as? SemesterCascadeEvaluation.Plan)
        assertEquals(listOf("span"), blocked.unmappable.map { it.scheduleId })
        assertEquals(2, blocked.unmappable.single().mappedStartPeriod)
        assertEquals(1, blocked.unmappable.single().mappedEndPeriod)
        assertTrue(blocked.message, blocked.message.contains("无法按新节次顺序安全重映射"))
        assertTrue(blocked.message, blocked.message.contains("数学"))
        assertTrue(blocked.message, blocked.message.contains("第4-9节"))
        assertTrue(blocked.message, blocked.message.contains("课程编辑"))
    }

    @Test
    fun reversedPersistedOrderStillRemapsArrangementsThatKeepAValidRange() {
        val courses = listOf(course("one", schedule("single", 4, 4), schedule("head", 9, 9)))

        val plan = planOf(reversedSemester, courses, reversedIdentitiesAfterDeleting(20))

        assertEquals(
            listOf("single" to (2 to 2), "head" to (1 to 1)),
            plan.courses.single().schedules.map { it.id to (it.startPeriod to it.endPeriod) },
        )
        assertEquals(listOf("single", "head"), plan.remappedSchedules.map { it.scheduleId })
        assertWritable(plan, 2)
    }

    @Test
    fun renamedReversedSemesterKeepsItsNumbersAndReportsNoImpact() {
        val courses = listOf(course("数学", schedule("span", 4, 9)))

        val plan = planOf(reversedSemester, courses, reversedDraftIdentities())

        assertFalse(plan.hasImpact)
        assertEquals(courses, plan.courses)
        assertEquals(listOf(4 to 9), plan.courses.single().schedules.map { it.startPeriod to it.endPeriod })
        assertWritable(plan, setOf(9, 4, 20))
    }

    @Test
    fun aRangeThatWouldSwallowAnUnrelatedPersistedPeriodIsRefused() {
        val sparseSemester = Semester("sparse", "稀疏学期", "2026-09-01", 18, listOf(
            Period(4, "08:00", "08:45"), Period(20, "08:55", "09:40"),
            Period(9, "10:00", "10:45"), Period(30, "10:55", "11:40"),
        ))
        val courses = listOf(course("数学", schedule("span", 4, 9)))
        val periods = listOf(PeriodIdentity(4, 1), PeriodIdentity(20, 2), PeriodIdentity(9, 3))

        val evaluation = SemesterCascadePlanner.evaluate(sparseSemester, courses, periods)

        val blocked = evaluation as SemesterCascadeEvaluation.Blocked
        assertEquals(listOf("span"), blocked.unmappable.map { it.scheduleId })
        assertTrue(blocked.message, blocked.message.contains("无法按新节次顺序安全重映射"))
    }

    /** Persisted periods minus the deleted numbers, renumbered exactly like the settings draft does. */
    private fun identitiesWithout(vararg deleted: Int): List<PeriodIdentity> {
        val removed = deleted.toSet()
        return periods.map { it.number }.filterNot { it in removed }
            .mapIndexed { index, source -> PeriodIdentity(source, index + 1) }
    }

    /** Without a removal the draft keeps every persisted number, including reversed ones. */
    private fun reversedDraftIdentities(): List<PeriodIdentity> =
        reversedSemester.periods.map { PeriodIdentity(it.number, it.number) }

    /** After the first removal the draft renumbers the remaining rows 1..n, like SemesterDraft.removePeriod. */
    private fun reversedIdentitiesAfterDeleting(vararg deleted: Int): List<PeriodIdentity> {
        val removed = deleted.toSet()
        return reversedSemester.periods.map { it.number }.filterNot { it in removed }
            .mapIndexed { index, source -> PeriodIdentity(source, index + 1) }
    }

    /** Unwraps the write path of the evaluation; a blocked change has no plan at all. */
    private fun planOf(previous: Semester?, courses: List<Course>, periods: List<PeriodIdentity>): SemesterCascadePlan =
        (SemesterCascadePlanner.evaluate(previous, courses, periods) as SemesterCascadeEvaluation.Plan).plan

    private fun assertWritable(plan: SemesterCascadePlan, periodCount: Int) = assertWritable(plan, (1..periodCount).toSet())

    /** Every produced schedule must reference visible periods and never be reversed. */
    private fun assertWritable(plan: SemesterCascadePlan, visibleNumbers: Set<Int>) {
        plan.courses.flatMap { it.schedules }.forEach { schedule ->
            assertTrue(
                "start ${schedule.startPeriod} must be one of ${visibleNumbers.sorted()}",
                schedule.startPeriod in visibleNumbers,
            )
            assertTrue(
                "end ${schedule.endPeriod} must be one of ${visibleNumbers.sorted()}",
                schedule.endPeriod in visibleNumbers,
            )
            assertTrue(
                "${schedule.startPeriod}-${schedule.endPeriod} must not be reversed",
                schedule.startPeriod <= schedule.endPeriod,
            )
        }
    }

    private fun course(name: String, vararg schedules: CourseSchedule) =
        Course(name, name, "教师", "#287B74", schedules.toList())

    private fun schedule(id: String, start: Int, end: Int) =
        CourseSchedule(id, 3, start, end, 1, 18, RepeatRule.EVERY, "A101")
}
