package com.qingke.schedule.reminder

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.SchedulePreferences
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A08: coordinator behaviour - add/retain/replace/cancel, single writer, generation gate, failure handling. */
class CourseReminderCoordinatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val nowInstant = Instant.parse("2026-03-01T00:00:00Z")
    private val weekOne = Instant.parse("2026-03-01T23:50:00Z")
    private val weekTwo = Instant.parse("2026-03-08T23:50:00Z")

    private fun semester() = Semester("term", "测试学期", "2026-03-02", 4, listOf(Period(1, "08:00", "08:45")))

    private fun schedule(id: String = "s1", startWeek: Int = 1, endWeek: Int = 2) =
        CourseSchedule(id, 1, 1, 1, startWeek, endWeek, RepeatRule.EVERY, "")

    private fun data(courses: List<Course> = listOf(Course("c1", "高等数学", "王老师", "#287B74", listOf(schedule())))) =
        ScheduleData(1, semester(), courses, "1970-01-01T00:00:00Z")

    /** A08 third batch: the next occurrence is outside a 14 day window but inside the next one. */
    private fun farData(): ScheduleData = ScheduleData(
        1,
        Semester("term", "测试学期", "2026-03-02", 18, listOf(Period(1, "08:00", "08:45"))),
        listOf(Course("far", "远期课程", "", "#287B74", listOf(CourseSchedule("far-slot", 1, 1, 1, 3, 3, RepeatRule.EVERY, "")))),
        "1970-01-01T00:00:00Z",
    )

    @Test fun theMaintenanceAlarmIsArmedHalfAWindowAhead() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()

        coordinator(scheduler = scheduler, registry = registry, window = Duration.ofDays(14))
            .reconcile(ReminderReconcileReason.APP_START)

        assertEquals(nowInstant.plus(Duration.ofDays(7)), scheduler.maintenanceFireAt)
        assertTrue("the period must never exceed half the window", scheduler.maintenanceFireAt!! <= nowInstant.plus(Duration.ofDays(14)))
        assertTrue(scheduler.isMaintenanceRegistered())
    }

    @Test fun theMaintenanceAlarmSurvivesAnEmptyWindowAndBringsTheFarCourseIn() = runTest {
        var moment = nowInstant
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(
            scheduler = scheduler,
            registry = registry,
            data = farData(),
            window = Duration.ofDays(14),
            now = { moment },
        )

        val empty = model.reconcile(ReminderReconcileReason.APP_START)

        assertTrue("no occurrence is inside the 14 day window yet", empty.submitted.isEmpty())
        assertTrue(registry.state.alarms.isEmpty())
        assertFalse("an internal fallback is not a course reminder", empty.activeAlarms.any { it.uri == ReminderMaintenance.URI })
        assertEquals(0, empty.activeCount)
        assertEquals(moment.plus(Duration.ofDays(7)), scheduler.maintenanceFireAt)

        // The fallback fires a week later, when the far occurrence has entered the fresh window.
        moment = moment.plus(Duration.ofDays(7))
        val advanced = model.reconcile(ReminderReconcileReason.WINDOW_MAINTENANCE)

        assertEquals(listOf("qingke://reminder/0/far/0/far-slot/3/2026-03-16"), advanced.submitted)
        assertEquals(1, registry.state.alarms.size)
        assertEquals(1, advanced.activeCount)
        assertFalse(advanced.activeAlarms.any { it.uri == ReminderMaintenance.URI })
        assertEquals(moment.plus(Duration.ofDays(7)), scheduler.maintenanceFireAt)
    }

    @Test fun repeatedMaintenanceTriggersAndTimeChangesNeverDuplicateIdentities() = runTest {
        var moment = nowInstant
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry, now = { moment })

        model.reconcile(ReminderReconcileReason.APP_START)
        model.reconcile(ReminderReconcileReason.WINDOW_MAINTENANCE)
        model.reconcile(ReminderReconcileReason.WINDOW_MAINTENANCE)
        moment = moment.plus(Duration.ofDays(3))
        model.reconcile(ReminderReconcileReason.TIME_CHANGED)
        model.reconcile(ReminderReconcileReason.WINDOW_MAINTENANCE)

        val uris = registry.state.alarms.map { it.uri }
        assertEquals("the registry must not contain duplicate identities", uris.size, uris.toSet().size)
        assertEquals(uris.toSet(), scheduler.registered.keys)
        assertFalse(registry.state.alarms.any { it.uri == ReminderMaintenance.URI })
        assertEquals(moment.plus(Duration.ofDays(30)), scheduler.maintenanceFireAt)
    }

    @Test fun theMaintenanceAlarmFollowsTheToggleAndTheDeliveryCapability() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)
        assertTrue(scheduler.isMaintenanceRegistered())

        coordinator(scheduler = scheduler, registry = registry, preferences = preferences(enabled = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
        assertNull("reminders off must clear the fallback", scheduler.maintenanceFireAt)

        coordinator(scheduler = scheduler, registry = registry, presenter = FakeNotificationPresenter(permitted = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
        assertNull("a missing notification permission must clear the fallback", scheduler.maintenanceFireAt)

        coordinator(scheduler = scheduler, registry = registry, presenter = FakeNotificationPresenter(channelReady = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
        assertNull("an unusable channel must clear the fallback", scheduler.maintenanceFireAt)

        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
        assertTrue("a recovered capability must re-arm the fallback", scheduler.isMaintenanceRegistered())
    }

    @Test fun cancelAllClearsTheMaintenanceAlarmToo() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)
        model.reconcile(ReminderReconcileReason.APP_START)
        assertTrue(scheduler.isMaintenanceRegistered())

        val cleared = model.cancelAll(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertNull(scheduler.maintenanceFireAt)
        assertTrue(cleared.failed.isEmpty())
        assertTrue(registry.state.alarms.isEmpty())
    }

    @Test fun aMaintenanceFailureIsReportedAndRetriedWithoutTouchingTheWindow() = runTest {
        val scheduler = FakeAlarmScheduler().apply { failMaintenance = true }
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)

        val failedRun = model.reconcile(ReminderReconcileReason.APP_START)

        assertTrue(failedRun.failed.contains(ReminderMaintenance.URI))
        assertEquals(2, failedRun.submitted.size)
        assertEquals("the course window must stay consistent", 2, registry.state.alarms.size)
        assertFalse(failedRun.activeAlarms.any { it.uri == ReminderMaintenance.URI })

        scheduler.failMaintenance = false
        val retried = model.reconcile(ReminderReconcileReason.WINDOW_MAINTENANCE)

        assertTrue(retried.failed.isEmpty())
        assertTrue(scheduler.isMaintenanceRegistered())

        scheduler.failMaintenanceCancel = true
        val off = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(enabled = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertTrue(off.failed.contains(ReminderMaintenance.URI))
        assertTrue(registry.state.alarms.isEmpty())
    }

    private fun preferences(enabled: Boolean = true, leadMinutes: Int = 10) =
        SchedulePreferences.defaults.copy(
            reminder = SchedulePreferences.defaults.reminder.copy(
                remindersEnabled = enabled,
                reminderLeadMinutes = leadMinutes,
            ),
        )

    private fun coordinator(
        scheduler: FakeAlarmScheduler = FakeAlarmScheduler(),
        presenter: FakeNotificationPresenter = FakeNotificationPresenter(),
        registry: FakeReminderRegistry = FakeReminderRegistry(),
        data: ScheduleData = data(),
        preferences: SchedulePreferences = preferences(),
        dataSource: (suspend () -> ScheduleData)? = null,
        preferencesSource: (suspend () -> SchedulePreferences)? = null,
        now: () -> Instant = { nowInstant },
        window: Duration = Duration.ofDays(60),
    ) = CourseReminderCoordinator(
        dataSource = dataSource ?: { data },
        preferencesSource = preferencesSource ?: { preferences },
        scheduler = scheduler,
        presenter = presenter,
        registry = registry,
        now = now,
        zone = { zone },
        window = window,
    )

    @Test fun enabledAndPermittedSchedulesTheWindowOnceAndIsIdempotent() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)

        val first = model.reconcile(ReminderReconcileReason.APP_START)

        assertTrue(first.availability.canDeliver)
        assertEquals(
            listOf("qingke://reminder/0/c1/0/s1/1/2026-03-02", "qingke://reminder/0/c1/0/s1/2/2026-03-09"),
            first.submitted.sorted(),
        )
        assertEquals(listOf(weekOne, weekTwo), scheduler.registered.values.map { it.fireAt })
        assertEquals(2, scheduler.registered.size)
        assertTrue(scheduler.registered.values.all { it.exact })
        assertFalse(first.degraded)

        val second = model.reconcile(ReminderReconcileReason.ALARM_FIRED)

        assertEquals(2, second.submitted.size)
        assertEquals(0, second.cancelled.size)
        assertEquals(2, second.unchanged.size)
        assertEquals(2, scheduler.registered.size)
        assertEquals(first.generation + 1, second.generation)
    }

    @Test fun disablingRemindersCancelsEverythingAndClearsTheRegistry() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)

        val off = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(enabled = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertEquals(2, off.cancelled.size)
        assertTrue(off.submitted.isEmpty())
        assertTrue(scheduler.registered.isEmpty())
        assertTrue(registry.state.alarms.isEmpty())
    }

    @Test fun deniedNotificationPermissionSchedulesNothingAndCancelsExisting() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)

        val presenter = FakeNotificationPresenter(permitted = false)
        val denied = coordinator(scheduler = scheduler, registry = registry, presenter = presenter)
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertFalse(denied.availability.canDeliver)
        assertTrue(denied.submitted.isEmpty())
        assertFalse(denied.notificationsPermittedOrChannelMissing())
        assertEquals(2, denied.cancelled.size)
        assertTrue(scheduler.registered.isEmpty())
    }

    @Test fun inexactCapabilityIsReportedAsDegradedAndMarkedInTheNotification() = runTest {
        val scheduler = FakeAlarmScheduler(exactAvailable = false)
        val registry = FakeReminderRegistry()
        val presenter = FakeNotificationPresenter()

        val result = coordinator(scheduler = scheduler, presenter = presenter, registry = registry)
            .reconcile(ReminderReconcileReason.EXACT_ALARM_PERMISSION_CHANGED)

        assertFalse(result.availability.exactAlarmsAvailable)
        assertTrue(result.degraded)
        assertEquals(2, result.submitted.size)
        assertTrue(scheduler.registered.values.all { !it.exact })
        assertTrue(scheduler.registered.values.all { it.body.endsWith(ReminderNotifications.INEXACT_MARKER) })
    }

    @Test fun gainingExactCapabilityReplacesTheInexactAlarms() = runTest {
        val scheduler = FakeAlarmScheduler(exactAvailable = false)
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)
        assertTrue(scheduler.registered.values.all { !it.exact })

        scheduler.exactAvailable = true
        val upgraded = coordinator(scheduler = scheduler, registry = registry)
            .reconcile(ReminderReconcileReason.EXACT_ALARM_PERMISSION_CHANGED)

        assertEquals(2, upgraded.cancelled.size)
        assertEquals(2, upgraded.submitted.size)
        assertFalse(upgraded.degraded)
        assertTrue(scheduler.registered.values.all { it.exact })
        assertTrue(scheduler.registered.values.none { it.body.endsWith(ReminderNotifications.INEXACT_MARKER) })
    }

    @Test fun changingTheLeadTimeReplacesTheAlarms() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)

        val replaced = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(leadMinutes = 0))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertEquals(2, replaced.cancelled.size)
        assertEquals(2, replaced.submitted.size)
        assertTrue(scheduler.registered.keys.all { it.contains("/1/") || it.contains("/2/") })
        assertTrue(scheduler.registered.values.all { it.fireAt == it.startAt })
    }

    @Test fun partialScheduleFailureKeepsTheOtherAlarmsAndRetriesLater() = runTest {
        val failing = "c1".let { "qingke://reminder/0/c1/0/s1/1/2026-03-02" }
        val scheduler = FakeAlarmScheduler().apply { failUris += failing }
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)

        val result = model.reconcile(ReminderReconcileReason.APP_START)

        assertEquals(1, result.submitted.size)
        assertEquals(listOf(failing), result.failed)
        assertEquals(1, registry.state.alarms.size)
        assertFalse(registry.state.alarms.any { it.uri == failing })
        // A first submission that failed is never registered, and every reported view comes from the saved set.
        assertEquals(registry.state.alarms, result.activeAlarms)
        assertEquals(1, result.activeCount)
        assertEquals(1, result.activeAlarms.map { it.uri }.toSet().size)

        scheduler.failUris.clear()
        val retry = model.reconcile(ReminderReconcileReason.MANUAL)
        assertEquals(2, retry.submitted.size)
        assertEquals(2, scheduler.registered.size)
        assertEquals(2, registry.state.alarms.size)
    }

    @Test fun unchangedResubmitFailureKeepsTheAlarmForRetryAndLaterCancel() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)
        model.reconcile(ReminderReconcileReason.APP_START)
        val failing = scheduler.registered.keys.first()

        scheduler.failUris += failing
        val failedRun = model.reconcile(ReminderReconcileReason.ALARM_FIRED)

        assertEquals(listOf(failing), failedRun.failed)
        assertEquals(2, failedRun.unchanged.size)
        // The failed re-submit keeps the previous entry: it is the only handle to a platform alarm that may
        // still exist, so a later cancel must still be able to remove it.
        assertEquals(2, registry.state.alarms.size)
        assertTrue(registry.state.alarms.any { it.uri == failing })
        assertEquals(registry.state.alarms, failedRun.activeAlarms)
        assertEquals(2, failedRun.activeCount)
        assertFalse(failedRun.degraded)

        scheduler.failUris.clear()
        val retried = model.reconcile(ReminderReconcileReason.MANUAL)
        assertEquals(2, retried.submitted.size)
        assertEquals(2, registry.state.alarms.size)

        val off = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(enabled = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
        assertEquals(2, off.cancelled.size)
        assertTrue(scheduler.registered.isEmpty())
        assertTrue(registry.state.alarms.isEmpty())
    }

    @Test fun failedCancelWithFailedResubmitKeepsThePreviousEntry() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)
        val previous = registry.state.alarms
        val uris = previous.map { it.uri }

        scheduler.failCancelUris += uris
        scheduler.failUris += uris
        val result = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(leadMinutes = 0))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertEquals(0, result.cancelled.size)
        assertEquals(0, result.submitted.size)
        assertEquals(uris.toSet(), result.failed.toSet())
        assertEquals(uris.size, result.failed.size)
        assertEquals(previous, result.activeAlarms)
        assertEquals(registry.state.alarms, result.activeAlarms)
        assertEquals(2, result.activeCount)
    }

    @Test fun cancelledPreviousEntryIsDroppedWhenTheResubmitFails() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)
        val uris = registry.state.alarms.map { it.uri }

        scheduler.failUris += uris
        val result = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(leadMinutes = 0))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertEquals(uris.size, result.cancelled.size)
        assertEquals(0, result.submitted.size)
        assertEquals(uris.toSet(), result.failed.toSet())
        // The previous alarms really were cancelled, so their entries must not survive as stale handles.
        assertTrue(result.activeAlarms.isEmpty())
        assertEquals(0, result.activeCount)
        assertTrue(registry.state.alarms.isEmpty())
        assertTrue(scheduler.registered.isEmpty())
    }

    @Test fun duplicateRegistryEntriesCollapseIntoOneActiveAlarm() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)
        val planned = model.reconcile(ReminderReconcileReason.APP_START).activeAlarms
        registry.state = ReminderRegistryState(registry.state.generation, planned + planned)

        val result = model.reconcile(ReminderReconcileReason.MANUAL)

        assertEquals(planned.size, result.activeCount)
        assertEquals(planned.map { it.uri }, result.activeAlarms.map { it.uri })
        assertEquals(planned, registry.state.alarms)
    }

    @Test fun cancelAllPartialFailureReportsTheRemainingActiveAlarms() = runTest {
        val scheduler = FakeAlarmScheduler(exactAvailable = false)
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)
        model.reconcile(ReminderReconcileReason.APP_START)
        val remaining = registry.state.alarms.last()
        scheduler.failCancelUris += remaining.uri

        val partial = model.cancelAll(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertEquals(1, partial.cancelled.size)
        assertEquals(listOf(remaining.uri), partial.failed)
        assertEquals(listOf(remaining), partial.activeAlarms)
        assertEquals(1, partial.activeCount)
        assertTrue("the retained alarm is inexact, so delivery stays degraded", partial.degraded)
        assertEquals(registry.state.alarms, partial.activeAlarms)
        assertEquals(1, scheduler.registered.size)

        scheduler.failCancelUris.clear()
        val cleared = model.cancelAll(ReminderReconcileReason.PREFERENCES_CHANGED)
        assertEquals(1, cleared.cancelled.size)
        assertTrue(cleared.activeAlarms.isEmpty())
        assertEquals(0, cleared.activeCount)
        assertTrue(registry.state.alarms.isEmpty())
        assertTrue(scheduler.registered.isEmpty())
    }

    @Test fun cancelFailureKeepsTheAlarmRegisteredForTheNextRun() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)
        scheduler.failCancelUris += scheduler.registered.keys.toList()

        val failed = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(enabled = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)

        assertEquals(2, failed.failed.size)
        assertEquals(2, registry.state.alarms.size)
        assertEquals(2, scheduler.registered.size)

        scheduler.failCancelUris.clear()
        val cleared = coordinator(scheduler = scheduler, registry = registry, preferences = preferences(enabled = false))
            .reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
        assertEquals(2, cleared.cancelled.size)
        assertTrue(scheduler.registered.isEmpty())
        assertTrue(registry.state.alarms.isEmpty())
    }

    @Test fun supersededRunDoesNotTouchThePlatformOrTheRegistry() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry().apply { bumpGenerationOnLoad = 7L }
        val model = coordinator(scheduler = scheduler, registry = registry)

        val result = model.reconcile(ReminderReconcileReason.APP_START)

        assertTrue(result.superseded)
        assertTrue(result.submitted.isEmpty())
        assertTrue(result.cancelled.isEmpty())
        assertTrue(scheduler.registered.isEmpty())
        assertEquals(7L, registry.state.generation)
        assertTrue(registry.state.alarms.isEmpty())
    }

    @Test fun concurrentReconcilesAreSerialisedWithoutDuplicatingAlarms() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)

        val first = async { model.reconcile(ReminderReconcileReason.APP_START) }
        val second = async { model.reconcile(ReminderReconcileReason.DATA_SAVED) }
        val results = listOf(first.await(), second.await())

        assertEquals(2, scheduler.registered.size)
        assertEquals(2, registry.state.alarms.size)
        assertEquals(listOf(2, 2), results.map { it.submitted.size })
        assertEquals(listOf(0, 2), results.map { it.unchanged.size }.sorted())
        assertEquals(2L, registry.state.generation)
    }

    @Test fun rebuildResubmitsEveryExpectedAlarmAfterThePlatformLostThem() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.APP_START)
        assertEquals(2, scheduler.registered.size)

        // A reboot drops the platform alarms while the persisted registry still lists them.
        scheduler.registered.clear()

        val rebuilt = coordinator(scheduler = scheduler, registry = registry).reconcile(ReminderReconcileReason.BOOT_COMPLETED)

        assertEquals(2, rebuilt.submitted.size)
        assertEquals(2, scheduler.registered.size)
        assertEquals(2, registry.state.alarms.size)
    }

    @Test fun everyRebuildReasonResubmitsTheExpectedAlarmsIdempotently() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)

        listOf(
            ReminderReconcileReason.APP_START,
            ReminderReconcileReason.BOOT_COMPLETED,
            ReminderReconcileReason.PACKAGE_REPLACED,
            ReminderReconcileReason.TIME_CHANGED,
            ReminderReconcileReason.EXACT_ALARM_PERMISSION_CHANGED,
        ).forEach { reason ->
            scheduler.registered.clear()
            val reconciliation = model.reconcile(reason)
            assertEquals(reason.toString(), 2, reconciliation.submitted.size)
            assertEquals(reason.toString(), 2, scheduler.registered.size)
            assertEquals(2, registry.state.alarms.size)
        }
    }

    @Test fun degradedReflectsActiveInexactAlarmsAcrossRuns() = runTest {
        val scheduler = FakeAlarmScheduler(exactAvailable = false)
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry)

        assertTrue(model.reconcile(ReminderReconcileReason.APP_START).degraded)

        // Nothing actually changes between the two runs, so this is the retained-inexact case.
        val second = model.reconcile(ReminderReconcileReason.ALARM_FIRED)

        assertEquals(2, second.unchanged.size)
        assertTrue("degraded must consider every active reminder", second.degraded)
        assertTrue(second.activeAlarms.isNotEmpty())
        assertTrue(second.activeAlarms.all { !it.exact })
    }

    @Test fun unreadableDataLeavesThePlatformAndRegistryUntouched() = runTest {
        val scheduler = FakeAlarmScheduler()
        val registry = FakeReminderRegistry()
        val model = coordinator(scheduler = scheduler, registry = registry, dataSource = { error("store unavailable") })

        val failure = runCatching { model.reconcile(ReminderReconcileReason.DATA_SAVED) }

        assertTrue(failure.isFailure)
        assertTrue(scheduler.registered.isEmpty())
        assertEquals(0L, registry.state.generation)
        assertTrue(registry.state.alarms.isEmpty())
    }

    @Test fun planForDeliveryUsesThePayloadFireInstantAsItsBoundary() = runTest {
        val model = coordinator()

        val plan = model.planForDelivery(
            ReminderAlarm(
                identity = CourseReminderIdentity(0, 0, "c1", "s1", 1, java.time.LocalDate.parse("2026-03-02"), false),
                fireAt = weekOne,
                startAt = Instant.parse("2026-03-02T00:00:00Z"),
                title = "高等数学",
                body = "08:00–08:45",
                exact = true,
            ),
        )

        assertNotNull(plan.firstOrNull { it.identity.uri == "qingke://reminder/0/c1/0/s1/1/2026-03-02" })
        assertEquals(2, plan.size)
    }

    @Test fun deliverPostsTheFreshContentForAValidPayload() = runTest {
        val presenter = FakeNotificationPresenter()
        val model = coordinator(presenter = presenter)
        val payload = payloadFor(weekOne)

        val outcome = model.deliver(payload, weekOne)

        assertTrue(outcome is ReminderDelivery.Delivered)
        assertEquals(1, presenter.posted.size)
        assertEquals(payload.uri, presenter.posted.single().uri)
        assertEquals("高等数学", presenter.posted.single().title)
    }

    @Test fun deliverSuppressesAPayloadFromAnOldTimetable() = runTest {
        val presenter = FakeNotificationPresenter()
        val model = coordinator(presenter = presenter, data = data(emptyList()))
        val payload = payloadFor(weekOne)

        val outcome = model.deliver(payload, weekOne)

        assertTrue(outcome is ReminderDelivery.Suppressed)
        assertTrue(presenter.posted.isEmpty())
    }

    @Test fun deliverSuppressesAPayloadWhoseFireTimeMoved() = runTest {
        val presenter = FakeNotificationPresenter()
        val model = coordinator(presenter = presenter)
        val payload = payloadFor(weekOne.minusSeconds(600))

        val outcome = model.deliver(payload, weekOne)

        assertEquals("fire time changed", (outcome as ReminderDelivery.Suppressed).reason)
        assertTrue(presenter.posted.isEmpty())
    }

    @Test fun deliverReportsAFailedPostWithoutThrowing() = runTest {
        val presenter = FakeNotificationPresenter().apply { failNextPost = true }
        val model = coordinator(presenter = presenter)

        val outcome = model.deliver(payloadFor(weekOne), weekOne)

        assertTrue(outcome is ReminderDelivery.Failed)
    }

    @Test fun deliverSuppressesWhenRemindersAreDisabled() = runTest {
        val presenter = FakeNotificationPresenter()
        val model = coordinator(presenter = presenter, preferences = preferences(enabled = false))

        val outcome = model.deliver(payloadFor(weekOne), weekOne)

        assertEquals("reminders disabled", (outcome as ReminderDelivery.Suppressed).reason)
        assertTrue(presenter.posted.isEmpty())
    }

    @Test fun deliverSuppressesWhenNotificationsAreDenied() = runTest {
        val presenter = FakeNotificationPresenter(permitted = false)
        val model = coordinator(presenter = presenter)

        val outcome = model.deliver(payloadFor(weekOne), weekOne)

        assertEquals("notifications not permitted", (outcome as ReminderDelivery.Suppressed).reason)
        assertTrue(presenter.posted.isEmpty())
    }

    @Test fun deliverSuppressesWhenTheReminderChannelIsUnusable() = runTest {
        val presenter = FakeNotificationPresenter(channelReady = false)
        val model = coordinator(presenter = presenter)

        val outcome = model.deliver(payloadFor(weekOne), weekOne)

        assertEquals("reminder channel unusable", (outcome as ReminderDelivery.Suppressed).reason)
        assertTrue(presenter.posted.isEmpty())
    }

    @Test fun aSilentNonPostIsReportedAsFailedNotDelivered() = runTest {
        val presenter = FakeNotificationPresenter().apply { published = false }
        val model = coordinator(presenter = presenter)

        val outcome = model.deliver(payloadFor(weekOne), weekOne)

        assertTrue(outcome is ReminderDelivery.Failed)
        assertTrue(presenter.posted.isEmpty())
    }

    private fun payloadFor(fireAt: Instant) = ReminderAlarm(

        identity = CourseReminderIdentity(0, 0, "c1", "s1", 1, java.time.LocalDate.parse("2026-03-02"), false),
        fireAt = fireAt,
        startAt = Instant.parse("2026-03-02T00:00:00Z"),
        title = "高等数学",
        body = "08:00–08:45",
        exact = true,
    )

    private fun ReminderReconciliation.notificationsPermittedOrChannelMissing(): Boolean =
        availability.notificationsPermitted && availability.channelReady
}

