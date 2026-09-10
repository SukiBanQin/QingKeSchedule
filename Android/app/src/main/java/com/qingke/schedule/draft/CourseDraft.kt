package com.qingke.schedule.draft

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleConflict
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.ScheduleValidator
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.domain.ValidationIssue
import java.time.LocalDate
import java.util.UUID

data class CourseScheduleDraft(
    val id: String,
    var dayOfWeek: Int,
    var startPeriod: Int,
    var endPeriod: Int,
    var startWeek: Int,
    var endWeek: Int,
    var repeatRule: RepeatRule,
    var classroom: String,
) {
    fun toCourseSchedule(): CourseSchedule = CourseSchedule(
        id, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, repeatRule, classroom.trim(),
    )
}

sealed interface CourseSaveEvaluation {
    data class Invalid(val issues: List<ValidationIssue>) : CourseSaveEvaluation
    data class Conflicting(val conflicts: List<ScheduleConflict>) : CourseSaveEvaluation
    data object Ready : CourseSaveEvaluation
}

class CourseDraft private constructor(
    var id: String,
    var name: String,
    var teacher: String,
    var color: String,
    val schedules: MutableList<CourseScheduleDraft>,
    private val baseline: Course,
    private val idFactory: () -> String,
) {
    val isDirty: Boolean get() = course() != baseline

    fun course(): Course = Course(
        id = id,
        name = name.trim(),
        teacher = teacher.trim(),
        color = color,
        schedules = schedules.map { it.toCourseSchedule() },
    )

    fun validationIssues(semester: Semester): List<ValidationIssue> = ScheduleValidator.validate(
        ScheduleData(1, semester, listOf(course()), "1970-01-01T00:00:00Z"),
    )

    fun evaluateSave(semester: Semester, existingCourses: List<Course>): CourseSaveEvaluation {
        val issues = validationIssues(semester)
        if (issues.isNotEmpty()) return CourseSaveEvaluation.Invalid(issues)
        val candidate = course()
        if (hasNewDuplicateSchedule(candidate)) {
            return CourseSaveEvaluation.Invalid(
                listOf(ValidationIssue("courses.0.schedules", "该上课安排已存在，请勿重复添加")),
            )
        }
        val conflicts = com.qingke.schedule.domain.ScheduleRules.conflicts(candidate, existingCourses)
        return if (conflicts.isEmpty()) CourseSaveEvaluation.Ready else CourseSaveEvaluation.Conflicting(conflicts)
    }

    fun addSchedule(copying: CourseScheduleDraft? = null) {
        val source = copying ?: schedules.lastOrNull()
        schedules += CourseScheduleDraft(
            idFactory(), source?.dayOfWeek ?: 1, source?.startPeriod ?: 1, source?.endPeriod ?: 1,
            source?.startWeek ?: 1, source?.endWeek ?: 1, source?.repeatRule ?: RepeatRule.EVERY,
            source?.classroom ?: "",
        )
    }

    fun removeSchedule(id: String) {
        if (schedules.size > 1) {
            val index = schedules.indexOfFirst { it.id == id }
            if (index >= 0) schedules.removeAt(index)
        }
    }

    private fun hasNewDuplicateSchedule(candidate: Course): Boolean {
        val baselineCounts = baseline.schedules.groupingBy(::feature).eachCount()
        return candidate.schedules.groupingBy(::feature).eachCount().any { (key, count) ->
            count > 1 && count > (baselineCounts[key] ?: 0)
        }
    }

    private data class Feature(
        val day: Int, val startPeriod: Int, val endPeriod: Int, val startWeek: Int, val endWeek: Int,
        val repeat: RepeatRule, val classroom: String,
    )

    private fun feature(schedule: CourseSchedule) = Feature(
        schedule.dayOfWeek, schedule.startPeriod, schedule.endPeriod, schedule.startWeek, schedule.endWeek,
        schedule.repeatRule, schedule.classroom.trim(),
    )

    companion object {
        fun create(
            semester: Semester,
            now: LocalDate,
            idFactory: () -> String = { UUID.randomUUID().toString() },
        ): CourseDraft {
            val courseId = idFactory()
            val draftSchedule = defaultSchedule(semester, now, idFactory())
            val baseline = Course(courseId, "", "", "#287B74", listOf(draftSchedule.toCourseSchedule()))
            return CourseDraft(courseId, "", "", "#287B74", mutableListOf(draftSchedule), baseline, idFactory)
        }

        fun edit(
            course: Course,
            semester: Semester,
            now: LocalDate,
            appendSchedule: Boolean = false,
            idFactory: () -> String = { UUID.randomUUID().toString() },
        ): CourseDraft {
            val drafts = course.schedules.map { schedule ->
                CourseScheduleDraft(schedule.id, schedule.dayOfWeek, schedule.startPeriod, schedule.endPeriod,
                    schedule.startWeek, schedule.endWeek, schedule.repeatRule, schedule.classroom)
            }.toMutableList()
            if (appendSchedule) drafts += defaultSchedule(semester, now, idFactory())
            val normalizedBaseline = course.copy(
                name = course.name.trim(), teacher = course.teacher.trim(),
                schedules = course.schedules.map { it.copy(classroom = it.classroom.trim()) },
            )
            return CourseDraft(course.id, course.name, course.teacher, course.color, drafts, normalizedBaseline, idFactory)
        }

        private fun defaultSchedule(semester: Semester, now: LocalDate, id: String) = CourseScheduleDraft(
            id, now.dayOfWeek.value, semester.periods.firstOrNull()?.number ?: 1,
            semester.periods.firstOrNull()?.number ?: 1, 1, semester.totalWeeks, RepeatRule.EVERY, "",
        )
    }
}
