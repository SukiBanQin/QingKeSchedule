package com.qingke.schedule.ui

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.activity.ComponentActivity
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
import com.qingke.schedule.reminder.AndroidAlarmScheduler
import com.qingke.schedule.reminder.ReminderReconcileReason
import com.qingke.schedule.state.ScheduleAppState
import com.qingke.schedule.viewmodel.ScheduleViewModel
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A08 second batch: the real settings flow - Room, DataStore, the production coordinator, AlarmManager and the
 * production Compose page. It proves that switching the reminder on persists the preference, plans the rolling
 * window, re-plans on a lead-time change and cancels every alarm again when switched off.
 *
 * The page is rendered with the production [QingKeApp] on a real [ScheduleViewModel] so the test never restates
 * the UI state by hand; only the notification permission is controlled from the outside (see
 * [ReminderPermissionRevocationTest] for the pre-revoked dedicated run).
 */
@RunWith(AndroidJUnit4::class)
class ReminderSettingsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: QingKeScheduleApplication = context as QingKeScheduleApplication
    private val dependencies get() = application.dependencies
    private val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val nextMonday: LocalDate = LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    /** The dedicated evidence run revokes the permission first and asks the page to report that state. */
    private fun permissionDeliberatelyDenied(): Boolean =
        InstrumentationRegistry.getArguments().getString("qingkePermissionDenied") == "true"

    @Before fun seed() {
        if (!permissionDeliberatelyDenied() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        }
        runBlocking {
            dependencies.scheduleRepository.replace(committedData())
            dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = false, reminderLeadMinutes = 10, usesCustomLeadTime = false))
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

    @Test fun enablingAndDisablingRemindersFromTheSettingsPageSchedulesAndCancelsTheWindow() {
        val model = model()
        rule.setContent { QingKeApp(model) }
        rule.onNodeWithTag("settings-tab").performClick()

        rule.onNodeWithTag("settings-reminders-toggle").performScrollTo().performClick()

        assertTrue("the preference must be persisted", awaitUntil { storedPreferences().reminder.remindersEnabled })
        assertTrue("the rolling window must be registered", awaitUntil { platformAlarmCount() > 0 })
        val planned = registryAlarms()
        assertTrue(planned.isNotEmpty())
        assertEquals(planned.size, platformAlarmCount())
        assertTrue(planned.all { it.uri.startsWith("qingke://reminder/") })
        assertTrue(
            "the status line must report the planned window",
            awaitUntil { model.reminderUi.value.statusMessage.contains("已安排最近") },
        )
        rule.waitForIdle()
        rule.onNodeWithTag("settings-reminders-status").performScrollTo()
            .assertTextContains("已安排最近", substring = true)

        captureReminderSection("settings-reminders-enabled")

        rule.onNodeWithTag("settings-reminders-toggle").performScrollTo().performClick()

        assertTrue("switching off must cancel every registered alarm", awaitUntil { platformAlarmCount() == 0 })
        assertTrue("the registry must be cleared as well", awaitUntil { registryAlarms().isEmpty() })
        assertTrue(awaitUntil { model.reminderUi.value.statusMessage == "提醒已关闭" })
        rule.waitForIdle()
        rule.onNodeWithTag("settings-reminders-status").performScrollTo().assertTextEquals("提醒已关闭")
    }

    @Test fun aLeadTimeChosenOnThePageReplansTheAlarms() {
        val model = model()
        rule.setContent { QingKeApp(model) }
        rule.onNodeWithTag("settings-tab").performClick()
        rule.onNodeWithTag("settings-reminders-toggle").performScrollTo().performClick()
        assertTrue(awaitUntil { registryAlarms().isNotEmpty() })

        rule.onNodeWithTag("settings-reminders-lead-30").performScrollTo().performClick()

        assertTrue(awaitUntil { storedPreferences().reminder.reminderLeadMinutes == 30 })
        assertTrue(
            "every planned alarm must follow the new lead time",
            awaitUntil {
                val alarms = registryAlarms()
                alarms.isNotEmpty() && alarms.all { it.fireAt == it.startAt.minusSeconds(30L * 60) }
            },
        )
        assertTrue("a preset keeps the custom flag off", !storedPreferences().reminder.usesCustomLeadTime)

        rule.onNodeWithTag("settings-reminders-lead-custom").performScrollTo().performClick()
        assertTrue(awaitUntil { storedPreferences().reminder.usesCustomLeadTime })
        val stored = storedPreferences().reminder.reminderLeadMinutes
        assertEquals(30, stored)
        rule.onNodeWithTag("settings-reminders-lead-custom-plus").performScrollTo().performClick()
        assertTrue(awaitUntil { storedPreferences().reminder.reminderLeadMinutes == 31 })
    }

    @Test fun theCapabilityRowsFollowTheRealPlatformState() {
        val model = model()
        rule.setContent { QingKeApp(model) }
        rule.onNodeWithTag("settings-tab").performClick()

        val permitted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (permitted) {
            rule.onNodeWithTag("settings-reminders-permission-value").performScrollTo().assertTextEquals("已授权")
        } else {
            // Dedicated pre-revoked run. The switch itself raises the system permission dialog, which an
            // automated run must not depend on, so the same production entry is driven through the ViewModel:
            // enabling must not schedule anything and the page must stay usable.
            model.setRemindersEnabled(true)
            assertTrue(awaitUntil { storedPreferences().reminder.remindersEnabled })
            assertTrue(awaitUntil { model.reminderUi.value.remindersEnabled })
            rule.onNodeWithTag("settings-reminders-permission-value").performScrollTo().assertTextEquals("未开启")
            rule.onNodeWithTag("settings-reminders-status").performScrollTo()
                .assertTextEquals("系统通知权限未开启，提醒不会投递")
            rule.onNodeWithTag("settings-reminders-request-permission").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("settings-reminders-open-notification-settings").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("settings-semester-section").performScrollTo().assertIsDisplayed()
            assertTrue("nothing may be registered without the permission", awaitUntil { platformAlarmCount() == 0 })
            captureReminderSection("settings-reminders-permission-denied")
        }

        val exactAvailable = runCatching { AndroidAlarmScheduler(context).canScheduleExactAlarms() }.getOrDefault(false)
        rule.onNodeWithTag("settings-reminders-exact-value").performScrollTo()
            .assertTextEquals(if (exactAvailable) "可用" else "不可用")
    }

    private fun model() = ScheduleViewModel(
        appState = ScheduleAppState(dependencies.scheduleRepository, dependencies.preferencesRepository),
        now = { LocalDateTime.now(zone) },
        idFactory = { java.util.UUID.randomUUID().toString() },
        reminders = dependencies.reminderCoordinator,
    )

    private fun storedPreferences() = runBlocking { dependencies.preferencesRepository.load() }

    private fun registryAlarms() = runBlocking { dependencies.reminderRegistry.load().alarms }

    private fun platformAlarmCount(): Int {
        val scheduler = AndroidAlarmScheduler(context)
        return registryAlarms().count { scheduler.isRegistered(it.uri) }
    }

    /** Real platform work happens on IO threads, so the flow polls instead of relying on a single idle pass. */
    private fun awaitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    /** Device evidence: the reminder panel exactly as the running app renders it. */
    private fun captureReminderSection(name: String) {
        val bitmap = rule.onNodeWithTag("settings-reminders-panel").performScrollTo().captureToImage().asAndroidBitmap()
        val directory = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.cacheDir.absolutePath,
            "p3-08-a08-reminders-batch2",
        )
        if (directory.exists() || directory.mkdirs()) {
            File(directory, name + ".png").outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }

    private fun committedData(): ScheduleData {
        val semester = Semester(  "flow", "提醒学期", nextMonday.toString(), 4, listOf(Period(1, "08:00", "08:45")))
        val course = Course(
            "flow",
            "提醒课程",
            "王老师",
            "#287B74",
            listOf(CourseSchedule("slot", 1, 1, 1, 1, 4, RepeatRule.EVERY, "A101")),
        )
        return ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z")
    }
}
