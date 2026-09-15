package com.qingke.schedule.ui

import java.time.LocalDate
import java.time.YearMonth

/** Monday-first, six-row calendar data used by the in-page semester date picker. */
internal object SemesterMonthGrid {
    const val columns = 7
    const val rows = 6

    fun dates(month: YearMonth): List<LocalDate?> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val days = (1..month.lengthOfMonth()).map(month::atDay)
        return List(leading) { null } + days + List((columns * rows - leading - days.size).coerceAtLeast(0)) { null }
    }
}
