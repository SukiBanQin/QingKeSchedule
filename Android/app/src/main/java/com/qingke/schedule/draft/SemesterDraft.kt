package com.qingke.schedule.draft

import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.ScheduleValidator
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.domain.ValidationIssue
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class PeriodDraft(val id: String, var number: Int, var startTime: LocalTime, var endTime: LocalTime)

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
        periods.mapIndexed { index, period -> Period(index + 1, period.startTime.format(TIME), period.endTime.format(TIME)) },
    )

    fun validationIssues(): List<ValidationIssue> = ScheduleValidator.validate(
        ScheduleData(1, semester(), emptyList(), "1970-01-01T00:00:00Z"),
    )

    fun addPeriod() {
        val start = (periods.lastOrNull()?.endTime ?: LocalTime.of(7, 50)).plusMinutes(10)
        periods += PeriodDraft(idFactory(), periods.size + 1, start, start.plusMinutes(45))
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
            semester.periods.map { PeriodDraft(idFactory(), it.number, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime)) }.toMutableList(), idFactory,
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
