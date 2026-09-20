package com.qingke.schedule.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.ReminderPreferences
import com.qingke.schedule.preferences.SchedulePreferences
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A08: the alarm and rebuild entries against the real application dependencies.
 *
 * The boot, app-update, time/timezone and exact-permission entries are protected system broadcasts that an app
 * cannot send, so the test exercises the shared handler plus the manifest declarations instead; a real reboot,
 * doze wake-up and delivered system broadcast cannot be produced in this environment and stay unverified.
 */
@RunWith(AndroidJUnit4::class)
class ReminderReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: QingKeScheduleApplication = context as QingKeScheduleApplication
    private val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val nextMonday: LocalDate = LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    private fun semester() = Semester("term", "测试学期", nextMonday.toString(), 4, listOf(Period(1, "08:00", "08:45")))

    private fun course(id: String = "c1", name: String = "高等数学") =
        Course(id, name, "王老师", "#287B74", listOf(CourseSchedule("s1", 1, 1, 1, 1, 4, RepeatRule.EVERY, "A101")))

    private fun committedData(courses: List<Course> = listOf(course())) =
        ScheduleData(1, semester(), courses, "1970-01-01T00:00:00Z")

    /** The payload the coordinator itself would register for the first future occurrence. */
    private fun plannedPayload(data: ScheduleData): ReminderAlarm {
        val preferences = runBlocking { application.dependencies.preferencesRepository.load() }
        val planned = CourseReminderPlanner.plan(
            data = data,
            academicCalendar = preferences.academicCalendar,
            leadMinutes = preferences.reminder.reminderLeadMinutes,
            now = Instant.now(),
            zone = zone,
            window = java.time.Duration.ofDays(30),
        )
        val reminder = planned.first()
        return ReminderAlarm.from(reminder, exact = true)
    }

    @Before
    fun seedCommittedScheduleAndPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        }
        runBlocking {
            application.dependencies.scheduleRepository.replace(committedData())
            application.dependencies.preferencesRepository.update { preferences -> enabled(preferences) }
            application.dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
        }
        notifications.cancelAll()
    }

    @After
    fun clearRemindersAndPostedNotifications() {
        runBlocking {
            application.dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
            application.dependencies.scheduleRepository.replace(ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"))
            application.dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = false))
            }
        }
        notifications.cancelAll()
    }

    @Test fun firedAlarmPostsTheNotificationAndRegistersTheNextWindow() {
        val payload = plannedPayload(committedData())

        sendAlarm(payload)

        val posted = awaitNotification(payload.title)
        assertNotNull("a verified alarm must post a notification", posted)
        assertEquals(payload.body, posted!!.extras.getCharSequence("android.text")?.toString())
        assertTrue(awaitScheduledAlarmsAtLeast(1))
    }

    @Test fun firedAlarmFromAnOldTimetableIsSuppressed() {
        val payload = plannedPayload(committedData())
        val stale = payload.copy(
            identity = payload.identity.copy(date = payload.identity.date.plusDays(14)),
            fireAt = payload.fireAt.plusSeconds(14 * 86_400),
            startAt = payload.startAt.plusSeconds(14 * 86_400),
            title = "过期课程",
        )

        sendAlarm(stale)

        Thread.sleep(1_500)
        assertNull("a payload that no longer matches the committed timetable must not post", notificationFor("过期课程"))
    }

    @Test fun manifestDeclaresTheReminderReceiversAndRebuildActions() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS,
        )

        val permissions = packageInfo.requestedPermissions.orEmpty().toList()
        assertTrue(permissions.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(permissions.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertTrue(permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue("D03 forbids USE_EXACT_ALARM", permissions.none { it == "android.permission.USE_EXACT_ALARM" })

        val alarmReceiver = packageInfo.receivers.orEmpty().firstOrNull { it.name == CourseReminderReceiver::class.java.name }
        val rebuildReceiver = packageInfo.receivers.orEmpty().firstOrNull { it.name == ReminderRebuildReceiver::class.java.name }
        val maintenanceReceiver = packageInfo.receivers.orEmpty().firstOrNull { it.name == ReminderMaintenanceReceiver::class.java.name }
        assertNotNull("the alarm receiver must be declared", alarmReceiver)
        assertNotNull("the rebuild receiver must be declared", rebuildReceiver)
        assertNotNull("the window fallback receiver must be declared", maintenanceReceiver)
        assertEquals(false, alarmReceiver!!.exported)
        assertEquals(false, rebuildReceiver!!.exported)
        assertEquals(false, maintenanceReceiver!!.exported)

        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ReminderRebuildReceiver.ACTION_EXACT_ALARM_PERMISSION_CHANGED,
        ).forEach { action ->
            val resolved = context.packageManager
                .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
                .mapNotNull { it.activityInfo?.name }
            assertTrue("missing rebuild action $action", resolved.contains(ReminderRebuildReceiver::class.java.name))
        }
        val alarmResolved = context.packageManager
            .queryBroadcastReceivers(Intent(CourseReminderReceiver.ACTION_COURSE_REMINDER).setPackage(context.packageName), 0)
            .mapNotNull { it.activityInfo?.name }
        assertTrue(alarmResolved.contains(CourseReminderReceiver::class.java.name))

        val maintenanceResolved = context.packageManager
            .queryBroadcastReceivers(
                Intent(ReminderMaintenanceReceiver.ACTION_REMINDER_MAINTENANCE).setPackage(context.packageName),
                0,
            )
            .mapNotNull { it.activityInfo?.name }
        assertTrue(maintenanceResolved.contains(ReminderMaintenanceReceiver::class.java.name))
    }

    @Test fun everyRebuildEntryPointRegistersThePlannedAlarms() {
        val coordinator = application.dependencies.reminderCoordinator
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ReminderRebuildReceiver.ACTION_EXACT_ALARM_PERMISSION_CHANGED,
        ).forEach { action ->
            runBlocking { coordinator.cancelAll(ReminderReconcileReason.MANUAL) }
            val reconciliation = runBlocking { performReminderRebuild(coordinator, action) }
            assertNotNull("$action must map to a rebuild reason", reconciliation)
            assertTrue("$action must register the planned alarms", awaitScheduledAlarmsAtLeast(1))
        }
        assertNull(reminderRebuildReason(Intent.ACTION_SCREEN_ON))
    }

    @Test fun alarmPayloadRoundTripsThroughTheIntentExtras() {
        val payload = plannedPayload(committedData())
        val intent = Intent(context, CourseReminderReceiver::class.java)
            .setAction(CourseReminderReceiver.ACTION_COURSE_REMINDER)
            .putExtras(payload.toExtras())

        assertEquals(payload, intent.toReminderAlarm())
        assertEquals(null, Intent(context, CourseReminderReceiver::class.java).toReminderAlarm())
    }


    @Test fun rebuildResubmitsWhenThePlatformLostItsAlarms() {
        val coordinator = application.dependencies.reminderCoordinator
        val scheduled = runBlocking { coordinator.reconcile(ReminderReconcileReason.APP_START) }
        assertTrue(scheduled.submitted.isNotEmpty())
        assertEquals(scheduled.submitted.size, awaitRegisteredAlarms())

        // A reboot drops the platform alarms while the persisted registry still lists them.
        val scheduler = AndroidAlarmScheduler(context)
        runBlocking { application.dependencies.reminderRegistry.load().alarms }
            .forEach { alarm -> scheduler.cancel(alarm.uri) }
        assertEquals(0, awaitRegisteredAlarms())
        assertTrue(runBlocking { application.dependencies.reminderRegistry.load().alarms.isNotEmpty() })

        val rebuilt = runBlocking { performReminderRebuild(coordinator, Intent.ACTION_BOOT_COMPLETED) }

        assertNotNull(rebuilt)
        assertEquals(rebuilt!!.submitted.size, awaitRegisteredAlarms())
        assertTrue("the rebuild must re-submit the expected alarms", awaitRegisteredAlarms() >= 1)
    }

    private fun awaitRegisteredAlarms(): Int {
        val scheduler = AndroidAlarmScheduler(context)
        repeat(30) {
            val alarms = runBlocking { application.dependencies.reminderRegistry.load().alarms }
            val registered = alarms.count { scheduler.isRegistered(it.uri) }
            if (registered > 0 || alarms.isEmpty()) return registered
            Thread.sleep(100)
        }
        return 0
    }

    private fun enabled(preferences: SchedulePreferences) = preferences.copy(
        reminder = preferences.reminder.copy(remindersEnabled = true, reminderLeadMinutes = ReminderPreferences.DEFAULT_LEAD_MINUTES),
    )

    private fun sendAlarm(alarm: ReminderAlarm) {
        context.sendBroadcast(
            Intent(context, CourseReminderReceiver::class.java)
                .setAction(CourseReminderReceiver.ACTION_COURSE_REMINDER)
                .putExtras(alarm.toExtras()),
        )
    }

    private fun awaitNotification(title: String): android.app.Notification? {
        repeat(40) {
            notificationFor(title)?.let { return it }
            Thread.sleep(100)
        }
        return null
    }

    private fun notificationFor(title: String): android.app.Notification? =
        notifications.activeNotifications.firstOrNull {
            it.notification.extras.getCharSequence("android.title")?.toString() == title
        }?.notification

    private fun awaitScheduledAlarmsAtLeast(minimum: Int): Boolean {
        val scheduler = AndroidAlarmScheduler(context)
        repeat(40) {
            val alarms = runBlocking { application.dependencies.reminderRegistry.load().alarms }
            if (alarms.count { scheduler.isRegistered(it.uri) } >= minimum) return true
            Thread.sleep(100)
        }
        return false
    }
}
