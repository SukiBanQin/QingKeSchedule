package com.qingke.schedule.viewmodel

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleAppState
import java.time.LocalDate
import java.time.LocalTime
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun initialLoadRunsOnceAndBuildsDeterministicOnboardingDraft() = runTest {
        val repository = FakeScheduleRepository()
        val model = ScheduleViewModel(appState(repository), now = { LocalDate.parse("2026-06-30") }, idFactory = ids())
        advanceUntilIdle()
        assertEquals(1, repository.loadCalls)
        assertEquals(LoadStatus.READY, model.state.value.loadStatus)
        assertEquals("2026 春季学期", model.form.value!!.name)
        assertEquals(18, model.form.value!!.totalWeeks)
        assertEquals(10, model.form.value!!.periods.size)
        assertEquals(11, model.form.value!!.let { listOf(it.id) + it.periods.map(PeriodFormState::id) }.toSet().size)
    }

    @Test fun invalidFormDoesNotSaveAndSavedFormUsesSingleSemester() = runTest {
        val repository = FakeScheduleRepository()
        val model = ScheduleViewModel(appState(repository), now = { LocalDate.parse("2026-07-01") }, idFactory = ids())
        advanceUntilIdle()
        model.updateName(" "); model.saveSemester(); advanceUntilIdle()
        assertEquals(0, repository.saveCalls)
        assertNotNull(model.form.value!!.validationMessage)
        model.updateName("秋季学期"); model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertFalse(model.state.value.needsOnboarding)
    }

    @Test fun editsPeriodsExpansionTabAndFullSemesterSnapshot() = runTest {
        val repository = FakeScheduleRepository()
        val model = ScheduleViewModel(appState(repository), now = { LocalDate.parse("2026-07-01") }, idFactory = ids())
        advanceUntilIdle()
        model.updateName("  自定义学期  "); model.updateStartDate(LocalDate.parse("2026-09-02")); model.updateTotalWeeks(20)
        model.togglePeriods(); val first = model.form.value!!.periods.first(); model.updatePeriodStart(first.id, LocalTime.of(7, 30)); model.updatePeriodEnd(first.id, LocalTime.of(8, 20)); model.addPeriod(); model.selectTab(MainTab.SETTINGS)
        assertTrue(model.form.value!!.periodsExpanded); assertEquals(LocalTime.of(7,30), model.form.value!!.periods.first().start); assertEquals(11, model.form.value!!.periods.size); assertEquals(MainTab.SETTINGS, model.selectedTab.value)
        model.saveSemester(); advanceUntilIdle()
        assertEquals("自定义学期", repository.saved!!.name); assertEquals("2026-09-02", repository.saved!!.startDate); assertEquals(20, repository.saved!!.totalWeeks); assertEquals(11, repository.saved!!.periods.size)
    }

    @Test fun failedLoadRetriesAndExistingSemesterDoesNotCreateDraft() = runTest {
        val repository = FakeScheduleRepository().also { it.failLoad = true }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        assertEquals(LoadStatus.FAILED, model.state.value.loadStatus); assertNull(model.form.value)
        repository.failLoad = false; repository.data = repository.data.copy(semester = Semester("s", "已有", "2026-09-01", 18, emptyList()))
        model.retryLoad(); advanceUntilIdle(); assertEquals(LoadStatus.READY, model.state.value.loadStatus); assertNull(model.form.value); assertEquals(2, repository.loadCalls)
    }

    @Test fun retryDuringSuspendedInitialLoadDoesNotStartAnotherReadAndLaterRetryDoes() = runTest {
        val repository = FakeScheduleRepository().also { it.loadGate = CompletableDeferred() }
        val model = ScheduleViewModel(appState(repository), idFactory = ids())
        assertEquals(1, repository.loadCalls)
        model.retryLoad(); model.retryLoad()
        assertEquals(1, repository.loadCalls)
        repository.loadGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(LoadStatus.READY, model.state.value.loadStatus)
        model.retryLoad(); advanceUntilIdle()
        assertEquals(2, repository.loadCalls)
    }

    @Test fun defaultDraftHasAllTimesInOrderAndUniqueIds() = runTest {
        val model = ScheduleViewModel(appState(FakeScheduleRepository()), now = { LocalDate.parse("2026-07-01") }, idFactory = ids())
        advanceUntilIdle()
        val form = model.form.value!!
        assertEquals(listOf(
            "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45", "10:55" to "11:40",
            "14:00" to "14:45", "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40",
            "19:00" to "19:45", "19:55" to "20:40",
        ), form.periods.map { it.start.toString() to it.end.toString() })
        assertEquals(11, (listOf(form.id) + form.periods.map { it.id }).toSet().size)
    }

    @Test fun formSnapshotClampsWeeksAndMaintainsOneToTwentySequentialPeriods() = runTest {
        val model = ScheduleViewModel(appState(FakeScheduleRepository()), idFactory = ids()); advanceUntilIdle()
        model.updateTotalWeeks(0); assertEquals(1, model.form.value!!.totalWeeks)
        model.updateTotalWeeks(99); assertEquals(52, model.form.value!!.totalWeeks)
        repeat(12) { model.addPeriod() }
        assertEquals(20, model.form.value!!.periods.size)
        model.addPeriod(); assertEquals(20, model.form.value!!.periods.size)
        val removed = model.form.value!!.periods[4].id; model.removePeriod(removed)
        assertEquals((1..19).toList(), model.form.value!!.periods.map { it.number })
        repeat(18) { model.removePeriod(model.form.value!!.periods.first().id) }
        assertEquals(1, model.form.value!!.periods.size)
    }

    @Test fun suspendedSaveOnlyWritesOnceAndSendsFullNormalizedSemester() = runTest {
        val repository = FakeScheduleRepository().also { it.saveGate = CompletableDeferred() }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.updateName("  秋季学期  "); model.updateStartDate(LocalDate.parse("2026-09-01")); model.updateTotalWeeks(20)
        model.togglePeriods(); val first = model.form.value!!.periods.first()
        model.updatePeriodStart(first.id, LocalTime.of(7, 30)); model.updatePeriodEnd(first.id, LocalTime.of(8, 20))
        model.saveSemester(); model.saveSemester()
        assertEquals(1, repository.saveCalls); assertTrue(model.state.value.isSaving)
        repository.saveGate!!.complete(Unit); advanceUntilIdle()
        val saved = repository.saved!!
        assertEquals(model.form.value!!.id, saved.id); assertEquals("秋季学期", saved.name)
        assertEquals("2026-09-01", saved.startDate); assertEquals(20, saved.totalWeeks)
        assertEquals("07:30", saved.periods.first().startTime); assertEquals("08:20", saved.periods.first().endTime)
        assertEquals((1..10).toList(), saved.periods.map { it.number }); assertFalse(model.state.value.isSaving)
    }

    @Test fun failedSaveAndDismissErrorRetainEntireDraft() = runTest {
        val repository = FakeScheduleRepository().also { it.failSave = true }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.updateName("保留"); model.updateStartDate(LocalDate.parse("2026-09-03")); model.updateTotalWeeks(21); model.togglePeriods(); model.addPeriod()
        val before = model.form.value!!; model.saveSemester(); advanceUntilIdle()
        assertEquals(before.copy(validationMessage = null), model.form.value)
        assertNotNull(model.state.value.error); model.dismissError()
        assertNull(model.state.value.error); assertEquals(before.copy(validationMessage = null), model.form.value)
    }

    @Test fun cancellationFromSaveDoesNotPublishOrdinaryErrorAndTabIsOwnedByModel() = runTest {
        val repository = FakeScheduleRepository().also { it.cancelSave = true }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.selectTab(MainTab.SCHEDULE); assertEquals(MainTab.SCHEDULE, model.selectedTab.value)
        model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls); assertNull(model.state.value.error); assertFalse(model.state.value.isSaving)
    }

    private fun appState(repository: FakeScheduleRepository) = ScheduleAppState(repository, object : SchedulePreferencesRepository {
        override suspend fun load() = SchedulePreferences.defaults
        override suspend fun save(preferences: SchedulePreferences) = preferences
        override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences) = transform(SchedulePreferences.defaults)
    })

    private class FakeScheduleRepository : ScheduleRepository {
        var data = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z")
        var loadCalls = 0; var saveCalls = 0; var failLoad = false; var failSave = false; var cancelSave = false; var saved: Semester? = null
        var loadGate: CompletableDeferred<Unit>? = null; var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun load(): ScheduleData { loadCalls++; loadGate?.await(); if (failLoad) error("load") ; return data }
        override suspend fun replace(data: ScheduleData) = data
        override suspend fun saveSemester(semester: Semester): ScheduleData { saveCalls++; saveGate?.await(); if (cancelSave) throw CancellationException("cancelled"); if (failSave) error("save"); saved = semester; return data.copy(semester = semester).also { data = it } }
        override suspend fun saveCourse(course: Course) = data
        override suspend fun deleteCourse(id: String) = data
    }

    private fun ids(): () -> String { var number = 0; return { "id-${number++}" } }
}
