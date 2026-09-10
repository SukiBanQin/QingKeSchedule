package com.qingke.schedule.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AppearanceMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

data class ReminderPreferences(
    val remindersEnabled: Boolean = false,
    val reminderLeadMinutes: Int = DEFAULT_LEAD_MINUTES,
    val usesCustomLeadTime: Boolean = false,
) {
    fun normalized(): ReminderPreferences {
        if (reminderLeadMinutes !in VALID_LEAD_MINUTES) {
            return copy(
                reminderLeadMinutes = DEFAULT_LEAD_MINUTES,
                usesCustomLeadTime = false,
            )
        }
        return copy()
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 10
        val PRESET_LEAD_MINUTES = setOf(0, 5, 10, 15, 30)
        val VALID_LEAD_MINUTES = 0..180
        val defaults = ReminderPreferences()
    }
}

@Serializable
data class MakeupTeachingDay(
    val date: String,
    val followsDayOfWeek: Int,
)

data class LunchBreakSettings(
    val isEnabled: Boolean = true,
    val title: String = DEFAULT_TITLE,
    val startTime: String = DEFAULT_START_TIME,
    val endTime: String = DEFAULT_END_TIME,
) {
    fun normalized(): LunchBreakSettings {
        val startMinutes = minutes(startTime)
        val endMinutes = minutes(endTime)
        if (startMinutes == null || endMinutes == null || startMinutes >= endMinutes) {
            return defaults.copy(isEnabled = isEnabled)
        }
        return copy(title = title.trim().ifEmpty { DEFAULT_TITLE })
    }

    companion object {
        const val DEFAULT_TITLE = "午休"
        const val DEFAULT_START_TIME = "11:40"
        const val DEFAULT_END_TIME = "14:00"
        val defaults = LunchBreakSettings()

        private fun minutes(value: String): Int? {
            if (!TIME.matches(value)) return null
            return value.substring(0, 2).toInt() * 60 + value.substring(3, 5).toInt()
        }

        private val TIME = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
    }
}

data class AcademicCalendarPreferences(
    val weekendsAreNonTeachingDays: Boolean = false,
    val nonTeachingDates: List<String> = emptyList(),
    val makeupTeachingDays: List<MakeupTeachingDay> = emptyList(),
    val lunchBreak: LunchBreakSettings = LunchBreakSettings.defaults,
) {
    fun normalized(): AcademicCalendarPreferences {
        val nonTeaching = nonTeachingDates.filter(::isValidDate).toSortedSet()
        val makeupByDate = linkedMapOf<String, MakeupTeachingDay>()
        makeupTeachingDays.forEach { day ->
            if (isValidDate(day.date) && day.followsDayOfWeek in 1..7 && day.date !in nonTeaching) {
                makeupByDate[day.date] = day
            }
        }
        return copy(
            nonTeachingDates = nonTeaching.toList(),
            makeupTeachingDays = makeupByDate.values.sortedBy { it.date },
            lunchBreak = lunchBreak.normalized(),
        )
    }

    companion object {
        val defaults = AcademicCalendarPreferences()

        private fun isValidDate(value: String): Boolean =
            DATE.matches(value) && !value.startsWith("0000-") && runCatching { LocalDate.parse(value) }.isSuccess

        private val DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    }
}

data class SchedulePreferences(
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val reminder: ReminderPreferences = ReminderPreferences.defaults,
    val academicCalendar: AcademicCalendarPreferences = AcademicCalendarPreferences.defaults,
) {
    fun normalized(): SchedulePreferences = copy(
        reminder = reminder.normalized(),
        academicCalendar = academicCalendar.normalized(),
    )

    companion object {
        val defaults = SchedulePreferences()
    }
}

interface SchedulePreferencesRepository {
    suspend fun load(): SchedulePreferences
    suspend fun save(preferences: SchedulePreferences): SchedulePreferences
    suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences): SchedulePreferences
}

internal object SchedulePreferencesFormat {
    const val VERSION = 1
    val json = Json { ignoreUnknownKeys = true }
}

internal object SchedulePreferencesKeys {
    val version = intPreferencesKey("preferences_version")
    val appearanceMode = stringPreferencesKey("appearance_mode")
    val remindersEnabled = booleanPreferencesKey("reminders_enabled")
    val reminderLeadMinutes = intPreferencesKey("reminder_lead_minutes")
    val usesCustomLeadTime = booleanPreferencesKey("uses_custom_lead_time")
    val weekendsAreNonTeachingDays = booleanPreferencesKey("weekends_are_non_teaching_days")
    val nonTeachingDates = stringPreferencesKey("non_teaching_dates_json")
    val makeupTeachingDays = stringPreferencesKey("makeup_teaching_days_json")
    val lunchBreakEnabled = booleanPreferencesKey("lunch_break_enabled")
    val lunchBreakTitle = stringPreferencesKey("lunch_break_title")
    val lunchBreakStartTime = stringPreferencesKey("lunch_break_start_time")
    val lunchBreakEndTime = stringPreferencesKey("lunch_break_end_time")
}

