package com.qingke.schedule.reminder

import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.preferences.SchedulePreferences
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A08: why a reconciliation ran; it is reported back so later batches can surface the last reason. */
enum class ReminderReconcileReason {
    APP_START,
    ALARM_FIRED,

    /** A08 third batch: the internal window fallback fired and asked for one re-plan. */
    WINDOW_MAINTENANCE,
    DATA_SAVED,
    PREFERENCES_CHANGED,
    BOOT_COMPLETED,
    PACKAGE_REPLACED,
    TIME_CHANGED,
    EXACT_ALARM_PERMISSION_CHANGED,
    MANUAL,
}

/**
 * A08: outcome of one reconciliation. [superseded] means a newer run advanced the registry first, so this run
 * deliberately did not touch the platform or the registry.
 *
 * [submitted] is every expected alarm handed to the platform in this run: a rebuild always re-submits the whole
 * expected set instead of trusting the persisted registry, because the registry says what the app *wants*
 * registered, not that AlarmManager still holds it (reboot, package replace, time change and process death all
 * drop the alarms).
 *
 * [failed] lists, once each, the identities whose platform call failed in this run and therefore need a retry. It
 * may also carry [ReminderMaintenance.URI], the internal window fallback, which is not a course reminder.
 * [activeAlarms] is the saved registry content and the only source of [activeCount] and [degraded]: a failed
 * re-submit of an already registered alarm keeps its previous entry (so a later cancel can still remove the
 * platform alarm), while a failed first submission is never registered.
 */
data class ReminderReconciliation(
    val reason: ReminderReconcileReason,
    val generation: Long,
    val remindersEnabled: Boolean,
    val availability: ReminderAvailability,
    val submitted: List<String> = emptyList(),
    val cancelled: List<String> = emptyList(),
    val unchanged: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val activeAlarms: List<ReminderAlarm> = emptyList(),
    val superseded: Boolean = false,
) {
    /** True when any currently active reminder is inexact, i.e. delivery may be delayed (D03). */
    val degraded: Boolean get() = activeAlarms.any { !it.exact }

    val activeCount: Int get() = activeAlarms.size
}

/**
 * A08 second batch: the read-only status the settings page renders. [activeAlarms] is exactly the persisted
 * registry content, so the reported count and degradation match what a later cancel would work with.
 */
data class ReminderStatusSnapshot(
    val availability: ReminderAvailability,
    val activeAlarms: List<ReminderAlarm>,
) {
    val activeCount: Int get() = activeAlarms.size

    val degraded: Boolean get() = activeAlarms.any { !it.exact }
}

/**
 * A08 second batch: the reminder operations the settings UI needs. [CourseReminderCoordinator] is the production
 * implementation; the ViewModel depends on this seam so its state can be tested without a platform.
 */
interface ReminderControl {
    suspend fun snapshot(): ReminderStatusSnapshot

    suspend fun reconcile(reason: ReminderReconcileReason): ReminderReconciliation

    suspend fun cancelAll(reason: ReminderReconcileReason): ReminderReconciliation
}

/** A08: outcome of delivering one fired payload. */
sealed interface ReminderDelivery {
    data class Delivered(val alarm: ReminderAlarm) : ReminderDelivery

    data class Suppressed(val reason: String) : ReminderDelivery

    data class Failed(val message: String) : ReminderDelivery
}

/**
 * A08: the single writer that keeps the platform alarms equal to the committed schedule. It serialises every
 * run (mutex), guards the side effects with the registry generation, keeps reminders and registry coherent when
 * a single platform call fails, and never touches the schedule or the preference store: a failed reconcile
 * leaves both untouched and is simply retried by the next entry point.
 */