private class FakeAlarmScheduler(var exactAvailable: Boolean = true) : AlarmScheduler {
    val registered = linkedMapOf<String, ReminderAlarm>()
    val cancelled = mutableListOf<String>()
    val failUris = mutableSetOf<String>()
    val failCancelUris = mutableSetOf<String>()
    var maintenanceFireAt: Instant? = null
    var failMaintenance = false
    var failMaintenanceCancel = false

    override fun canScheduleExactAlarms(): Boolean = exactAvailable

    override fun schedule(alarm: ReminderAlarm) {
        if (alarm.uri in failUris) error("schedule failed")
        registered[alarm.uri] = alarm
    }

    override fun cancel(uri: String) {
        if (uri in failCancelUris) error("cancel failed")
        cancelled += uri
        registered.remove(uri)
    }

    override fun isRegistered(uri: String): Boolean = uri in registered

    override fun scheduleMaintenance(fireAt: Instant) {
        if (failMaintenance) error("maintenance schedule failed")
        maintenanceFireAt = fireAt
    }

    override fun cancelMaintenance() {
        if (failMaintenanceCancel) error("maintenance cancel failed")
        maintenanceFireAt = null
    }

    override fun isMaintenanceRegistered(): Boolean = maintenanceFireAt != null
}

private class FakeNotificationPresenter(
    var permitted: Boolean = true,
    var channelReady: Boolean = true,
) : NotificationPresenter {
    var channelEnsures = 0
    var failNextPost = false
    var published = true
    val posted = mutableListOf<ReminderAlarm>()

    override fun ensureChannel(): Boolean {
        channelEnsures++
        return channelReady
    }

    override fun isChannelReady(): Boolean = channelReady

    override fun areNotificationsPermitted(): Boolean = permitted

    override fun notify(alarm: ReminderAlarm): Boolean {
        if (failNextPost) error("notification post failed")
        if (!published) return false
        posted += alarm
        return true
    }

    override fun cancelNotification(uri: String) = Unit
}

private class FakeReminderRegistry(initial: ReminderRegistryState = ReminderRegistryState()) : ReminderRegistry {
    var state = initial
    var loads = 0
    var bumpGenerationOnLoad: Long? = null

    override suspend fun load(): ReminderRegistryState {
        loads++
        if (loads == 2 && bumpGenerationOnLoad != null) state = state.copy(generation = bumpGenerationOnLoad!!)
        return state
    }

    override suspend fun save(state: ReminderRegistryState): ReminderRegistryState = state.also { this.state = it }
}
