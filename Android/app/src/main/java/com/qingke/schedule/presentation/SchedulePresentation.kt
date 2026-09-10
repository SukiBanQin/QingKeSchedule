package com.qingke.schedule.presentation

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseOccurrence
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.CourseStatus
import com.qingke.schedule.domain.OccurrenceKey
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.ScheduleRules
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.LunchBreakSettings
import java.text.Collator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

sealed interface AcademicDayResolution {
    data class TeachingDay(val sourceDayOfWeek: Int, val isMakeup: Boolean) : AcademicDayResolution
    data class NonTeachingDay(val reason: String) : AcademicDayResolution
}

object AcademicCalendarResolver {
    fun resolve(date: LocalDate, preferences: AcademicCalendarPreferences): AcademicDayResolution {
        val value = date.toString()
        if (value in preferences.nonTeachingDates) {
            return AcademicDayResolution.NonTeachingDay("已设为停课日")
        }
        preferences.makeupTeachingDays.firstOrNull { it.date == value }?.let { makeup ->
            return AcademicDayResolution.TeachingDay(makeup.followsDayOfWeek, isMakeup = true)
        }
        val dayOfWeek = date.dayOfWeek.value
        if (preferences.weekendsAreNonTeachingDays && dayOfWeek >= 6) {
            return AcademicDayResolution.NonTeachingDay("周末默认停课")
        }
        return AcademicDayResolution.TeachingDay(dayOfWeek, isMakeup = false)
    }
}

data class CourseTimingProgress(
    val elapsedMinutes: Int,
    val remainingMinutes: Int,
    val fraction: Double,
)

data class TodayCourseItem(
    val occurrence: CourseOccurrence,
    val status: CourseStatus,
    val isNext: Boolean,
    val timingProgress: CourseTimingProgress?,
)

data class TodaySchedulePresentation(
    val teachingWeek: Int?,
    val items: List<TodayCourseItem>,
    val emptyMessage: String,
    val isNonTeachingDay: Boolean,
) {
    companion object {
        fun create(
            semester: Semester,
            courses: List<Course>,
            now: LocalDateTime,
            academicCalendar: AcademicCalendarPreferences = AcademicCalendarPreferences.defaults,
        ): TodaySchedulePresentation {
            val week = ScheduleRules.teachingWeek(now.toLocalDate(), semester)
            val resolution = AcademicCalendarResolver.resolve(now.toLocalDate(), academicCalendar)
            val occurrences = when {
                resolution !is AcademicDayResolution.TeachingDay -> emptyList()
                !ScheduleRules.isTeachingWeekInSemester(week, semester) -> emptyList()
                else -> ScheduleRules.occurrencesForWeek(week, courses)
                    .filter { it.schedule.dayOfWeek == resolution.sourceDayOfWeek }
                    .sortedWith(occurrenceComparator)
            }
            val statuses = occurrences.map { ScheduleRules.occurrenceStatus(it, semester, now) }
            val nextIndex = statuses.indexOfFirst { it == CourseStatus.UPCOMING }
            val items = occurrences.mapIndexed { index, occurrence ->
                val status = statuses[index]
                TodayCourseItem(
                    occurrence = occurrence,
                    status = status,
                    isNext = index == nextIndex,
                    timingProgress = timingProgress(occurrence, status, semester, now),
                )
            }
            val inSemester = ScheduleRules.isTeachingWeekInSemester(week, semester)
            val message = when {
                resolution is AcademicDayResolution.NonTeachingDay && inSemester ->
                    "${resolution.reason}，今日不显示课程。"
                inSemester -> "今天没有课程，享受空闲时间吧。"
                else -> "当前日期不在这个学期内。"
            }
            return TodaySchedulePresentation(
                teachingWeek = week,
                items = items,
                emptyMessage = message,
                isNonTeachingDay = resolution is AcademicDayResolution.NonTeachingDay,
            )
        }
    }
}

data class WeekCourseItem(
    val occurrence: CourseOccurrence,
    val isConflicting: Boolean,
    val displayDayOfWeek: Int,
)

data class WeekDayPresentation(
    val dayOfWeek: Int,
    val date: LocalDate,
    val items: List<WeekCourseItem>,
    val isNonTeachingDay: Boolean,
    val scheduleSourceDayOfWeek: Int?,
)

