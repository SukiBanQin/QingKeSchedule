package com.qingke.schedule.domain

/**
 * P3-06-R7: the identity a visible draft period row carries. [sourceNumber] is the number this period had
 * in the persisted semester, so a course reference can still be followed after earlier periods were
 * deleted and every visible number shifted. Rows that were never persisted carry null.
 */
data class PeriodIdentity(val sourceNumber: Int?, val number: Int)

enum class CascadeRemovalReason { DIRECT_REFERENCE, SPANNED_RANGE }

data class RemovedSchedule(
    val courseIndex: Int,
    val courseId: String,
    val courseName: String,
    val scheduleId: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val reason: CascadeRemovalReason,
) {
    val description: String get() = describeSchedule(dayOfWeek, startPeriod, endPeriod, startWeek, endWeek)
}

data class RemappedSchedule(
    val courseIndex: Int,
    val courseId: String,
    val courseName: String,
    val scheduleId: String,
    val dayOfWeek: Int,
    val startWeek: Int,
    val endWeek: Int,
    val previousStartPeriod: Int,
    val previousEndPeriod: Int,
    val startPeriod: Int,
    val endPeriod: Int,
) {
    val description: String
        get() = weekdayName(dayOfWeek) + " " + periodRange(previousStartPeriod, previousEndPeriod) +
            " → " + periodRange(startPeriod, endPeriod)
}

/**
 * P3-06-R7-R1: an existing schedule whose persisted range has no faithful representation in the new
 * contiguous numbering. Version 1 legally allows reversed or sparse period numbers, for example the
 * persisted order 9, 4, 20 where a schedule of 4-9 would map onto 2-1. Such a plan must never be written:
 * [startPeriod] is the persisted start and [mappedStartPeriod] the number it would have taken.
 */
data class UnmappableSchedule(
    val courseIndex: Int,
    val courseId: String,
    val courseName: String,
    val scheduleId: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val mappedStartPeriod: Int,
    val mappedEndPeriod: Int,
) {
    val displayName: String get() = courseName.ifBlank { "未命名课程" }

    val description: String get() = describeSchedule(dayOfWeek, startPeriod, endPeriod, startWeek, endWeek)
}

/** One course of the plan: [removedSchedules] all failed, [keptSchedules] survives with new numbers. */
data class CourseCascade(
    val courseIndex: Int,
    val courseId: String,
    val courseName: String,
    val keptSchedules: Int,
    val removedSchedules: List<RemovedSchedule>,
) {
    val displayName: String get() = courseName.ifBlank { "未命名课程" }
}

/**
 * P3-06-R7: the result of applying a period deletion to the courses that reference the persisted periods.
 * [courses] is the exact list to write inside the same transaction as the semester, so a partially or
 * fully invalidated course can never survive next to the new period table.
 */
data class SemesterCascadePlan(
    val courses: List<Course>,
    val coursesWithPartialRemoval: List<CourseCascade>,
    val deletedCourses: List<CourseCascade>,
    val remappedSchedules: List<RemappedSchedule>,
    val removedSchedules: List<RemovedSchedule>,
) {
    /** True when deleting or reordering periods changes what already saved courses refer to. */
    val hasImpact: Boolean get() = removedSchedules.isNotEmpty() || remappedSchedules.isNotEmpty()

    /** One red summary line per affected course group, never one line per clicked save. */
    val summaryLines: List<String>
        get() = buildList {
            if (deletedCourses.isNotEmpty()) {
                add(
                    "整门删除：" +
                        summarize(deletedCourses.map { it.displayName + "（" + it.removedSchedules.size + " 个安排全部失效）" }) +
                        "，课程一并移除",
                )
            }
            coursesWithPartialRemoval.forEach { course ->
                add(
                    course.displayName + "：删除 " + course.removedSchedules.size + " 个安排（" +
                        summarize(course.removedSchedules.map { it.description }) + "），保留 " + course.keptSchedules + " 个安排",
                )
            }
            remappedSchedules.groupBy { it.courseIndex }.values.forEach { entries ->
                add(
                    entries.first().courseName.ifBlank { "未命名课程" } + "：上课安排仅重新编号（" +
                        summarize(entries.map { it.description }) + "）",
                )
            }
        }

    private companion object {
        private const val MAX_LISTED = 4

        private fun summarize(values: List<String>): String =
            if (values.size <= MAX_LISTED) values.joinToString("、")
            else values.take(MAX_LISTED).joinToString("、") + " 等 " + values.size + " 项"
    }
}

