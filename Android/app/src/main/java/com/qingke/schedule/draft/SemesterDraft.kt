package com.qingke.schedule.draft

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.PeriodIdentity
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.ScheduleValidator
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.domain.ValidationIssue
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class PeriodDraft(
    val id: String,
    var number: Int,
    var startTime: LocalTime,
    var endTime: LocalTime,
    /** Number this period carried in the persisted semester; null for periods added inside the draft. */
    var sourceNumber: Int? = null,
)

class SemesterDraft private constructor(
    var id: String,
    var name: String,
    var startDate: LocalDate,
    var totalWeeks: Int,
    val periods: MutableList<PeriodDraft>,
    private val idFactory: () -> String,
) {
    fun semester(): Semester = Semester(
        id, name.trim(), startDate.format(DATE), totalWeeks,
        periods.map { Period(it.number, it.startTime.format(TIME), it.endTime.format(TIME)) },
    )

    /**
     * Non-continuable input checks that only exist because already saved courses are compared against the
     * draft: shortening the semester below a saved course range cannot be cascaded, so it must be fixed in
     * the course editor first. Period deletions no longer block here; P3-06-R7 turns them into a
     * [com.qingke.schedule.domain.SemesterCascadePlan] instead.
     */
    fun courseRangeIssues(previous: Semester?, courses: List<Course>): List<ValidationIssue> {
        if (previous == null || courses.isEmpty()) return emptyList()
        return if (courses.any { course -> course.schedules.any { it.endWeek > totalWeeks } }) {
            listOf(
                ValidationIssue(
                    "semester.totalWeeks",
                    "缩短总周数会让已有课程超出学期范围，请先在课程编辑中调整相关课程的周次。",
                ),
            )
        } else {
            emptyList()
        }
    }

    /** Identity of every visible row for [com.qingke.schedule.domain.SemesterCascadePlanner]. */
    fun periodIdentities(): List<PeriodIdentity> = periods.map { PeriodIdentity(it.sourceNumber, it.number) }

    /**
     * Re-points the identity baseline at a semester that was just written. [persistedNumbers] must
     * be the numbers captured when the write was requested, keyed by draft period id, so that edits
     * made while the write was in flight are not reported as persisted. Periods missing from that
     * map were created after the request and keep no persisted identity. Only call this after a
     * successful write.
     */
    fun markPersisted(persistedNumbers: Map<String, Int>) {
        periods.forEach { period -> period.sourceNumber = persistedNumbers[period.id] }
    }

    fun validationIssues(): List<ValidationIssue> = ScheduleValidator.validate(
        ScheduleData(1, semester(), emptyList(), "1970-01-01T00:00:00Z"),
    )

    fun addPeriod() {
        val start = (periods.lastOrNull()?.endTime ?: LocalTime.of(7, 50)).plusMinutes(10)
        val number = (periods.maxOfOrNull { it.number } ?: 0) + 1
        periods += PeriodDraft(idFactory(), number, start, start.plusMinutes(45))
    }

    fun removePeriod(id: String) {
        if (periods.size > 1) {
            periods.removeAll { it.id == id }
            periods.forEachIndexed { index, period -> period.number = index + 1 }
        }
    }

    companion object {
        fun create(now: LocalDate, idFactory: () -> String = { UUID.randomUUID().toString() }): SemesterDraft {
            val season = if (now.monthValue < 7) "春季" else "秋季"
            return SemesterDraft(idFactory(), "${now.year} ${season}学期", now, 18,
                DEFAULT_TIMES.mapIndexed { index, pair -> PeriodDraft(idFactory(), index + 1, pair.first, pair.second) }.toMutableList(), idFactory)
        }

        fun edit(semester: Semester, idFactory: () -> String = { UUID.randomUUID().toString() }): SemesterDraft = SemesterDraft(
            semester.id, semester.name, LocalDate.parse(semester.startDate), semester.totalWeeks,
            semester.periods.map {
                PeriodDraft(
                    idFactory(), it.number, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime),
                    sourceNumber = it.number,
                )
            }.toMutableList(), idFactory,
        )

        private val DEFAULT_TIMES = listOf(
            "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45", "10:55" to "11:40",
            "14:00" to "14:45", "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40",
            "19:00" to "19:45", "19:55" to "20:40",
        ).map { LocalTime.parse(it.first) to LocalTime.parse(it.second) }
        private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    }
}