data class WeekSchedulePresentation(
    val week: Int,
    val currentWeek: Int?,
    val days: List<WeekDayPresentation>,
) {
    companion object {
        fun create(
            requestedWeek: Int,
            semester: Semester,
            courses: List<Course>,
            now: LocalDateTime,
            academicCalendar: AcademicCalendarPreferences = AcademicCalendarPreferences.defaults,
        ): WeekSchedulePresentation {
            val resolvedWeek = requestedWeek.coerceIn(1, semester.totalWeeks)
            val calculatedCurrentWeek = ScheduleRules.teachingWeek(now.toLocalDate(), semester)
            val currentWeek = calculatedCurrentWeek.takeIf { ScheduleRules.isTeachingWeekInSemester(it, semester) }
            val occurrences = ScheduleRules.occurrencesForWeek(resolvedWeek, courses)
            val days = (1..7).map { displayDay ->
                val date = requireNotNull(ScheduleRules.dateForTeachingWeek(resolvedWeek, displayDay, semester))
                when (val resolution = AcademicCalendarResolver.resolve(date, academicCalendar)) {
                    is AcademicDayResolution.NonTeachingDay -> WeekDayPresentation(
                        dayOfWeek = displayDay,
                        date = date,
                        items = emptyList(),
                        isNonTeachingDay = true,
                        scheduleSourceDayOfWeek = null,
                    )
                    is AcademicDayResolution.TeachingDay -> {
                        val items = occurrences
                            .filter { it.schedule.dayOfWeek == resolution.sourceDayOfWeek }
                            .sortedWith(occurrenceComparator)
                        val conflicts = conflictingKeys(items)
                        WeekDayPresentation(
                            dayOfWeek = displayDay,
                            date = date,
                            items = items.map { WeekCourseItem(it, it.key in conflicts, displayDay) },
                            isNonTeachingDay = false,
                            scheduleSourceDayOfWeek = resolution.sourceDayOfWeek,
                        )
                    }
                }
            }
            return WeekSchedulePresentation(resolvedWeek, currentWeek, days)
        }

        fun initialWeek(semester: Semester, now: LocalDateTime): Int =
            ScheduleRules.teachingWeek(now.toLocalDate(), semester).coerceIn(1, semester.totalWeeks)
    }
}

data class WeekMatrixItem(
    val id: String,
    val occurrence: CourseOccurrence,
    val isConflicting: Boolean,
    val dayColumn: Int,
    val startRow: Int,
    val rowSpan: Int,
    val lane: Int,
    val laneCount: Int,
)

data class WeekMatrixBreak(
    val title: String,
    val startTime: String,
    val endTime: String,
    val insertionRow: Int,
)

data class WeekMatrixPresentation(
    val periods: List<Period>,
    val items: List<WeekMatrixItem>,
    val scheduleBreak: WeekMatrixBreak?,
) {
    companion object {
        fun create(
            semester: Semester,
            days: List<WeekDayPresentation>,
            academicCalendar: AcademicCalendarPreferences = AcademicCalendarPreferences.defaults,
        ): WeekMatrixPresentation {
            val periods = semester.periods.sortedBy { it.number }
            val periodRows = periods.mapIndexed { index, period -> period.number to index }.toMap()
            val items = days.filter { it.dayOfWeek in 1..7 }.flatMap { day ->
                layoutDay(day, periodRows)
            }.sortedWith(compareBy<WeekMatrixItem> { it.dayColumn }
                .thenBy { it.startRow }
                .thenBy { it.lane }
                .thenBy { it.occurrence.key.courseIndex }
                .thenBy { it.occurrence.key.scheduleIndex })
            return WeekMatrixPresentation(periods, items, makeBreak(academicCalendar.lunchBreak, periods))
        }

        private fun layoutDay(day: WeekDayPresentation, periodRows: Map<Int, Int>): List<WeekMatrixItem> {
            val drafts = day.items.mapNotNull { item ->
                val startRow = periodRows[item.occurrence.schedule.startPeriod] ?: return@mapNotNull null
                val endRow = periodRows[item.occurrence.schedule.endPeriod] ?: return@mapNotNull null
                if (endRow < startRow) return@mapNotNull null
                MatrixDraft(item, day.dayOfWeek - 1, startRow, endRow - startRow + 1)
            }.sortedWith(compareBy<MatrixDraft> { it.startRow }
                .thenBy { it.endRow }
                .thenBy { it.item.occurrence.key.courseIndex }
                .thenBy { it.item.occurrence.key.scheduleIndex })
            val result = mutableListOf<WeekMatrixItem>()
            val component = mutableListOf<MatrixDraft>()
            var componentEnd = -1
            drafts.forEach { draft ->
                if (component.isNotEmpty() && draft.startRow > componentEnd) {
                    result += layoutComponent(component)
                    component.clear()
                    componentEnd = -1
                }
                component += draft
                componentEnd = maxOf(componentEnd, draft.endRow)
            }
            result += layoutComponent(component)
            return result
        }

        private fun layoutComponent(component: List<MatrixDraft>): List<WeekMatrixItem> {
            if (component.isEmpty()) return emptyList()
            val laneEnds = mutableListOf<Int>()
            val placements = component.map { draft ->
                val lane = laneEnds.indexOfFirst { it < draft.startRow }.takeIf { it >= 0 } ?: laneEnds.size
                if (lane == laneEnds.size) laneEnds += draft.endRow else laneEnds[lane] = draft.endRow
                draft to lane
            }
            return placements.map { (draft, lane) ->
                WeekMatrixItem(
                    id = "${draft.dayColumn}:${draft.item.occurrence.key.courseIndex}:${draft.item.occurrence.key.scheduleIndex}",
                    occurrence = draft.item.occurrence,
                    isConflicting = draft.item.isConflicting,
                    dayColumn = draft.dayColumn,
                    startRow = draft.startRow,
                    rowSpan = draft.rowSpan,
                    lane = lane,
                    laneCount = laneEnds.size,
                )
            }
        }

        private fun makeBreak(settings: LunchBreakSettings, periods: List<Period>): WeekMatrixBreak? {
            if (!settings.isEnabled) return null
            val start = ScheduleRules.minutes(settings.startTime) ?: return null
            val end = ScheduleRules.minutes(settings.endTime) ?: return null
            if (start >= end) return null
            val insertion = periods.indexOfFirst { period ->
                (ScheduleRules.minutes(period.startTime) ?: Int.MIN_VALUE) >= end
            }
            if (insertion <= 0) return null
            val previousEnd = ScheduleRules.minutes(periods[insertion - 1].endTime) ?: return null
            if (previousEnd > start) return null
            return WeekMatrixBreak(settings.title, settings.startTime, settings.endTime, insertion)
        }
    }

    private data class MatrixDraft(
        val item: WeekCourseItem,
        val dayColumn: Int,
        val startRow: Int,
        val rowSpan: Int,
    ) {
        val endRow: Int get() = startRow + rowSpan - 1
    }
}

