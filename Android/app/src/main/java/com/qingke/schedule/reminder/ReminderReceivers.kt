package com.qingke.schedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingke.schedule.QingKeScheduleApplication
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A08: fires when one planned alarm is due. The receiver re-checks the committed data through the coordinator
 * before posting, so a payload that belongs to an older timetable is suppressed, and it then reconciles the
 * next rolling window so reminders keep advancing without any resident service.
 */
class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COURSE_REMINDER) return
        val payload = intent.toReminderAlarm() ?: return
        val application = context.applicationContext as? QingKeScheduleApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val coordinator = application.dependencies.reminderCoordinator
                coordinator.deliver(payload, Instant.now())
                coordinator.reconcile(ReminderReconcileReason.ALARM_FIRED)
            } catch (error: Throwable) {
                // A reminder must never crash the app or corrupt data; the next entry point retries.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COURSE_REMINDER = "com.qingke.schedule.action.COURSE_REMINDER"

        val EXTRA_KEYS = listOf("courseIndex", "scheduleIndex", "courseId", "scheduleId", "week", "date", "isMakeup", "fireAt", "startAt", "title", "body", "exact")
    }
}

/** A08: rebuild entries - boot, app update, time/timezone change and exact-alarm permission change. */
class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (reminderRebuildReason(intent.action) == null) return
        val application = context.applicationContext as? QingKeScheduleApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                performReminderRebuild(application.dependencies.reminderCoordinator, intent.action)
            } catch (error: Throwable) {
                // Ignore: the schedule and the preferences are never touched by a failed rebuild.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+) without a hard dependency. */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}

/**
 * A08: the rebuild entry points are protected system broadcasts, so the action mapping and the reconcile call
 * live outside the receiver as well and can be exercised directly by tests.
 */
internal fun reminderRebuildReason(action: String?): ReminderReconcileReason? = when (action) {
    Intent.ACTION_BOOT_COMPLETED -> ReminderReconcileReason.BOOT_COMPLETED
    Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderReconcileReason.PACKAGE_REPLACED
    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> ReminderReconcileReason.TIME_CHANGED
    ReminderRebuildReceiver.ACTION_EXACT_ALARM_PERMISSION_CHANGED -> ReminderReconcileReason.EXACT_ALARM_PERMISSION_CHANGED
    else -> null
}

internal suspend fun performReminderRebuild(
    coordinator: CourseReminderCoordinator,
    action: String?,
): ReminderReconciliation? = reminderRebuildReason(action)?.let { reason -> coordinator.reconcile(reason) }

internal fun ReminderAlarm.toExtras() = android.os.Bundle().apply {
    putInt("courseIndex", identity.courseIndex)
    putInt("scheduleIndex", identity.scheduleIndex)
    putString("courseId", identity.courseId)
    putString("scheduleId", identity.scheduleId)
    putInt("week", identity.week)
    putString("date", identity.date.toString())
    putBoolean("isMakeup", identity.isMakeup)
    putLong("fireAt", fireAt.toEpochMilli())
    putLong("startAt", startAt.toEpochMilli())
    putString("title", title)
    putString("body", body)
    putBoolean("exact", exact)
}

internal fun Intent.toReminderAlarm(): ReminderAlarm? {
    val courseIndex = getIntExtra("courseIndex", -1)
    val scheduleIndex = getIntExtra("scheduleIndex", -1)
    val week = getIntExtra("week", -1)
    val fireAt = getLongExtra("fireAt", -1)
    val startAt = getLongExtra("startAt", -1)
    val date = getStringExtra("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val courseId = getStringExtra("courseId")
    val scheduleId = getStringExtra("scheduleId")
    val title = getStringExtra("title")
    val body = getStringExtra("body")
    if (courseIndex < 0 || scheduleIndex < 0 || week < 0 || fireAt < 0 || startAt < 0 || date == null ||
        courseId == null || scheduleId == null || title == null || body == null
    ) {
        return null
    }
    return ReminderAlarm(
        identity = CourseReminderIdentity(
            courseIndex = courseIndex,
            scheduleIndex = scheduleIndex,
            courseId = courseId,
            scheduleId = scheduleId,
            week = week,
            date = date,
            isMakeup = getBooleanExtra("isMakeup", false),
        ),
        fireAt = Instant.ofEpochMilli(fireAt),
        startAt = Instant.ofEpochMilli(startAt),
        title = title,
        body = body,
        exact = getBooleanExtra("exact", false),
    )
}
