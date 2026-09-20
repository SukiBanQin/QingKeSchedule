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
 */
data class ReminderReconciliation(
    val reason: ReminderReconcileReason,
    val generation: Long,
    val remindersEnabled: Boolean,
    val availability: ReminderAvailability,
    val scheduled: List<String> = emptyList(),
    val cancelled: List<String> = emptyList(),
    val retained: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val superseded: Boolean = false,
) {
    /** A plan that only registered inexact alarms, i.e. delivery may be delayed (D03). */
    val degraded: Boolean get() = scheduled.isNotEmpty() && !availability.exactAlarmsAvailable

    val activeCount: Int get() = retained.size + scheduled.size
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
        val cancelTargets = current.filter { existing -> desiredByUri[existing.uri] != existing }
        val retained = desired.filter { candidate -> current.any { it == candidate } }
        val scheduleTargets = desired.filterNot { candidate -> current.any { it == candidate } }

        if (registry.load().generation != startState.generation) {
            return ReminderReconciliation(
                reason = reason,
                generation = startState.generation,
                remindersEnabled = preferences.reminder.remindersEnabled,
                availability = availability,
                superseded = true,
            )
        }

        val cancelled = mutableListOf<String>()
        val failed = mutableListOf<String>()
        cancelTargets.forEach { alarm ->
            runCatching { scheduler.cancel(alarm.uri) }.fold(
                onSuccess = { cancelled += alarm.uri },
                onFailure = { failed += alarm.uri },
            )
        }
        val scheduled = mutableListOf<String>()
        scheduleTargets.forEach { alarm ->
            runCatching { scheduler.schedule(alarm) }.fold(
                onSuccess = { scheduled += alarm.uri },
                onFailure = { failed += alarm.uri },
            )
        }

        val next = retained +
            scheduleTargets.filter { it.uri in scheduled } +
            cancelTargets.filter { it.uri in failed }
        val saved = registry.save(ReminderRegistryState(startState.generation + 1, next.sortedBy { it.fireAt }))

        ReminderReconciliation(
            reason = reason,
            generation = saved.generation,
            remindersEnabled = preferences.reminder.remindersEnabled,
            availability = availability,
            scheduled = scheduled,
            cancelled = cancelled,
            retained = retained.map { it.uri },
            failed = failed,
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
     * A08: posts one fired reminder. The payload is re-validated against the currently committed data first, so
     * a notification can never surface an occurrence that an older timetable produced.
     */
    suspend fun deliver(payload: ReminderAlarm, moment: Instant = now()): ReminderDelivery {
        val plan = planForDelivery(payload)
        return when (val decision = CourseReminderDelivery.decide(payload, plan, moment)) {
            is CourseReminderDelivery.Decision.Deliver -> {
                val alarm = ReminderAlarm.from(
                    reminder = decision.reminder,
                    exact = payload.exact,
                    body = ReminderNotifications.bodyFor(decision.reminder.body, payload.exact),
                )
                runCatching { presenter.notify(alarm) }.fold(
                    onSuccess = { ReminderDelivery.Delivered(alarm) },
                    onFailure = { ReminderDelivery.Failed(it.message ?: "notify failed") },
                )
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
