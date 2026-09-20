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
) {
    private val mutex = Mutex()

    suspend fun availability(): ReminderAvailability {
        val channelReady = runCatching { presenter.ensureChannel() }.getOrDefault(false)
        val permitted = runCatching { presenter.areNotificationsPermitted() }.getOrDefault(false)
        val exact = runCatching { scheduler.canScheduleExactAlarms() }.getOrDefault(false)
        return ReminderAvailability(permitted, channelReady, exact)
    }

    /** Plans the rolling window (or clears everything when reminders are off or cannot be delivered). */
    suspend fun reconcile(reason: ReminderReconcileReason): ReminderReconciliation = mutex.withLock {
        val availability = availability()
        val preferences = preferencesSource()
        val data = dataSource()
        val moment = now()
        val startState = registry.load()

        val desired = desiredAlarms(data, preferences, availability, moment)
        val current = startState.alarms
        val desiredByUri = desired.associateBy { it.uri }
        val stale = current.filter { existing -> desiredByUri[existing.uri] != existing }
        val unchanged = desired.filter { candidate -> current.any { it == candidate } }

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
        val failed = mutableListOf<String>()
        stale.forEach { alarm ->
            runCatching { scheduler.cancel(alarm.uri) }.fold(
                onSuccess = { cancelled += alarm.uri },
                onFailure = { failed += alarm.uri },
            )
        }
        // Every expected alarm is (re)submitted: AlarmManager replaces an alarm with the same identity, so this
        // is idempotent and it repairs a platform that lost its alarms without changing the registry.
        val submitted = mutableListOf<String>()
        desired.forEach { alarm ->
            runCatching { scheduler.schedule(alarm) }.fold(
                onSuccess = { submitted += alarm.uri },
                onFailure = { failed += alarm.uri },
            )
        }

        val active = desired.filter { it.uri in submitted } +
            stale.filter { it.uri in failed }.mapNotNull { previous -> current.firstOrNull { it.uri == previous.uri } }
        val saved = registry.save(ReminderRegistryState(startState.generation + 1, active.sortedBy { it.fireAt }))

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
    suspend fun cancelAll(reason: ReminderReconcileReason): ReminderReconciliation = mutex.withLock {
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
        val saved = registry.save(
            ReminderRegistryState(startState.generation + 1, startState.alarms.filter { it.uri in failed }),
        )
        ReminderReconciliation(
            reason = reason,
            generation = saved.generation,
            remindersEnabled = false,
            availability = availability,
            cancelled = cancelled,
            failed = failed,
        )
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