/**
 * P3-06-R7-R1: the planner either produces a writable plan or refuses the whole change. A blocked
 * evaluation carries no plan at all, so a partially remapped semester can never reach the repository.
 */
sealed interface SemesterCascadeEvaluation {
    /** The change cannot be represented safely; the caller must report [message] and write nothing. */
    data class Blocked(val message: String, val unmappable: List<UnmappableSchedule>) : SemesterCascadeEvaluation

    data class Plan(val plan: SemesterCascadePlan) : SemesterCascadeEvaluation
}

/**
 * P3-06-R7: maps every persisted period a course can reference onto the visible draft configuration.
 *
 * A schedule is dropped when it directly references a deleted period or when its range spans one, and a
 * surviving schedule is re-pointed through [PeriodIdentity.sourceNumber]. A number that merely still
 * exists after a deletion must never be reused for a different period, and a range whose mapping would
 * reorder or silently include unrelated persisted periods is refused instead of being written.
 */
object SemesterCascadePlanner {
    fun evaluate(previous: Semester?, courses: List<Course>, periods: List<PeriodIdentity>): SemesterCascadeEvaluation {
        if (previous == null) {
            return SemesterCascadeEvaluation.Plan(
                SemesterCascadePlan(courses, emptyList(), emptyList(), emptyList(), emptyList()),
            )
        }
        val numberBySource = periods.mapNotNull { period -> period.sourceNumber?.let { it to period.number } }.toMap()
        val sourceByNumber = periods.mapNotNull { period -> period.sourceNumber?.let { period.number to it } }.toMap()
        val deletedNumbers = previous.periods.map { it.number }.filterNot { it in numberBySource }.sorted()

        val nextCourses = mutableListOf<Course>()
        val partialCourses = mutableListOf<CourseCascade>()
        val deletedCourses = mutableListOf<CourseCascade>()
        val remappedSchedules = mutableListOf<RemappedSchedule>()
        val removedSchedules = mutableListOf<RemovedSchedule>()
        val unmappable = mutableListOf<UnmappableSchedule>()

        courses.forEachIndexed { courseIndex, course ->
            if (course.schedules.isEmpty()) {
                nextCourses += course
                return@forEachIndexed
            }
            val kept = mutableListOf<CourseSchedule>()
            val removed = mutableListOf<RemovedSchedule>()
            val remapped = mutableListOf<RemappedSchedule>()
            course.schedules.forEach { schedule ->
                val mappedStart = numberBySource[schedule.startPeriod]
                val mappedEnd = numberBySource[schedule.endPeriod]
                val spansDeleted = deletedNumbers.filter { it in schedule.startPeriod..schedule.endPeriod }
                when {
                    spansDeleted.isNotEmpty() -> removed += RemovedSchedule(
                        courseIndex, course.id, course.name, schedule.id, schedule.dayOfWeek, schedule.startPeriod, schedule.endPeriod,
                        schedule.startWeek, schedule.endWeek,
                        if (spansDeleted.any { it == schedule.startPeriod || it == schedule.endPeriod })
                            CascadeRemovalReason.DIRECT_REFERENCE else CascadeRemovalReason.SPANNED_RANGE,
                    )
                    mappedStart == null || mappedEnd == null -> removed += RemovedSchedule(
                        courseIndex, course.id, course.name, schedule.id, schedule.dayOfWeek, schedule.startPeriod, schedule.endPeriod,
                        schedule.startWeek, schedule.endWeek, CascadeRemovalReason.DIRECT_REFERENCE,
                    )
                    !isRepresentable(schedule, numberBySource, sourceByNumber) -> unmappable += UnmappableSchedule(
                        courseIndex, course.id, course.name, schedule.id, schedule.dayOfWeek, schedule.startPeriod, schedule.endPeriod,
                        schedule.startWeek, schedule.endWeek, mappedStart, mappedEnd,
                    )
                    mappedStart == schedule.startPeriod && mappedEnd == schedule.endPeriod -> kept += schedule
                    else -> {
                        kept += schedule.copy(startPeriod = mappedStart, endPeriod = mappedEnd)
                        remapped += RemappedSchedule(
                            courseIndex, course.id, course.name, schedule.id, schedule.dayOfWeek, schedule.startWeek, schedule.endWeek,
                            schedule.startPeriod, schedule.endPeriod, mappedStart, mappedEnd,
                        )
                    }
                }
            }
            remappedSchedules += remapped
            removedSchedules += removed
            if (kept.isEmpty()) {
                deletedCourses += CourseCascade(courseIndex, course.id, course.name, 0, removed)
            } else {
                if (removed.isNotEmpty()) partialCourses += CourseCascade(courseIndex, course.id, course.name, kept.size, removed)
                nextCourses += course.copy(schedules = kept)
            }
        }
        if (unmappable.isNotEmpty()) return SemesterCascadeEvaluation.Blocked(unmappableMessage(unmappable), unmappable)
        return SemesterCascadeEvaluation.Plan(
            SemesterCascadePlan(nextCourses, partialCourses, deletedCourses, remappedSchedules, removedSchedules),
        )
    }

