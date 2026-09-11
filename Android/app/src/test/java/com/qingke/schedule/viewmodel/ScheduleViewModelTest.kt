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

    private fun appState(repository: FakeScheduleRepository) = ScheduleAppState(repository, object : SchedulePreferencesRepository {
        override suspend fun load() = SchedulePreferences.defaults
        override suspend fun save(preferences: SchedulePreferences) = preferences
        override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences) = transform(SchedulePreferences.defaults)
    })

    private class FakeScheduleRepository : ScheduleRepository {
        var data = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z")
        var loadCalls = 0; var saveCalls = 0; var failLoad = false; var failSave = false; var saved: Semester? = null
        override suspend fun load(): ScheduleData { loadCalls++; if (failLoad) error("load") ; return data }
        override suspend fun replace(data: ScheduleData) = data
        override suspend fun saveSemester(semester: Semester): ScheduleData { saveCalls++; if (failSave) error("save"); saved = semester; return data.copy(semester = semester).also { data = it } }
        override suspend fun saveCourse(course: Course) = data
        override suspend fun deleteCourse(id: String) = data
    }

    private fun ids(): () -> String { var number = 0; return { "id-${number++}" } }
}
