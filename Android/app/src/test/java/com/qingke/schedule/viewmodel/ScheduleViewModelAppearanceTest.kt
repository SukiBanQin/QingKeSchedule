package com.qingke.schedule.viewmodel

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import com.qingke.schedule.preferences.ReminderPreferences
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.reminder.ReminderAvailability
import com.qingke.schedule.reminder.ReminderControl
import com.qingke.schedule.reminder.ReminderReconcileReason
import com.qingke.schedule.reminder.ReminderReconciliation
import com.qingke.schedule.reminder.ReminderStatusSnapshot
import com.qingke.schedule.state.ScheduleAppState
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P3-09／A11: the single appearance write path. Selecting the current value writes nothing, a committed write
 * publishes only the new mode, and a failure or a cancellation keeps the last committed mode plus the existing
 * global error. Appearance never reconciles reminders and never touches the schedule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelAppearanceTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun selectingTheModeThatIsAlreadyCommittedWritesNothing() = runTest {
        val preferences = AppearancePreferencesRepository(preferences(appearanceMode = AppearanceMode.DARK))
        val repository = AppearanceScheduleRepository(seededSchedule())
        val reminders = AppearanceReminderControl()
        val model = model(repository, preferences, reminders)
        advanceUntilIdle()

        model.setAppearanceMode(AppearanceMode.DARK)
        advanceUntilIdle()

        assertEquals(0, preferences.saveCalls)
        assertEquals(AppearanceMode.DARK, model.state.value.preferences.appearanceMode)
        assertNull(model.state.value.error)
        assertTrue(reminders.reconcileReasons.isEmpty())
    }

    @Test
    fun aCommittedSelectionPublishesOnlyTheAppearanceMode() = runTest {
        val stored = preferences(appearanceMode = AppearanceMode.SYSTEM)
        val preferences = AppearancePreferencesRepository(stored)
        val repository = AppearanceScheduleRepository(seededSchedule())
        val reminders = AppearanceReminderControl()
        val model = model(repository, preferences, reminders)
        advanceUntilIdle()
        val originalData = repository.data

        model.setAppearanceMode(AppearanceMode.LIGHT)
        advanceUntilIdle()

        assertEquals(1, preferences.saveCalls)
        assertEquals(stored.copy(appearanceMode = AppearanceMode.LIGHT), preferences.preferences)
        assertEquals(stored.copy(appearanceMode = AppearanceMode.LIGHT), model.state.value.preferences)
        assertEquals(stored.reminder, model.state.value.preferences.reminder)
        assertEquals(stored.academicCalendar, model.state.value.preferences.academicCalendar)
        assertEquals(AppearanceMode.LIGHT, model.state.value.preferences.appearanceMode)
        assertNull(model.state.value.error)
        assertEquals(false, model.state.value.isSaving)
        assertEquals(originalData, repository.data)
        assertEquals(originalData, model.state.value.data)
        assertEquals(0, repository.scheduleWrites)
        assertTrue("appearance must never reconcile reminders", reminders.reconcileReasons.isEmpty())
    }

    @Test
    fun aFailedWriteKeepsTheLastCommittedModeAndReportsTheExistingError() = runTest {
        val stored = preferences(appearanceMode = AppearanceMode.LIGHT)
        val preferences = AppearancePreferencesRepository(stored).also { it.failSave = true }
        val repository = AppearanceScheduleRepository(seededSchedule())
        val reminders = AppearanceReminderControl()
        val model = model(repository, preferences, reminders)
        advanceUntilIdle()
        val originalData = repository.data

        model.setAppearanceMode(AppearanceMode.DARK)
        advanceUntilIdle()

        assertEquals(1, preferences.saveCalls)
        assertEquals("the failed write must not replace the stored value", stored, preferences.preferences)
        assertEquals(AppearanceMode.LIGHT, model.state.value.preferences.appearanceMode)
        assertEquals(stored, model.state.value.preferences)
        assertEquals(false, model.state.value.isSaving)
        assertNotNull(model.state.value.error)
        assertTrue(model.state.value.error.orEmpty().isNotBlank())
        assertEquals(originalData, model.state.value.data)
        assertEquals(0, repository.scheduleWrites)
        assertTrue(reminders.reconcileReasons.isEmpty())
    }

    @Test
    fun aRetryAfterAFailedSelectionCommitsTheNewMode() = runTest {
        val preferences = AppearancePreferencesRepository(preferences(appearanceMode = AppearanceMode.SYSTEM))
            .also { it.failSave = true }
        val model = model(AppearanceScheduleRepository(seededSchedule()), preferences, AppearanceReminderControl())
        advanceUntilIdle()

        model.setAppearanceMode(AppearanceMode.DARK)
        advanceUntilIdle()
        assertEquals(AppearanceMode.SYSTEM, model.state.value.preferences.appearanceMode)

        preferences.failSave = false
        model.setAppearanceMode(AppearanceMode.DARK)
        advanceUntilIdle()
        assertEquals(AppearanceMode.DARK, model.state.value.preferences.appearanceMode)
        assertNull(model.state.value.error)
        assertEquals(2, preferences.saveCalls)
    }

    @Test
    fun aCancelledWriteRestoresTheCommittedModeWithoutAnOrdinaryError() = runTest {
        val stored = preferences(appearanceMode = AppearanceMode.SYSTEM)
        val preferences = AppearancePreferencesRepository(stored).also { it.cancelSave = true }
        val model = model(AppearanceScheduleRepository(seededSchedule()), preferences, AppearanceReminderControl())
        advanceUntilIdle()

        model.setAppearanceMode(AppearanceMode.DARK)
        advanceUntilIdle()

        assertEquals(stored, model.state.value.preferences)
        assertNull(model.state.value.error)
        assertEquals(false, model.state.value.isSaving)
    }

    @Test
    fun aSecondSelectionWhileTheFirstWriteIsInFlightIsIgnored() = runTest {
        val preferences = AppearancePreferencesRepository(preferences(appearanceMode = AppearanceMode.SYSTEM))
        val gate = CompletableDeferred<Unit>()
        preferences.saveGate = gate
        val model = model(AppearanceScheduleRepository(seededSchedule()), preferences, AppearanceReminderControl())
        advanceUntilIdle()

        model.setAppearanceMode(AppearanceMode.DARK)
        assertEquals(true, model.state.value.isSaving)
        model.setAppearanceMode(AppearanceMode.LIGHT)
        model.setAppearanceMode(AppearanceMode.SYSTEM)
        assertEquals("overlapping selections must not queue", 1, preferences.saveCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, preferences.saveCalls)
        assertEquals(AppearanceMode.DARK, model.state.value.preferences.appearanceMode)
        assertEquals(AppearanceMode.DARK, preferences.preferences.appearanceMode)
        assertEquals(false, model.state.value.isSaving)
    }

    private fun model(
        repository: AppearanceScheduleRepository,
        preferences: AppearancePreferencesRepository,
        reminders: AppearanceReminderControl,
    ) = ScheduleViewModel(
        ScheduleAppState(repository, preferences),
        now = { LocalDateTime.parse("2026-09-03T09:00") },
        idFactory = { "generated" },
        reminders = reminders,
    )
}

