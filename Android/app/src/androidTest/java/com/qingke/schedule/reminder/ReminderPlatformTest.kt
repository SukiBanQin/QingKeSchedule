package com.qingke.schedule.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qingke.schedule.QingKeScheduleApplication
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A08: platform plumbing on a real device - channel, alarm identity, exact capability and registry storage. */
@RunWith(AndroidJUnit4::class)
class ReminderPlatformTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun grantNotifications() {
        // publishReminder only reports Delivered when the reminder is really postable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        }
    }

    private fun alarm(
        courseIndex: Int = 0,
        scheduleIndex: Int = 0,
        date: String = "2026-03-02",
        fireAt: Instant = Instant.parse("2026-03-02T00:50:00Z"),
        exact: Boolean = true,
        courseId: String = "same",
    ) = ReminderAlarm(
        identity = CourseReminderIdentity(
            courseIndex = courseIndex,
            scheduleIndex = scheduleIndex,
            courseId = courseId,
            scheduleId = "shared",
            week = 1,
            date = LocalDate.parse(date),
            isMakeup = false,
        ),
        fireAt = fireAt,
        startAt = Instant.parse("2026-03-02T01:00:00Z"),
        title = "高等数学",
        body = "08:00–08:45",
        exact = exact,
    )

    @Test fun notificationChannelIsCreatedWithTheReminderIdentity() {
        val presenter = AndroidNotificationPresenter(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(
            "the reminder channel must be usable: " + manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID),
            presenter.ensureChannel(),
        )

        val channel = manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(ReminderNotifications.CHANNEL_NAME, channel!!.name.toString())
        assertEquals(ReminderNotifications.CHANNEL_DESCRIPTION, channel.description)
    }
    @Test fun alarmIdentityUsesTheReminderUriSoDuplicateIdsCannotCollide() {
        val scheduler = AndroidAlarmScheduler(context)
        val first = alarm(courseIndex = 0, date = "2026-03-02")
        val second = alarm(courseIndex = 1, date = "2026-03-02")
        val third = alarm(courseIndex = 0, date = "2026-03-09")

        try {
            scheduler.schedule(first)
            scheduler.schedule(second)
            scheduler.schedule(third)

            assertTrue(scheduler.isRegistered(first.uri))
            assertTrue(scheduler.isRegistered(second.uri))
            assertTrue(scheduler.isRegistered(third.uri))
            assertEquals(3, listOf(first, second, third).map { it.uri }.distinct().size)

            scheduler.cancel(second.uri)

            assertTrue(scheduler.isRegistered(first.uri))
            assertFalse(scheduler.isRegistered(second.uri))
            assertTrue(scheduler.isRegistered(third.uri))
        } finally {
            listOf(first, second, third).forEach { scheduler.cancel(it.uri) }
        }
        assertFalse(scheduler.isRegistered(first.uri))
    }

    @Test fun exactAlarmCapabilityFollowsTheGrantedAppop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val scheduler = AndroidAlarmScheduler(context)
        val application = context as QingKeScheduleApplication
        assertNotNull(application)

        setExactAlarmAppop("deny")
        assertFalse("SCHEDULE_EXACT_ALARM denied must report no exact capability", scheduler.canScheduleExactAlarms())

        setExactAlarmAppop("allow")
        assertTrue("SCHEDULE_EXACT_ALARM granted must report exact capability", scheduler.canScheduleExactAlarms())

        // Registration works in both modes; the exact/inexact choice is unit tested through ReminderAlarm.exact.
        val inexact = alarm(exact = false)
        val exact = alarm(courseIndex = 2, exact = true)
        try {
            scheduler.schedule(inexact)
            scheduler.schedule(exact)
            assertTrue(scheduler.isRegistered(inexact.uri))
            assertTrue(scheduler.isRegistered(exact.uri))
        } finally {
            scheduler.cancel(inexact.uri)
            scheduler.cancel(exact.uri)
        }
    }

    @Test fun registryRoundTripsInItsOwnStoreAndRecoversFromCorruption() {
        runBlocking {
            val file = File(context.cacheDir, "reminder-registry-${System.nanoTime()}.preferences_pb")
            val registry = DataStoreReminderRegistry.create(file)
            try {
                assertEquals(ReminderRegistryState(), registry.load())

                val state = ReminderRegistryState(generation = 4, alarms = listOf(alarm(), alarm(courseIndex = 1, exact = false)))
                assertEquals(state, registry.save(state))
                assertEquals(state, registry.load())
                // The production registry lives in its own file, never in the whole-file preference store.
                assertEquals("schedule_reminders.preferences_pb", DataStoreReminderRegistry.FILE_NAME)
                assertFalse(DataStoreReminderRegistry.FILE_NAME == com.qingke.schedule.preferences.DataStoreSchedulePreferencesRepository.FILE_NAME)

                val decoded = DataStoreReminderRegistry.decode("{ not json")
                assertEquals(ReminderRegistryState(), decoded)
            } finally {
                registry.close()
                file.delete()
            }
        }
    }


    @Test fun notificationIdentityUsesTheUriTagSoHashCollisionsCannotOverrideEachOther() {
        val presenter = AndroidNotificationPresenter(context)
        assertTrue(presenter.ensureChannel())
        // "Aa" and "BB" share a deterministic Java hashCode, so a hashCode-based identity would collide.
        val first = alarm(courseId = "Aa")
        val second = alarm(courseId = "BB")
        assertEquals(first.uri.hashCode(), second.uri.hashCode())

        try {
            context.getSystemService(NotificationManager::class.java).cancelAll()
            assertTrue(presenter.notify(first))
            assertTrue(presenter.notify(second))

            // Posting is asynchronous on the platform side, so wait for the expected counts.
            assertEquals(2, awaitActiveNotifications(2))

            val titles = context.getSystemService(NotificationManager::class.java).activeNotifications
                .filter { it.notification.extras.getCharSequence("android.text") != null }
                .map { it.notification.extras.getCharSequence("android.text").toString() }
            assertEquals(2, titles.size)

            presenter.cancelNotification(first.uri)
            assertEquals(1, awaitActiveNotifications(1))
        } finally {
            context.getSystemService(NotificationManager::class.java).cancelAll()
        }
    }
    @Test fun aChannelTheUserTurnedOffIsReportedAsUnusable() {
        // The platform preserves a switched-off channel's importance across an app-side delete/recreate, so this
        // test drives a private probe channel instead of pushing the shared reminder channel to IMPORTANCE_NONE.
        val probeId = "course_reminders_probe_" + java.util.UUID.randomUUID()
        val manager = context.getSystemService(NotificationManager::class.java)
        val presenter = AndroidNotificationPresenter(context, probeId)

        try {
            assertTrue("a fresh channel is usable", presenter.ensureChannel())

            manager.deleteNotificationChannel(probeId)
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    probeId,
                    ReminderNotifications.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_NONE,
                ),
            )
            Thread.sleep(250)
            assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(probeId)!!.importance)

            assertFalse("IMPORTANCE_NONE must count as unusable", presenter.ensureChannel())
        } finally {
            manager.deleteNotificationChannel(probeId)
        }
    }

    private fun awaitActiveNotifications(expected: Int): Int {
        val manager = context.getSystemService(NotificationManager::class.java)
        repeat(40) {
            if (manager.activeNotifications.size == expected) return expected
            Thread.sleep(50)
        }
        return manager.activeNotifications.size
    }

    private fun setExactAlarmAppop(mode: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM $mode",
        )
        descriptor.use { it.fileDescriptor }
        Thread.sleep(300)
    }
}