internal fun Preferences.toSchedulePreferences(): SchedulePreferences {
    val storedLeadMinutes = this[SchedulePreferencesKeys.reminderLeadMinutes]
        ?: ReminderPreferences.DEFAULT_LEAD_MINUTES
    val validLeadMinutes = storedLeadMinutes in ReminderPreferences.VALID_LEAD_MINUTES
    val resolvedLeadMinutes = if (validLeadMinutes) storedLeadMinutes else ReminderPreferences.DEFAULT_LEAD_MINUTES
    val storedCustomLeadTime = this[SchedulePreferencesKeys.usesCustomLeadTime]
    val customLeadTime = when {
        !validLeadMinutes -> false
        storedCustomLeadTime == null -> resolvedLeadMinutes !in ReminderPreferences.PRESET_LEAD_MINUTES
        else -> storedCustomLeadTime
    }
    return SchedulePreferences(
        appearanceMode = AppearanceMode.fromStorage(this[SchedulePreferencesKeys.appearanceMode]),
        reminder = ReminderPreferences(
            remindersEnabled = this[SchedulePreferencesKeys.remindersEnabled] ?: false,
            reminderLeadMinutes = resolvedLeadMinutes,
            usesCustomLeadTime = customLeadTime,
        ),
        academicCalendar = AcademicCalendarPreferences(
            weekendsAreNonTeachingDays = this[SchedulePreferencesKeys.weekendsAreNonTeachingDays] ?: false,
            nonTeachingDates = decodeList(this[SchedulePreferencesKeys.nonTeachingDates]),
            makeupTeachingDays = decodeMakeupDays(this[SchedulePreferencesKeys.makeupTeachingDays]),
            lunchBreak = LunchBreakSettings(
                isEnabled = this[SchedulePreferencesKeys.lunchBreakEnabled] ?: true,
                title = this[SchedulePreferencesKeys.lunchBreakTitle] ?: LunchBreakSettings.DEFAULT_TITLE,
                startTime = this[SchedulePreferencesKeys.lunchBreakStartTime] ?: LunchBreakSettings.DEFAULT_START_TIME,
                endTime = this[SchedulePreferencesKeys.lunchBreakEndTime] ?: LunchBreakSettings.DEFAULT_END_TIME,
            ),
        ),
    ).normalized()
}

internal fun SchedulePreferences.toPreferences(): Preferences {
    val value = normalized()
    return mutablePreferencesOf(
        SchedulePreferencesKeys.version to SchedulePreferencesFormat.VERSION,
        SchedulePreferencesKeys.appearanceMode to value.appearanceMode.storageValue,
        SchedulePreferencesKeys.remindersEnabled to value.reminder.remindersEnabled,
        SchedulePreferencesKeys.reminderLeadMinutes to value.reminder.reminderLeadMinutes,
        SchedulePreferencesKeys.usesCustomLeadTime to value.reminder.usesCustomLeadTime,
        SchedulePreferencesKeys.weekendsAreNonTeachingDays to value.academicCalendar.weekendsAreNonTeachingDays,
        SchedulePreferencesKeys.nonTeachingDates to SchedulePreferencesFormat.json.encodeToString(value.academicCalendar.nonTeachingDates),
        SchedulePreferencesKeys.makeupTeachingDays to SchedulePreferencesFormat.json.encodeToString(value.academicCalendar.makeupTeachingDays),
        SchedulePreferencesKeys.lunchBreakEnabled to value.academicCalendar.lunchBreak.isEnabled,
        SchedulePreferencesKeys.lunchBreakTitle to value.academicCalendar.lunchBreak.title,
        SchedulePreferencesKeys.lunchBreakStartTime to value.academicCalendar.lunchBreak.startTime,
        SchedulePreferencesKeys.lunchBreakEndTime to value.academicCalendar.lunchBreak.endTime,
    )
}

private fun decodeList(value: String?): List<String> =
    value?.let { runCatching { SchedulePreferencesFormat.json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) }
        ?: emptyList()

private fun decodeMakeupDays(value: String?): List<MakeupTeachingDay> =
    value?.let { runCatching { SchedulePreferencesFormat.json.decodeFromString<List<MakeupTeachingDay>>(it) }.getOrDefault(emptyList()) }
        ?: emptyList()
