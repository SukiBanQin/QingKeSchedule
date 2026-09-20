package com.qingke.schedule.reminder

/**
 * A08/D03: the three capabilities are modelled separately. Reminders being switched on is never treated as
 * "reliably scheduled": the caller must look at notification permission, channel readiness and exact-alarm
 * capability individually, and a plan that only registered inexact alarms is reported as degraded.
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

    /** Registers [alarm]; implementations use an exact or inexact RTC_WAKEUP alarm based on [ReminderAlarm.exact]. */
    fun schedule(alarm: ReminderAlarm)

    fun cancel(uri: String)

    /** True when a PendingIntent for this identity is currently registered with the platform. */
    fun isRegistered(uri: String): Boolean
}

/** A08: replaceable notification backend (NotificationManager in production, a fake in tests). */
interface NotificationPresenter {
    /** Creates the "上课提醒" channel when needed; returns whether the channel is usable. */
    fun ensureChannel(): Boolean

    fun areNotificationsPermitted(): Boolean

    fun notify(alarm: ReminderAlarm)

    fun cancelNotification(uri: String)
}
