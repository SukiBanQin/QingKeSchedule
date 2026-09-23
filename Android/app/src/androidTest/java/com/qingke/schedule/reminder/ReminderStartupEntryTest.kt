package com.qingke.schedule.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qingke.schedule.MainActivity
import com.qingke.schedule.QingKeScheduleApplication
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A08/R1: entering the app is a production rebuild entry. Opening [MainActivity] must re-submit the expected
 * alarms without any permission prompt or new UI.
 */
@RunWith(AndroidJUnit4::class)
class ReminderStartupEntryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: QingKeScheduleApplication = context as QingKeScheduleApplication
    private val zone: ZoneId = ZoneId.systemDefault()
    private val nextMonday: LocalDate = LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    @Before
    fun seed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        }
        val semester = Semester("startup", "启动学期", nextMonday.toString(), 4, listOf(Period(1, "08:00", "08:45")))
        val course = Course("startup", "启动课程", "王老师", "#287B74", listOf(CourseSchedule("slot", 1, 1, 1, 1, 4, RepeatRule.EVERY, "A101")))
        runBlocking {
            application.dependencies.scheduleRepository.replace(ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z"))
            application.dependencies.preferencesRepository.update { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = true))
            }
            application.dependencies.reminderCoordinator.cancelAll(ReminderReconcileReason.MANUAL)
        }
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
    }

    @Test fun reopeningTheAppResubmitsTheExpectedAlarms() {
        assertTrue(runBlocking { application.dependencies.reminderRegistry.load().alarms.isEmpty() })

        ActivityScenario.launch(MainActivity::class.java).use {
            it.onActivity { activity -> assertTrue(activity is MainActivity) }
        }

        assertTrue("opening the app must resubmit the window", awaitRegisteredAlarms() >= 1)
    }

    private fun awaitRegisteredAlarms(): Int {
        val scheduler = AndroidAlarmScheduler(context)
        repeat(40) {
            val alarms = runBlocking { application.dependencies.reminderRegistry.load().alarms }
            val registered = alarms.count { scheduler.isRegistered(it.uri) }
            if (registered >= 1) return registered
            Thread.sleep(100)
        }
        return 0
    }
}
