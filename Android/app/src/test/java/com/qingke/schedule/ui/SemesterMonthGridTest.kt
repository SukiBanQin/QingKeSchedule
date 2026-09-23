package com.qingke.schedule.ui

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterMonthGridTest {
    @Test fun `month grid is Monday first and always six weeks`() {
        val dates = SemesterMonthGrid.dates(YearMonth.of(2026, 3))
        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2026, 3, 1), dates[6])
        assertEquals(LocalDate.of(2026, 3, 31), dates[36])
        assertNull(dates[41])
    }

    @Test fun `leap February and year boundary retain all valid dates`() {
        val leap = SemesterMonthGrid.dates(YearMonth.of(2024, 2))
        assertTrue(LocalDate.of(2024, 2, 29) in leap)
        val january = SemesterMonthGrid.dates(YearMonth.of(2027, 1))
        assertTrue(LocalDate.of(2027, 1, 1) in january)
        assertEquals(42, january.size)
    }
}
