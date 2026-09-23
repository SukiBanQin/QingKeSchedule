package com.qingke.schedule.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qingke.schedule.QingKeScheduleApplication
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A08 evidence: posts one real reminder through the production receiver path and records the channel,
 * notification and registry state. The notification is intentionally left active so the notification shade can
 * be captured on the device; a real reboot, doze wake-up or system-delivered broadcast cannot be produced here.
 */
@RunWith(AndroidJUnit4::class)
class ReminderEvidenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: QingKeScheduleApplication = context as QingKeScheduleApplication
    private val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val nextMonday: LocalDate = LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    @Test fun reminderNotificationAndSchedulingEvidenceRecord() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        }
        val semester = Semester("evidence", "证据学期", nextMonday.toString(), 4, listOf(Period(1, "08:00", "08:45")))
        val course = Course("evidence", "证据课程", "王老师", "#287B74", listOf(CourseSchedule("slot", 1, 1, 1, 1, 4, RepeatRule.EVERY, "A101")))
        val data = ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z")

        val record = StringBuilder()
        record.appendLine("P3-08/A08 batch 1 R1 device evidence (API 37 ARM64, ${Build.VERSION.SDK_INT})")
        runBlocking {
            application.dependencies.scheduleRepository.replace(data)
            application.dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = true))
            }
            application.dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
            notifications.cancelAll()

            val preferences = application.dependencies.preferencesRepository.load()
            val planned = CourseReminderPlanner.plan(
                data = data,
                academicCalendar = preferences.academicCalendar,
                leadMinutes = preferences.reminder.reminderLeadMinutes,
                now = Instant.now(),
                zone = zone,
                window = java.time.Duration.ofDays(30),
            )
            val reminder = planned.first()
            val payload = ReminderAlarm.from(reminder, exact = runCatching { AndroidAlarmScheduler(context).canScheduleExactAlarms() }.getOrDefault(false))
            record.appendLine("planned reminder: uri=${payload.uri}")
            record.appendLine("planned fireAt=${payload.fireAt} startAt=${payload.startAt} title=${payload.title} body=${payload.body} exact=${payload.exact}")

            val reconciliation = application.dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.APP_START)
            record.appendLine("reconcile: submitted=${reconciliation.submitted.size} unchanged=${reconciliation.unchanged.size} cancelled=${reconciliation.cancelled.size} active=${reconciliation.activeCount} generation=${reconciliation.generation}")
            record.appendLine("availability: notificationsPermitted=${reconciliation.availability.notificationsPermitted} channelReady=${reconciliation.availability.channelReady} exactAvailable=${reconciliation.availability.exactAlarmsAvailable} degraded=${reconciliation.degraded}")

            context.sendBroadcast(
                Intent(context, CourseReminderReceiver::class.java)
                    .setAction(CourseReminderReceiver.ACTION_COURSE_REMINDER)
                    .putExtras(payload.toExtras()),
            )
        }

        var active: android.app.Notification? = null
        repeat(50) {
            active = notifications.activeNotifications
                .firstOrNull { it.notification.extras.getCharSequence("android.title") != null }?.notification
            if (active == null) Thread.sleep(100)
        }
        val channel = notifications.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
        record.appendLine("channel: id=${channel?.id} name=${channel?.name} importance=${channel?.importance}")
        record.appendLine("active notification: title=${active?.extras?.getCharSequence("android.title")} text=${active?.extras?.getCharSequence("android.text")}")
        record.appendLine("active count=${notifications.activeNotifications.size}")
        record.appendLine("permission denied branch: recorded separately by ReminderPermissionRevocationTest, see permission-denied-20260920.txt")
        record.appendLine("unverified in this environment: real reboot, doze wake-up, system-delivered BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED/MY_PACKAGE_REPLACED/exact-permission broadcasts, exact-vs-inexact delivery timing")

        val directory = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.cacheDir.absolutePath,
            "p3-08-a08-reminders",
        ).also { check(it.exists() || it.mkdirs()) }
        File(directory, "device-verification-20260920.txt").writeText(record.toString())
    }
}
