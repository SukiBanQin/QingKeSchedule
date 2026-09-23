package com.qingke.schedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.qingke.schedule.domain.ScheduleRules
import com.qingke.schedule.preferences.LunchBreakSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

internal enum class CalendarExceptionMode(val label: String) { NON_TEACHING("停课日"), MAKEUP("调课上课") }

/**
 * Independent UI state for the A07 calendar section. It never shares state with the semester date
 * control, so the two inline calendars and the two time pickers cannot address each other's rows.
 */
internal class AcademicCalendarUiState(
    initialDate: LocalDate,
    lunchStart: LocalTime,
    lunchEnd: LocalTime,
    mode: CalendarExceptionMode = CalendarExceptionMode.NON_TEACHING,
    datePickerExpanded: Boolean = false,
    selectedDate: LocalDate = initialDate,
    shownYear: Int = initialDate.year,
    shownMonth: Int = initialDate.monthValue,
    followsDayOfWeek: Int = 1,
) {
    var mode: CalendarExceptionMode by mutableStateOf(mode)
    var datePickerExpanded: Boolean by mutableStateOf(datePickerExpanded)
    var selectedDate: LocalDate by mutableStateOf(selectedDate)
    var followsDayOfWeek: Int by mutableIntStateOf(followsDayOfWeek)
    var shownYear: Int by mutableIntStateOf(shownYear)
    var shownMonth: Int by mutableIntStateOf(shownMonth)
    var lunchStart: LocalTime by mutableStateOf(lunchStart)
    var lunchEnd: LocalTime by mutableStateOf(lunchEnd)
    val lunchPicker = CalendarTimePickerState()
    val lunchBreakRangeIsValid: Boolean get() = lunchStart < lunchEnd
    val shownMonthValue: YearMonth get() = YearMonth.of(shownYear, shownMonth)
}

private fun calendarUiStateSaver(start: LocalTime, end: LocalTime, initialDate: LocalDate): Saver<AcademicCalendarUiState, Any> = listSaver(
    save = { state ->
        listOf(
            state.mode.name, state.datePickerExpanded, state.selectedDate.toEpochDay(), state.followsDayOfWeek,
            state.shownYear, state.shownMonth, state.lunchStart.toSecondOfDay(), state.lunchEnd.toSecondOfDay(),
        )
    },
    restore = { values ->
        AcademicCalendarUiState(
            initialDate = initialDate,
            lunchStart = LocalTime.ofSecondOfDay((values[6] as Int).toLong()),
            lunchEnd = LocalTime.ofSecondOfDay((values[7] as Int).toLong()),
            mode = CalendarExceptionMode.entries.firstOrNull { it.name == values[0] } ?: CalendarExceptionMode.NON_TEACHING,
            datePickerExpanded = values[1] as Boolean,
            selectedDate = LocalDate.ofEpochDay(values[2] as Long),
            followsDayOfWeek = values[3] as Int,
            shownYear = values[4] as Int,
            shownMonth = values[5] as Int,
        )
    },
)

/**
 * Lunch times live in the section's own state like the iOS `@State` dates; a rejected range is shown
 * but never written, so the stored value keeps the last valid range.
 */
@Composable
internal fun rememberAcademicCalendarUiState(lunchBreak: LunchBreakSettings, initialDate: LocalDate): AcademicCalendarUiState {
    val start = ScheduleRules.parseLocalTime(lunchBreak.startTime) ?: LocalTime.of(11, 40)
    val end = ScheduleRules.parseLocalTime(lunchBreak.endTime) ?: LocalTime.of(14, 0)
    return rememberSaveable(saver = calendarUiStateSaver(start, end, initialDate)) {
        AcademicCalendarUiState(initialDate = initialDate, lunchStart = start, lunchEnd = end)
    }
}
