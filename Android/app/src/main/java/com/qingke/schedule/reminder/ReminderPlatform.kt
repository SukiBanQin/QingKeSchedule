package com.qingke.schedule.reminder

/**
 * A08/D03: the three capabilities are modelled separately. Reminders being switched on is never treated as
 * "reliably scheduled": the caller must look at notification permission, channel readiness and exact-alarm
 * capability individually, and a plan whose active alarms are inexact is reported as degraded.
 */
data class ReminderAvailability(
    val notificationsPermitted: Boolean,
    val channelReady: Boolean,
    val exactAlarmsAvailable: Boolean,
) {
    val canDeliver: Boolean get() = notificationsPermitted && channelReady
}

/** A08: replaceable alarm backend (AlarmManager in production, a fake in tests). */
interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean

    /**
     * Registers (or replaces) [alarm]; implementations use an exact or inexact RTC_WAKEUP alarm based on
     * [ReminderAlarm.exact]. Re-submitting the same identity is idempotent, which is what a reboot rebuild
     * relies on.
     */
    fun schedule(alarm: ReminderAlarm)

    fun cancel(uri: String)

    /**
     * Whether a PendingIntent token for this identity currently exists. This is NOT proof that AlarmManager
     * still holds the alarm (a token can exist without an alarm and vice versa), so production code never uses
     * it to decide whether a rebuild is needed.
     */
    fun isRegistered(uri: String): Boolean
}

/** A08: replaceable notification backend (NotificationManager in production, a fake in tests). */
interface NotificationPresenter {
    /**
     * Creates the "上课提醒" channel when needed; returns whether the channel is usable. A channel the user
     * turned off (IMPORTANCE_NONE) counts as unusable.
     */
    fun ensureChannel(): Boolean

    fun areNotificationsPermitted(): Boolean

    /** Posts [alarm] and returns whether a notification was actually published. */
    fun notify(alarm: ReminderAlarm): Boolean

    fun cancelNotification(uri: String)
}