object ScheduleDisplayText {
    val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekdayName(dayOfWeek: Int): String = weekdayNames.getOrElse(dayOfWeek - 1) { "" }

    fun periodRange(schedule: CourseSchedule): String = if (schedule.startPeriod == schedule.endPeriod) {
        "第 ${schedule.startPeriod} 节"
    } else {
        "第 ${schedule.startPeriod}–${schedule.endPeriod} 节"
    }

    fun timeRange(schedule: CourseSchedule, semester: Semester): String {
        val start = semester.periods.firstOrNull { it.number == schedule.startPeriod }?.startTime ?: "--:--"
        val end = semester.periods.firstOrNull { it.number == schedule.endPeriod }?.endTime ?: "--:--"
        return "$start–$end"
    }

    fun weekMatrixSummary(periodCount: Int): String = "MON–SUN / $periodCount PERIODS"

    fun compactCourseDetails(course: Course, schedule: CourseSchedule): String =
        listOf(schedule.classroom, course.teacher).filter { it.isNotEmpty() }.joinToString(" · ")
}

private val occurrenceComparator = Comparator<CourseOccurrence> { left, right ->
    compareValuesBy(left, right, { it.schedule.startPeriod }, { it.schedule.endPeriod }).takeIf { it != 0 }
        ?: zhCollator.compare(left.course.name, right.course.name).takeIf { it != 0 }
        ?: compareValuesBy(left, right, { it.key.courseIndex }, { it.key.scheduleIndex })
}

private val zhCollator: Collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)

private fun timingProgress(
    occurrence: CourseOccurrence,
    status: CourseStatus,
    semester: Semester,
    now: LocalDateTime,
): CourseTimingProgress? {
    if (status != CourseStatus.ONGOING) return null
    val start = semester.periods.firstOrNull { it.number == occurrence.schedule.startPeriod }
        ?.let { ScheduleRules.minutes(it.startTime) } ?: return null
    val end = semester.periods.firstOrNull { it.number == occurrence.schedule.endPeriod }
        ?.let { ScheduleRules.minutes(it.endTime) } ?: return null
    val current = now.hour * 60 + now.minute
    val duration = maxOf(end - start, 1)
    val elapsed = (current - start).coerceIn(0, duration)
    return CourseTimingProgress(elapsed, maxOf(end - current, 0), elapsed.toDouble() / duration)
}

private fun conflictingKeys(occurrences: List<CourseOccurrence>): Set<OccurrenceKey> {
    val conflicts = mutableSetOf<OccurrenceKey>()
    occurrences.indices.forEach { leftIndex ->
        (leftIndex + 1 until occurrences.size).forEach { rightIndex ->
            val left = occurrences[leftIndex]
            val right = occurrences[rightIndex]
            if (left.course.id != right.course.id &&
                ScheduleRules.periodRangesOverlap(left.schedule, right.schedule)
            ) {
                conflicts += left.key
                conflicts += right.key
            }
        }
    }
    return conflicts
}