class CourseReminderCoordinator(
    private val dataSource: suspend () -> ScheduleData,
    private val preferencesSource: suspend () -> SchedulePreferences,
    private val scheduler: AlarmScheduler,
    private val presenter: NotificationPresenter,
    private val registry: ReminderRegistry,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val window: Duration = CourseReminderPlanner.DEFAULT_WINDOW,
    private val limit: Int = CourseReminderPlanner.DEFAULT_LIMIT,
) : ReminderControl {
    private val mutex = Mutex()

    /**
     * Reads the current capabilities and the persisted registry content without creating the channel and
     * without writing anything, so the settings page can render the real state before reminders are enabled.
     */
    override suspend fun snapshot(): ReminderStatusSnapshot = mutex.withLock {
        ReminderStatusSnapshot(
            availability = ReminderAvailability(
                notificationsPermitted = runCatching { presenter.areNotificationsPermitted() }.getOrDefault(false),
                channelReady = runCatching { presenter.isChannelReady() }.getOrDefault(false),
                exactAlarmsAvailable = runCatching { scheduler.canScheduleExactAlarms() }.getOrDefault(false),
            ),
            activeAlarms = registry.load().alarms,
        )
    }

    suspend fun availability(): ReminderAvailability {
        val channelReady = runCatching { presenter.ensureChannel() }.getOrDefault(false)
        val permitted = runCatching { presenter.areNotificationsPermitted() }.getOrDefault(false)
        val exact = runCatching { scheduler.canScheduleExactAlarms() }.getOrDefault(false)
        return ReminderAvailability(permitted, channelReady, exact)
    }

    /** Plans the rolling window (or clears everything when reminders are off or cannot be delivered). */
    override suspend fun reconcile(reason: ReminderReconcileReason): ReminderReconciliation = mutex.withLock {
        val availability = availability()
        val preferences = preferencesSource()
        val data = dataSource()
        val moment = now()
        val startState = registry.load()

        val desired = desiredAlarms(data, preferences, availability, moment)
        val current = startState.alarms
        val currentByUri = current.associateBy { it.uri }
        val desiredByUri = desired.associateBy { it.uri }
        val stale = current.filter { existing -> desiredByUri[existing.uri] != existing }
        val unchanged = desired.filter { candidate -> currentByUri[candidate.uri] == candidate }

        if (registry.load().generation != startState.generation) {
            return ReminderReconciliation(
                reason = reason,
                generation = startState.generation,
                remindersEnabled = preferences.reminder.remindersEnabled,
                availability = availability,
                activeAlarms = current,
                superseded = true,
            )
        }

        val cancelled = mutableListOf<String>()
        val cancelFailed = mutableListOf<String>()
        stale.forEach { alarm ->
            runCatching { scheduler.cancel(alarm.uri) }.fold(
                onSuccess = { cancelled += alarm.uri },
                onFailure = { cancelFailed += alarm.uri },
            )
        }
        // Every expected alarm is (re)submitted: AlarmManager replaces an alarm with the same identity, so this
        // is idempotent and it repairs a platform that lost its alarms without changing the registry.
        val submitted = mutableListOf<String>()
        val submitFailed = mutableListOf<String>()
        desired.forEach { alarm ->
            runCatching { scheduler.schedule(alarm) }.fold(
                onSuccess = { submitted += alarm.uri },
                onFailure = { submitFailed += alarm.uri },
            )
        }

        // The final active set is the only source for the registry, activeAlarms, activeCount and degraded, so
        // the persisted state can never disagree with what the platform is believed to hold.
        val cancelledUris = cancelled.toSet()
        val cancelFailedUris = cancelFailed.toSet()
        val unchangedUris = unchanged.map { it.uri }.toSet()
        val active = mutableListOf<ReminderAlarm>()
        desired.forEach { alarm ->
            when {
                alarm.uri !in submitFailed -> active += alarm
                // An unchanged alarm was already registered: a failed re-submit keeps the previous entry, which
                // is the only handle to a platform alarm that may still exist and must stay cancellable.
                alarm.uri in unchangedUris -> currentByUri[alarm.uri]?.let { active += it }
                // The previous entry for this identity was really cancelled, so nothing may be left behind.
                alarm.uri in cancelledUris -> Unit
                // Cancelling the previous entry failed as well, so keep it for the next retry.
                alarm.uri in cancelFailedUris -> currentByUri[alarm.uri]?.let { active += it }
                // A first submission that failed is never registered: there is nothing to cancel yet.
            }
        }
        // Identities that are no longer expected stay registered only while their cancel keeps failing.
        stale.forEach { previous ->
            if (desiredByUri[previous.uri] == null && previous.uri in cancelFailedUris) active += previous
        }
        val finalActive = active.distinctBy { it.uri }.sortedBy { it.fireAt }
        // A08 third batch: the window fallback is re-armed (or cleared) in the same pass, but it never enters the
        // registry, so it cannot change the active count or the degraded flag.
        val maintenanceFailed = runCatching { maintainWindowFallback(preferences, availability, moment) }.isFailure
        val failed = (cancelFailed + submitFailed + if (maintenanceFailed) listOf(ReminderMaintenance.URI) else emptyList())
            .distinct()
        val saved = registry.save(ReminderRegistryState(startState.generation + 1, finalActive))

        ReminderReconciliation(
            reason = reason,
            generation = saved.generation,
            remindersEnabled = preferences.reminder.remindersEnabled,
            availability = availability,
            submitted = submitted,
            cancelled = cancelled,
            unchanged = unchanged.map { it.uri },
            failed = failed,
            activeAlarms = saved.alarms,
        )
    }

    /** Cancels every registered alarm and clears the registry, e.g. when the user turns reminders off. */
    override suspend fun cancelAll(reason: ReminderReconcileReason): ReminderReconciliation = mutex.withLock {
        val availability = availability()
        val startState = registry.load()
        if (registry.load().generation != startState.generation) {
            return ReminderReconciliation(reason, startState.generation, false, availability, superseded = true)
        }
        val cancelled = mutableListOf<String>()
        val failed = mutableListOf<String>()
        startState.alarms.forEach { alarm ->
            runCatching { scheduler.cancel(alarm.uri) }.fold(
                onSuccess = { cancelled += alarm.uri },
                onFailure = { failed += alarm.uri },
            )
        }
        val failedAlarms = failed.distinct()
        val failedUris = failedAlarms.toSet()
        val remaining = startState.alarms.filter { it.uri in failedUris }.distinctBy { it.uri }
        val saved = registry.save(ReminderRegistryState(startState.generation + 1, remaining))
        // Turning reminders off also clears the window fallback; a failure is reported like any other cancel.
        val maintenanceFailed = runCatching { scheduler.cancelMaintenance() }.isFailure
        ReminderReconciliation(
            reason = reason,
            generation = saved.generation,
            remindersEnabled = false,
            availability = availability,
            cancelled = cancelled,
            failed = (failedAlarms + if (maintenanceFailed) listOf(ReminderMaintenance.URI) else emptyList()).distinct(),
            // Alarms whose cancel failed are still registered, so the reported active set must match the registry.
            activeAlarms = saved.alarms,
        )
    }

    /**
     * A08 third batch: keeps exactly one window fallback while reminders are on and deliverable, and clears it
     * otherwise. Re-scheduling the same identity is idempotent, so every rebuild, save, trigger or capability
     * recovery renews the fallback - including while the current window holds no course at all.
     */
    private fun maintainWindowFallback(
        preferences: SchedulePreferences,
        availability: ReminderAvailability,
        moment: Instant,
    ) {
        if (preferences.reminder.remindersEnabled && availability.canDeliver) {
            scheduler.scheduleMaintenance(ReminderMaintenance.nextFireAt(moment, window))
        } else {
            scheduler.cancelMaintenance()
        }
    }

    /**
     * A08: posts one fired reminder. Delivery is only attempted when reminders are still enabled, notifications
     * are permitted and the channel is usable, and the payload is re-validated against the currently committed
     * data as of the payload's own fire instant. A silent non-post is never reported as delivered.
     */
    suspend fun deliver(payload: ReminderAlarm, moment: Instant = now()): ReminderDelivery {
        val preferences = preferencesSource()
        if (!preferences.reminder.remindersEnabled) return ReminderDelivery.Suppressed("reminders disabled")
        val availability = availability()
        if (!availability.notificationsPermitted) return ReminderDelivery.Suppressed("notifications not permitted")
        if (!availability.channelReady) return ReminderDelivery.Suppressed("reminder channel unusable")

        return when (val decision = CourseReminderDelivery.decide(payload, planForDelivery(payload), moment)) {
            is CourseReminderDelivery.Decision.Deliver -> {
                val alarm = ReminderAlarm.from(
                    reminder = decision.reminder,
                    exact = payload.exact,
                    body = ReminderNotifications.bodyFor(decision.reminder.body, payload.exact),
                )
                val posted = runCatching { presenter.notify(alarm) }
                when {
                    posted.isFailure -> ReminderDelivery.Failed(posted.exceptionOrNull()?.message ?: "notify failed")
                    posted.getOrDefault(false) -> ReminderDelivery.Delivered(alarm)
                    else -> ReminderDelivery.Failed("notification was not published")
                }
            }
            is CourseReminderDelivery.Decision.Suppress -> ReminderDelivery.Suppressed(decision.reason)
        }
    }

    /** The plan a fired payload must still match, evaluated as of that payload's own fire instant. */
    suspend fun planForDelivery(payload: ReminderAlarm): List<CourseReminder> {
        val preferences = preferencesSource()
        return CourseReminderPlanner.plan(
            data = dataSource(),
            academicCalendar = preferences.academicCalendar,
            leadMinutes = preferences.reminder.reminderLeadMinutes,
            now = payload.fireAt.minusMillis(1),
            zone = zone(),
            window = window,
            limit = limit,
        )
    }

    private fun desiredAlarms(
        data: ScheduleData,
        preferences: SchedulePreferences,
        availability: ReminderAvailability,
        moment: Instant,
    ): List<ReminderAlarm> {
        if (!preferences.reminder.remindersEnabled || !availability.canDeliver) return emptyList()
        return CourseReminderPlanner.plan(
            data = data,
            academicCalendar = preferences.academicCalendar,
            leadMinutes = preferences.reminder.reminderLeadMinutes,
            now = moment,
            zone = zone(),
            window = window,
            limit = limit,
        ).map { reminder ->
            ReminderAlarm.from(
                reminder = reminder,
                exact = availability.exactAlarmsAvailable,
                body = ReminderNotifications.bodyFor(reminder.body, availability.exactAlarmsAvailable),
            )
        }
    }
}
