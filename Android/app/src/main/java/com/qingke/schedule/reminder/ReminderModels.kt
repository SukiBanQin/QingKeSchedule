package com.qingke.schedule.reminder

import java.time.Instant

/**
 * A08: one alarm payload. It carries the occurrence identity, the planned fire instant, the notification
 * title/body and whether the platform registered it as an exact alarm. The same value is the registry entry
 * and the receiver payload.
 */
data class ReminderAlarm(
    val identity: CourseReminderIdentity,
    val fireAt: Instant,
    val startAt: Instant,
    val title: String,
    val body: String,
    val exact: Boolean,
) {
    val uri: String get() = identity.uri

    fun matches(reminder: CourseReminder): Boolean =
        uri == reminder.identity.uri && fireAt == reminder.fireAt

    companion object {
        fun from(reminder: CourseReminder, exact: Boolean, body: String = reminder.body): ReminderAlarm =
            ReminderAlarm(reminder.identity, reminder.fireAt, reminder.startAt, reminder.title, body, exact)
    }
}

/**
 * A08: the app-owned alarm registry. It lives in its own DataStore file so the internal scheduling state can
 * never be rewritten together with the user preference file, and the generation lets a reconcile run detect
 * that another run superseded it before it touches the platform.
 */
data class ReminderRegistryState(
    val generation: Long = 0,
    val alarms: List<ReminderAlarm> = emptyList(),
)

interface ReminderRegistry {
    suspend fun load(): ReminderRegistryState
    suspend fun save(state: ReminderRegistryState): ReminderRegistryState
}

/** A08: notification copy and platform identifiers used by both the scheduler and the receivers. */
object ReminderNotifications {
    const val CHANNEL_ID = "course_reminders"
    const val CHANNEL_NAME = "上课提醒"
    const val CHANNEL_DESCRIPTION = "上课前的课程提醒"
    const val INEXACT_MARKER = "（可能延迟）"

    fun bodyFor(body: String, exact: Boolean): String = if (exact || body.endsWith(INEXACT_MARKER)) body else "$body $INEXACT_MARKER"
}
