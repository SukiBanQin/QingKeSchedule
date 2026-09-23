package com.qingke.schedule.reminder

import java.time.Duration
import java.time.Instant

/**
 * A08: the pure decision a fired alarm has to pass before it may post a notification. The payload is compared
 * against a plan recomputed from the currently committed data as of the payload's own fire instant: an
 * occurrence that no longer exists, whose fire time moved, or that started long ago is suppressed so an old
 * schedule can never surface as a fresh reminder.
 */
object CourseReminderDelivery {
    /** A late inexact alarm is still useful shortly after the class started, but not long after. */
    val LATE_TOLERANCE: Duration = Duration.ofMinutes(10)

    sealed interface Decision {
        data class Deliver(val reminder: CourseReminder) : Decision

        data class Suppress(val reason: String) : Decision
    }

    fun decide(payload: ReminderAlarm, currentPlan: List<CourseReminder>, now: Instant): Decision {
        val exact = currentPlan.firstOrNull { it.identity.uri == payload.uri }
        if (exact != null) {
            if (exact.fireAt != payload.fireAt) return Decision.Suppress("fire time changed")
            return afterStartCheck(payload, exact, now)
        }
        val shifted = currentPlan.firstOrNull { reminder ->
            reminder.identity.courseId == payload.identity.courseId &&
                reminder.identity.scheduleId == payload.identity.scheduleId &&
                reminder.identity.scheduleIndex == payload.identity.scheduleIndex &&
                reminder.identity.date == payload.identity.date &&
                reminder.startAt == payload.startAt &&
                reminder.fireAt == payload.fireAt
        } ?: return Decision.Suppress("occurrence no longer planned")
        return afterStartCheck(payload, shifted, now)
    }

    private fun afterStartCheck(payload: ReminderAlarm, reminder: CourseReminder, now: Instant): Decision =
        if (now.isAfter(reminder.startAt.plus(LATE_TOLERANCE))) {
            Decision.Suppress("class already started")
        } else {
            Decision.Deliver(reminder)
        }
}