private fun preferences(appearanceMode: AppearanceMode) = SchedulePreferences(
    appearanceMode = appearanceMode,
    reminder = ReminderPreferences(remindersEnabled = true, reminderLeadMinutes = 25, usesCustomLeadTime = true),
    academicCalendar = AcademicCalendarPreferences(
        weekendsAreNonTeachingDays = true,
        nonTeachingDates = listOf("2026-10-01", "2026-10-02"),
        makeupTeachingDays = listOf(MakeupTeachingDay("2026-10-10", 3)),
        lunchBreak = LunchBreakSettings(isEnabled = false, title = "午休", startTime = "12:00", endTime = "13:30"),
    ),
)

private fun seededSchedule() = ScheduleData(
    1,
    Semester("semester", "2026 秋季学期", "2026-09-02", 18, listOf(Period(1, "08:00", "08:45"))),
    listOf(Course("course", "数据结构", "", "#287B74", listOf(CourseSchedule("slot", 1, 1, 1, 1, 18, RepeatRule.EVERY, "A101")))),
    "2026-09-02T12:00:00.000Z",
)

private class AppearanceScheduleRepository(initial: ScheduleData) : ScheduleRepository {
    var data = initial
    var scheduleWrites = 0

    override suspend fun load(): ScheduleData = data

    override suspend fun replace(data: ScheduleData): ScheduleData = data.also { scheduleWrites++; this.data = it }

    override suspend fun saveSemester(semester: Semester): ScheduleData = data.also { scheduleWrites++ }

    override suspend fun saveCourse(course: Course): ScheduleData = data.also { scheduleWrites++ }

    override suspend fun deleteCourse(id: String): ScheduleData = data.also { scheduleWrites++ }
}

private class AppearancePreferencesRepository(private val initial: SchedulePreferences) : SchedulePreferencesRepository {
    var preferences = initial
    var saveCalls = 0
    var failSave = false
    var cancelSave = false
    var saveGate: CompletableDeferred<Unit>? = null

    override suspend fun load(): SchedulePreferences = preferences

    override suspend fun save(preferences: SchedulePreferences): SchedulePreferences {
        saveCalls++
        saveGate?.await()
        if (cancelSave) throw CancellationException("appearance cancelled")
        if (failSave) error("appearance write failed")
        this.preferences = preferences
        return preferences
    }

    override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences): SchedulePreferences =
        save(transform(preferences))
}

private class AppearanceReminderControl : ReminderControl {
    val reconcileReasons = mutableListOf<ReminderReconcileReason>()

    override suspend fun snapshot() = ReminderStatusSnapshot(
        availability = ReminderAvailability(true, true, true),
        activeAlarms = emptyList(),
    )

    override suspend fun reconcile(reason: ReminderReconcileReason): ReminderReconciliation {
        reconcileReasons += reason
        return ReminderReconciliation(reason, 0, true, ReminderAvailability(true, true, true))
    }

    override suspend fun cancelAll(reason: ReminderReconcileReason): ReminderReconciliation =
        ReminderReconciliation(reason, 0, false, ReminderAvailability(true, true, true))
}
