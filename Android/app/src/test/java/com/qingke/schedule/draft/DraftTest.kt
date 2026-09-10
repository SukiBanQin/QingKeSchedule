package com.qingke.schedule.draft

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.Semester
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTest {
    private val semester = Semester("semester", "测试学期", "2026-09-01", 18, listOf(
        Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"),
    ))

    @Test
    fun newCourseUsesInjectedIdsDateDefaultsAndNormalizedDirtyCheck() {
        val ids = ids("course", "schedule", "copy")
        val draft = CourseDraft.create(semester, LocalDate.of(2026, 9, 3), ids)
        assertEquals("course", draft.id)
        assertEquals("#287B74", draft.color)
        assertEquals(1, draft.schedules.size)
        assertEquals(4, draft.schedules.single().dayOfWeek)
        assertEquals(1, draft.schedules.single().startPeriod)
        assertEquals(18, draft.schedules.single().endWeek)
        assertFalse(draft.isDirty)
        draft.name = "  课程  "
        draft.teacher = "  教师  "
        draft.schedules.single().classroom = "  A101  "
        assertEquals("课程", draft.course().name)
        assertEquals("教师", draft.course().teacher)
        assertEquals("A101", draft.course().schedules.single().classroom)
        assertTrue(draft.isDirty)
    }

    @Test
    fun editingAppendingCopyingAndRemovingSchedulesPreservesRequiredSemantics() {
        val existing = Course("course", "名称", "教师", "#287B74", listOf(schedule("old")))
        val unchanged = CourseDraft.edit(existing, semester, LocalDate.of(2026, 9, 1), idFactory = ids("ignored"))
        assertFalse(unchanged.isDirty)
        val draft = CourseDraft.edit(existing, semester, LocalDate.of(2026, 9, 4), true, ids("appended", "copied"))
        assertEquals("course", draft.id)
        assertEquals(listOf("old", "appended"), draft.schedules.map { it.id })
        assertEquals(5, draft.schedules.last().dayOfWeek)
        assertTrue(draft.isDirty)
        draft.addSchedule(draft.schedules.first())
        assertEquals("copied", draft.schedules.last().id)
        draft.removeSchedule("old")
        draft.removeSchedule("appended")
        draft.removeSchedule("copied")
        assertEquals(1, draft.schedules.size)
    }

    @Test
    fun duplicateEvaluationAllowsHistoricalDuplicatesButBlocksNewAndPrioritizesValidation() {
        val legacy = Course("legacy", "旧课", "", "#287B74", listOf(schedule("one"), schedule("two")))
        val editing = CourseDraft.edit(legacy, semester, LocalDate.of(2026, 9, 1), idFactory = ids("new"))
        assertTrue(editing.evaluateSave(semester, listOf(legacy)) is CourseSaveEvaluation.Ready)
        editing.addSchedule(editing.schedules.first())
        assertDuplicate(editing)

        val invalid = CourseDraft.create(semester, LocalDate.of(2026, 9, 1), ids("course", "schedule"))
        invalid.schedules.single().startPeriod = 99
        assertTrue(invalid.evaluateSave(semester, emptyList()) is CourseSaveEvaluation.Invalid)
    }

    @Test
    fun conflictsAndDifferentRepeatOrClassroomAreEvaluatedCorrectly() {
        val draft = CourseDraft.create(semester, LocalDate.of(2026, 8, 31), ids("candidate", "candidate-schedule", "copied"))
        draft.name = "候选"
        draft.schedules.single().startPeriod = 1
        draft.schedules.single().endPeriod = 2
        val conflict = Course("other", "已有", "", "#287B74", listOf(schedule("other", startPeriod = 2, repeat = RepeatRule.ODD)))
        val result = draft.evaluateSave(semester, listOf(conflict))
        assertTrue(result is CourseSaveEvaluation.Conflicting)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15, 17), (result as CourseSaveEvaluation.Conflicting).conflicts.single().weeks)
        draft.schedules.single().repeatRule = RepeatRule.EVEN
        assertTrue(draft.evaluateSave(semester, listOf(conflict)) is CourseSaveEvaluation.Ready)
        draft.schedules.single().repeatRule = RepeatRule.ODD
        draft.schedules.single().classroom = "B202"
        draft.addSchedule(draft.schedules.single())
        draft.schedules.last().classroom = "A101"
        assertTrue(draft.evaluateSave(semester, emptyList()) is CourseSaveEvaluation.Ready)
    }

    @Test
    fun semesterDraftDefaultsEditValidationAndPeriodOperationsUseDomainRules() {
        val spring = SemesterDraft.create(LocalDate.of(2026, 6, 30), ids("semester", *Array(10) { "p$it" }))
        assertEquals("2026 春季学期", spring.name)
        assertEquals(18, spring.totalWeeks)
        assertEquals(10, spring.periods.size)
        assertEquals("08:00", spring.semester().periods.first().startTime)
        assertEquals("20:40", spring.semester().periods.last().endTime)

        val autumn = SemesterDraft.create(LocalDate.of(2026, 7, 1), ids("semester", *Array(11) { "a$it" }))
        assertEquals("2026 秋季学期", autumn.name)
        autumn.removePeriod(autumn.periods[4].id)
        assertEquals((1..9).toList(), autumn.periods.map { it.number })
        autumn.addPeriod()
        assertEquals("20:50", autumn.periods.last().startTime.toString())
        assertEquals("21:35", autumn.periods.last().endTime.toString())
        autumn.name = "  已修改  "
        assertEquals("已修改", autumn.semester().name)
        autumn.name = " "
        assertTrue(autumn.validationIssues().isNotEmpty())

        val edited = SemesterDraft.edit(semester, ids("p1", "p2", "p3"))
        assertEquals(semester.id, edited.id)
        assertEquals(semester.periods.map { it.startTime }, edited.semester().periods.map { it.startTime })
        edited.periods.clear()
        edited.addPeriod()
        assertEquals(LocalTime.of(8, 0), edited.periods.single().startTime)
    }

    @Test
    fun semesterValidationDelegatesWeekCountPeriodCountAndTimeErrorsToExistingValidator() {
        val draft = SemesterDraft.create(LocalDate.of(2026, 9, 1), ids("semester", *Array(25) { "p$it" }))
        draft.name = " "
        draft.totalWeeks = 0
        draft.periods[1].startTime = LocalTime.of(7, 0)
        draft.periods[1].endTime = LocalTime.of(6, 0)
        assertTrue(draft.validationIssues().any { it.path == "semester.name" })
        assertTrue(draft.validationIssues().any { it.path == "semester.totalWeeks" })
        assertTrue(draft.validationIssues().any { it.path == "semester.periods.1" })
        repeat(11) { draft.addPeriod() }
        assertTrue(draft.validationIssues().any { it.path == "semester.periods" })
    }

    private fun assertDuplicate(draft: CourseDraft) {
        val result = draft.evaluateSave(semester, emptyList())
        assertTrue(result is CourseSaveEvaluation.Invalid)
        assertEquals("courses.0.schedules", (result as CourseSaveEvaluation.Invalid).issues.single().path)
    }

    private fun schedule(
        id: String, startPeriod: Int = 1, repeat: RepeatRule = RepeatRule.EVERY,
    ) = CourseSchedule(id, 1, startPeriod, startPeriod, 1, 18, repeat, "")

    private fun ids(vararg values: String): () -> String {
        val iterator = values.iterator()
        return { iterator.next() }
    }
}
