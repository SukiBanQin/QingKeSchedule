package com.qingke.schedule.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
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
import com.qingke.schedule.preferences.ReminderPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A08 third batch: the internal window fallback on a real device. The timetable's next occurrence is outside the
 * 14 day window, so the registry stays empty while the fixed fallback alarm must still be registered, must not
 * post anything and must be re-armed after it fires.
 */
@RunWith(AndroidJUnit4::class)
class ReminderMaintenanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: QingKeScheduleApplication = context as QingKeScheduleApplication
    private val dependencies get() = application.dependencies
    private val scheduler = AndroidAlarmScheduler(context)
    private val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val monday: LocalDate = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The dedicated evidence run revokes the permission first and asks the fallback to stay cleared. */
    private fun permissionDeliberatelyDenied(): Boolean =
        InstrumentationRegistry.getArguments().getString("qingkePermissionDenied") == "true"

    @Before fun seed() {
        if (!permissionDeliberatelyDenied() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        }
        runBlocking {
            dependencies.scheduleRepository.replace(farTimetable())
            dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = true))
            }
            dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
        }
        notifications.cancelAll()
    }

    @After fun clear() {
        runBlocking {
            dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
            dependencies.scheduleRepository.replace(ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"))
            dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = false))
            }
        }
        notifications.cancelAll()
    }

    @Test fun theFixedFallbackIsRegisteredWithoutCountingAsAReminderAndPostsNothing() {
        val reconciliation = runBlocking { dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.APP_START) }

        assertTrue("the next occurrence is outside the 14 day window", reconciliation.submitted.isEmpty())
        assertTrue(runBlocking { dependencies.reminderRegistry.load().alarms }.isEmpty())
        val snapshot = runBlocking { dependencies.reminderCoordinator.snapshot() }
        assertEquals(0, snapshot.activeCount)
        assertFalse(snapshot.degraded)

        assertTrue("the fixed fallback must be registered", awaitUntil { scheduler.isMaintenanceRegistered() })
        assertTrue("AlarmManager must really hold it", awaitUntil { maintenanceAlarmEntries() >= 1 })
        recordAlarmDump()

        // Reconciling again must replace the same identity, never add a second one.
        runBlocking { dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.WINDOW_MAINTENANCE) }
        Thread.sleep(300)
        assertEquals(1, maintenanceAlarmEntries())

        // Firing it must not post a notification and must leave registry and AlarmManager consistent.
        context.sendBroadcast(
            Intent(context, ReminderMaintenanceReceiver::class.java)
                .setAction(ReminderMaintenanceReceiver.ACTION_REMINDER_MAINTENANCE),
        )
        Thread.sleep(800)
        assertEquals("the fallback is not a course reminder", 0, notifications.activeNotifications.size)
        val alarms = runBlocking { dependencies.reminderRegistry.load().alarms }
        assertEquals(alarms.size, alarms.count { scheduler.isRegistered(it.uri) })
        assertTrue("the fallback must be re-armed after firing", awaitUntil { scheduler.isMaintenanceRegistered() })
        assertTrue(awaitUntil { maintenanceAlarmEntries() == 1 })
    }

    @Test fun theFallbackIsClearedWhenRemindersStopAndReturnsWhenTheyResume() {
        runBlocking { dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.APP_START) }
        assertTrue(awaitUntil { scheduler.isMaintenanceRegistered() })

        runBlocking { dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.PREFERENCES_CHANGED) }

        assertTrue("switching off must clear the fallback", awaitUntil { !scheduler.isMaintenanceRegistered() })
        assertTrue(awaitUntil { maintenanceAlarmEntries() == 0 })

        runBlocking {
            dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = true))
            }
            dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
        }

        assertTrue("switching back on must re-arm the fallback", awaitUntil { scheduler.isMaintenanceRegistered() })
        assertTrue(awaitUntil { maintenanceAlarmEntries() == 1 })
    }

    @Test fun anUndeliverableCapabilityKeepsTheFallbackClearedAndARecoveredOneRearmsIt() {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            // Regular run: the capability is there, so the fallback must be registered.
            runBlocking { dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.APP_START) }
            assertTrue(awaitUntil { scheduler.isMaintenanceRegistered() })
            assertTrue(awaitUntil { maintenanceAlarmEntries() == 1 })
        } else {
            // Dedicated pre-revoked run: without the notification permission nothing may be deliverable, so the
            // fallback (and every course reminder) must stay cleared.
            runBlocking { dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.APP_START) }
            Thread.sleep(300)
            assertFalse("an undeliverable reminder must not keep the fallback", scheduler.isMaintenanceRegistered())
            assertEquals(0, maintenanceAlarmEntries())
            assertTrue(runBlocking { dependencies.reminderRegistry.load().alarms }.isEmpty())

            // The permission is granted without restarting the app; the production recovery entry reconciles.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
            assertTrue(awaitUntil { NotificationManagerCompat.from(context).areNotificationsEnabled() })
            runBlocking { dependencies.reminderCoordinator.reconcile(ReminderReconcileReason.MANUAL) }

            assertTrue("a recovered capability must re-arm the fallback", awaitUntil { scheduler.isMaintenanceRegistered() })
            assertTrue(awaitUntil { maintenanceAlarmEntries() == 1 })
        }
    }

    private fun farTimetable(): ScheduleData {
        val semester = Semester("maintenance", "兜底学期", monday.toString(), 6, listOf(Period(1, "08:00", "08:45")))
        val course = Course(
            "maintenance",
            "远期课程",
            "王老师",
            "#287B74",
            listOf(CourseSchedule("slot", 1, 1, 1, 5, 5, RepeatRule.EVERY, "A101")),
        )
        return ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z")
    }

    /** Writes the real platform listing of the fallback into the device evidence directory. */
    private fun recordAlarmDump() {
        val lines = alarmDump().lines().filter { line -> line.contains(context.packageName) || line.contains(ReminderMaintenanceReceiver.ACTION_REMINDER_MAINTENANCE) }
        val directory = java.io.File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.cacheDir.absolutePath,
            "p3-08-a08-reminders-batch3",
        )
        if (directory.exists() || directory.mkdirs()) {
            java.io.File(directory, "maintenance-alarm-dump.txt").writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
        }
    }

    /**
     * Real platform evidence: [alarmDump] is parsed into alarm blocks, and a block only counts when its own
     * alarm line belongs to this package and the very next line is the fallback's action tag. That is the real
     * AlarmManager entry, so two consecutive reconciles can only ever produce one of them.
     */
    private fun maintenanceAlarmEntries(): Int {
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        alarmDump().lines().forEach { line ->
            if (line.contains("Alarm{")) {
                current?.let { blocks += it }
                current = mutableListOf()
            }
            current?.add(line)
        }
        current?.let { blocks += it }
        return blocks.count { block ->
            block.size >= 2 &&
                block[0].contains(context.packageName) &&
                block[1].contains(ReminderMaintenanceReceiver.ACTION_REMINDER_MAINTENANCE)
        }
    }

    private fun alarmDump(): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys alarm")
        return descriptor.use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().decodeToString() }
    }

    private fun awaitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }
}
