package com.qingke.schedule.reminder

import java.time.Duration
import java.time.Instant

/**
 * A08 third batch: the internal rolling-window fallback.
 *
 * AlarmManager only holds the alarms inside the bounded rolling window, so a timetable whose next occurrence is
 * further away - or an app that is never opened again and has no course alarm left - would freeze the window.
 * This fallback is one fixed, conflict-free and idempotent identity that wakes the app before the window lapses
 * and simply runs one reconciliation, which advances the window and arms the next fallback.
 *
 * It is not a course reminder: it never posts a notification, is never stored in the reminder registry and is
 * therefore invisible to the active count and the degraded flag. It also never needs the exact-alarm
 * capability: being late only delays a re-plan, while [ReminderAlarm.exact] stays reserved for real reminders
 * (D03).
 */
object ReminderMaintenance {
    /** Fixed identity: its own receiver, action and data URI, so it can never collide with a reminder URI. */
    const val URI = "qingke://reminder-maintenance/window"

    /**
     * Half the rolling window (14 days to 7 days). Half is the largest safe period: a run only covers [window]
     * from that moment, so waking up any later could already have missed it, and anything shorter only wakes the
     * device more often without extending the guarantee.
     */
    fun nextFireAt(moment: Instant, window: Duration): Instant = moment.plus(window.dividedBy(2))
}
