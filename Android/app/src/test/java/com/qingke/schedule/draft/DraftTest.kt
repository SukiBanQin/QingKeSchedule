package com.qingke.schedule.draft

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleRules
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
        assertEquals("", draft.name)
        assertEquals("", draft.teacher)
        assertEquals("#287B74", draft.color)
        assertEquals(1, draft.schedules.size)
        assertEquals("schedule", draft.schedules.single().id)
        assertEquals(4, draft.schedules.single().dayOfWeek)
        assertEquals(1, draft.schedules.single().startPeriod)
        assertEquals(1, draft.schedules.single().endPeriod)
        assertEquals(1, draft.schedules.single().startWeek)
        assertEquals(18, draft.schedules.single().endWeek)
        assertEquals(RepeatRule.EVERY, draft.schedules.single().repeatRule)
        assertEquals("", draft.schedules.single().classroom)
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
        val existing = Course("course", "名称", "教师", "#287B74", listOf(
            schedule("old", dayOfWeek = 2, startPeriod = 2, endPeriod = 2, classroom = "A101"),
            schedule("second", dayOfWeek = 3, startPeriod = 1, endPeriod = 2, repeat = RepeatRule.ODD, classroom = "B202"),
        ))
        val unchanged = CourseDraft.edit(existing, semester, LocalDate.of(2026, 9, 1), idFactory = ids("ignored"))
        assertFalse(unchanged.isDirty)
        assertEquals(existing, unchanged.course())
        assertEquals(listOf("old", "second"), unchanged.schedules.map { it.id })
        val draft = CourseDraft.edit(existing, semester, LocalDate.of(2026, 9, 4), true, ids("appended", "copied"))
        assertEquals("course", draft.id)
        assertEquals(listOf("old", "second", "appended"), draft.schedules.map { it.id })
        assertEquals(5, draft.schedules.last().dayOfWeek)
        assertEquals(1, draft.schedules.last().startPeriod)
        assertEquals(1, draft.schedules.last().endPeriod)
        assertEquals(1, draft.schedules.last().startWeek)
        assertEquals(18, draft.schedules.last().endWeek)
        assertEquals(RepeatRule.EVERY, draft.schedules.last().repeatRule)
        assertEquals("", draft.schedules.last().classroom)
        assertTrue(draft.isDirty)
        draft.addSchedule(draft.schedules.first())
        assertEquals("copied", draft.schedules.last().id)
        draft.removeSchedule("old")
        draft.removeSchedule("second")
        draft.removeSchedule("appended")
        draft.removeSchedule("copied")
        assertEquals(1, draft.schedules.size)
    }

    @Test
    fun removingDuplicateScheduleIdsRemovesOnlyFirstAndProtectsLast() {
        val course = Course("course", "名称", "教师", "#287B74", listOf(schedule("same"), schedule("same")))
        val draft = CourseDraft.edit(course, semester, LocalDate.of(2026, 9, 1), idFactory = ids("unused"))
        draft.removeSchedule("missing")
        assertEquals(2, draft.schedules.size)
        draft.removeSchedule("same")
        assertEquals(1, draft.schedules.size)
        assertEquals("same", draft.schedules.single().id)
        draft.removeSchedule("same")
        assertEquals(1, draft.schedules.size)
    }

    @Test
    fun editingWhitespaceOnlyKeepsNormalizedCourseAndDirtyFalse() {
        val course = Course("course", "名称", "教师", "#287B74", listOf(schedule("one").copy(classroom = "A101")))
        val draft = CourseDraft.edit(course, semester, LocalDate.of(2026, 9, 1), idFactory = ids("unused"))
        draft.name = "  名称  "
        draft.teacher = " 教师 "
        draft.schedules.single().classroom = "  A101  "
        assertEquals(course, draft.course())
        assertFalse(draft.isDirty)
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
    fun newAppendedAndEditedDuplicateSchedulesReportTheSpecifiedInvalidIssue() {
        val created = CourseDraft.create(semester, LocalDate.of(2026, 8, 31), ids("new-course", "new-one", "new-two"))
        created.name = "新课"
        created.schedules.single().classroom = "A101"
        created.addSchedule(created.schedules.single())
        created.schedules.last().classroom = " A101 "
        assertDuplicate(created)

        val existing = Course("existing", "已有", "", "#287B74", listOf(schedule("one")))
        val appended = CourseDraft.edit(existing, semester, LocalDate.of(2026, 9, 7), true, ids("appended"))
        assertDuplicate(appended)

        val edited = CourseDraft.edit(
            existing.copy(schedules = listOf(schedule("one", classroom = "A101"), schedule("two", dayOfWeek = 2, classroom = "B202"))),
            semester,
            LocalDate.of(2026, 9, 1),
            idFactory = ids("unused"),
        )
        edited.schedules[1].dayOfWeek = 1
        edited.schedules[1].classroom = " A101 "
        assertDuplicate(edited)
    }

    @Test
    fun validationPrecedesDuplicateAndCrossCourseConflict() {
        val draft = CourseDraft.create(semester, LocalDate.of(2026, 8, 31), ids("candidate", "one", "two"))
        draft.name = " "
        draft.addSchedule(draft.schedules.single())
        val conflicting = Course("other", "已有", "", "#287B74", listOf(schedule("other", startPeriod = 1)))
        val candidate = draft.course()

        assertEquals(candidate.schedules.first().copy(id = "same"), candidate.schedules.last().copy(id = "same"))
        assertTrue(ScheduleRules.conflicts(candidate, listOf(conflicting)).isNotEmpty())

        val result = draft.evaluateSave(semester, listOf(conflicting))

        assertTrue(result is CourseSaveEvaluation.Invalid)
        assertFalse(result is CourseSaveEvaluation.Conflicting)
        val issues = (result as CourseSaveEvaluation.Invalid).issues
        assertTrue(issues.any { it.path == "courses.0.name" && it.message == "请填写课程名称" })
        assertFalse(issues.any { it.message == "该上课安排已存在，请勿重复添加" })
    }

    @Test
    fun sameCourseSchedulesDifferingOnlyByRepeatRuleAreReady() {
        val draft = CourseDraft.create(semester, LocalDate.of(2026, 8, 31), ids("course", "every", "odd"))
        draft.name = "重复规则不同"
        draft.schedules.single().classroom = " A101 "
        draft.addSchedule(draft.schedules.single())
        draft.schedules.last().repeatRule = RepeatRule.ODD

        assertEquals(listOf("every", "odd"), draft.schedules.map { it.id })
        assertEquals(RepeatRule.EVERY, draft.schedules.first().repeatRule)
        assertEquals(RepeatRule.ODD, draft.schedules.last().repeatRule)
        assertTrue(draft.evaluateSave(semester, emptyList()) is CourseSaveEvaluation.Ready)
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
        assertEquals(LocalDate.of(2026, 6, 30), spring.startDate)
        assertEquals(18, spring.totalWeeks)
        assertEquals(defaultPeriods(), spring.semester().periods)

        val autumn = SemesterDraft.create(LocalDate.of(2026, 7, 1), ids("semester", *Array(11) { "a$it" }))
        assertEquals("2026 秋季学期", autumn.name)
        assertEquals(LocalDate.of(2026, 7, 1), autumn.startDate)
        assertEquals(18, autumn.totalWeeks)
        autumn.removePeriod(autumn.periods[4].id)
        assertEquals((1..9).toList(), autumn.periods.map { it.number })
        autumn.addPeriod()
        assertEquals("20:50", autumn.periods.last().startTime.toString())
        assertEquals("21:35", autumn.periods.last().endTime.toString())
        autumn.name = "  已修改  "
        assertEquals("已修改", autumn.semester().name)
        autumn.name = " "
        assertTrue(autumn.validationIssues().isNotEmpty())

        val existingSemester = semester.copy(startDate = "2026-02-23", totalWeeks = 16, periods = listOf(
            Period(4, "08:30", "09:15"), Period(9, "09:25", "10:10"),
        ))
        val edited = SemesterDraft.edit(existingSemester, ids("p1", "p2", "p3"))
        assertEquals(semester.id, edited.id)
        assertEquals(LocalDate.of(2026, 2, 23), edited.startDate)
        assertEquals(16, edited.totalWeeks)
        assertEquals(listOf(Period(1, "08:30", "09:15"), Period(2, "09:25", "10:10")), edited.semester().periods)
        edited.periods.clear()
        edited.addPeriod()
        assertEquals(LocalTime.of(8, 0), edited.periods.single().startTime)
    }

    @Test
    fun semesterDraftKeepsOnePeriodAndReportsTimeOverlapOrInvalidOrdering() {
        val draft = SemesterDraft.create(LocalDate.of(2026, 9, 1), ids("semester", *Array(12) { "p$it" }))
        draft.periods.drop(1).map { it.id }.forEach(draft::removePeriod)
        draft.removePeriod(draft.periods.single().id)
        assertEquals(1, draft.periods.size)

        val invalid = SemesterDraft.create(LocalDate.of(2026, 9, 1), ids("invalid", *Array(10) { "i$it" }))
        invalid.periods[1].startTime = LocalTime.of(7, 0)
        invalid.periods[1].endTime = LocalTime.of(6, 0)
        assertTrue(invalid.validationIssues().any { it.path == "semester.periods.1" && it.message == "节次时间无效" })
        invalid.periods[1].startTime = LocalTime.of(8, 30)
        invalid.periods[1].endTime = LocalTime.of(9, 15)
        assertTrue(invalid.validationIssues().any { it.path == "semester.periods.1" && it.message == "相邻节次的时间不能重叠" })

        val crossDay = SemesterDraft.create(LocalDate.of(2026, 9, 1), ids("cross", *Array(11) { "c$it" }))
        crossDay.periods.last().endTime = LocalTime.of(23, 55)
        crossDay.addPeriod()
        assertEquals(LocalTime.of(0, 5), crossDay.periods.last().startTime)
        assertTrue(crossDay.validationIssues().any { it.path == "semester.periods.10" && it.message == "相邻节次的时间不能重叠" })
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
        assertEquals("该上课安排已存在，请勿重复添加", result.issues.single().message)
    }

    private fun defaultPeriods() = listOf(
        Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"),
        Period(3, "10:00", "10:45"), Period(4, "10:55", "11:40"),
        Period(5, "14:00", "14:45"), Period(6, "14:55", "15:40"),
        Period(7, "16:00", "16:45"), Period(8, "16:55", "17:40"),
        Period(9, "19:00", "19:45"), Period(10, "19:55", "20:40"),
    )

    private fun schedule(
        id: String,
        dayOfWeek: Int = 1,
        startPeriod: Int = 1,
        endPeriod: Int = startPeriod,
        startWeek: Int = 1,
        endWeek: Int = 18,
        repeat: RepeatRule = RepeatRule.EVERY,
        classroom: String = "",
    ) = CourseSchedule(id, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, repeat, classroom)

    private fun ids(vararg values: String): () -> String {
        val iterator = values.iterator()
        return { iterator.next() }
    }
}
