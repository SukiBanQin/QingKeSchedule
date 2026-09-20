package com.qingke.schedule.reminder

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.QingKeScheduleApplication
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A08/R1/R2: a fired reminder whose notification permission was revoked must be suppressed before it is posted
 * and must never be reported as delivered, while a permitted reminder must really reach the notification shade.
 *
 * The permission cannot be revoked from inside the tested process - the platform kills the app when a granted
 * runtime permission is revoked, and the runtime appop behind POST_NOTIFICATIONS refuses direct writes - so the
 * denied branch is exercised by a dedicated run that revokes the permission first and records its output in
 * docs/Android/evidence/p3-08-a08-reminders/:
 *
 *   1. adb shell pm revoke com.qingke.schedule android.permission.POST_NOTIFICATIONS
 *   2. adb shell am instrument -w -e class com.qingke.schedule.reminder.ReminderPermissionRevocationTest \
 *        com.qingke.schedule.test/androidx.test.runner.AndroidJUnitRunner
 *
 * In the regular connected run the permission is granted by then, so the case asserts the positive path: the
 * reminder is delivered and appears under its reminder URI. Neither branch skips, so the suite stays green.
 */
@RunWith(AndroidJUnit4::class)
class ReminderPermissionRevocationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: QingKeScheduleApplication = context as QingKeScheduleApplication
    private val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val nextMonday: LocalDate = LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    private val semester = Semester(
        "permission",
        "权限学期",
        nextMonday.toString(),
        4,
        listOf(Period(1, "08:00", "08:45")),
    )
    private val course = Course(
        "permission",
        "权限课程",
        "王老师",
        "#287B74",
        listOf(CourseSchedule("slot", 1, 1, 1, 1, 4, RepeatRule.EVERY, "A101")),
    )

    private fun committedData() = ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z")

    @Before
    fun seed() {
        runBlocking {
            application.dependencies.scheduleRepository.replace(committedData())
            application.dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = true))
            }
            application.dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
        }
        notifications.cancelAll()
    }

    @After
    fun clear() {
        runBlocking {
            application.dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
            application.dependencies.scheduleRepository.replace(ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"))
            application.dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = false))
            }
        }
        notifications.cancelAll()
    }

    private fun plannedPayload(): ReminderAlarm {
        val preferences = runBlocking { application.dependencies.preferencesRepository.load() }
        val planned = CourseReminderPlanner.plan(
            data = committedData(),
            academicCalendar = preferences.academicCalendar,
            leadMinutes = preferences.reminder.reminderLeadMinutes,
            now = Instant.now(),
            zone = zone,
            window = Duration.ofDays(30),
        ).first()
        return ReminderAlarm.from(planned, exact = true)
    }

    /** The production identity is the full reminder URI as the notification tag. */
    private fun awaitPostedReminder(payload: ReminderAlarm): StatusBarNotification? {
        repeat(40) {
            val found = notifications.activeNotifications.firstOrNull { it.tag == payload.uri }
            if (found != null) return found
            Thread.sleep(50)
        }
        return null
    }

    @Test
    fun aReminderIsSuppressedWhenNotificationsAreNotPermitted() {
        val payload = plannedPayload()
        val permitted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        // Recorded so the evidence file shows which branch this device run really exercised.
        println("ReminderPermissionRevocationTest: permitted=" + permitted + " uri=" + payload.uri)

        val outcome = runBlocking { application.dependencies.reminderCoordinator.deliver(payload, Instant.now()) }

        if (permitted) {
            assertTrue(
                "a permitted reminder must be delivered, but the outcome was " + outcome,
                outcome is ReminderDelivery.Delivered,
            )
            assertNotNull(
                "the delivered reminder must be found by its URI tag " + payload.uri,
                awaitPostedReminder(payload),
            )
        } else {
            assertEquals("notifications not permitted", (outcome as ReminderDelivery.Suppressed).reason)
            Thread.sleep(500)
            // Look for the reminder by the identity production posts with (URI tag) and independently by title,
            // so a posted notification can never be missed by comparing the wrong extra.
            val leaked = notifications.activeNotifications.filter { status ->
                status.tag == payload.uri ||
                    status.notification.extras.getCharSequence("android.title")?.toString() == payload.title
            }
            assertTrue(
                "a suppressed reminder must not appear in the shade: " + leaked.map { it.tag },
                leaked.isEmpty(),
            )
        }
    }
}