    /**
     * A range survives only when the persisted periods the arrangement covered are exactly the persisted
     * periods the new range covers. Both sides are collected by inspecting the periods that actually exist
     * (at most 20), never by walking the number span: version 1 accepts sparse or reversed numbers, and a
     * legal semester may carry numbers close to Int.MAX_VALUE. Number gaps are holes rather than periods and
     * never block on their own, while a reversed mapping or a range that would swallow an unrelated period
     * still fails.
     */
    private fun isRepresentable(
        schedule: CourseSchedule,
        numberBySource: Map<Int, Int>,
        sourceByNumber: Map<Int, Int>,
    ): Boolean {
        val mappedStart = numberBySource[schedule.startPeriod] ?: return false
        val mappedEnd = numberBySource[schedule.endPeriod] ?: return false
        if (mappedStart > mappedEnd) return false
        val coveredSources = numberBySource.keys.filterTo(mutableSetOf()) { it in schedule.startPeriod..schedule.endPeriod }
        val sourcesBehindNewRange = sourceByNumber.filterKeys { it in mappedStart..mappedEnd }.values.toMutableSet()
        return coveredSources == sourcesBehindNewRange
    }

    private fun unmappableMessage(unmappable: List<UnmappableSchedule>): String = if (unmappable.size == 1) {
        val only = unmappable.single()
        "「" + only.displayName + "」" + only.description + " 的安排无法按新节次顺序安全重映射，请先在课程编辑中调整该课程。"
    } else {
        "有 " + unmappable.size + " 个上课安排无法按新节次顺序安全重映射（如「" + unmappable.first().displayName + "」" +
            unmappable.first().description + "），请先在课程编辑中调整相关课程。"
    }
}

private fun describeSchedule(dayOfWeek: Int, startPeriod: Int, endPeriod: Int, startWeek: Int, endWeek: Int): String =
    weekdayName(dayOfWeek) + " " + periodRange(startPeriod, endPeriod) + " " + weekRange(startWeek, endWeek)

private fun periodRange(start: Int, end: Int): String = if (start == end) "第${start}节" else "第${start}-${end}节"

private fun weekRange(start: Int, end: Int): String = if (start == end) "${start}周" else "${start}-${end}周"

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun weekdayName(dayOfWeek: Int): String = WEEKDAYS.getOrElse(dayOfWeek - 1) { "周${dayOfWeek}" }
