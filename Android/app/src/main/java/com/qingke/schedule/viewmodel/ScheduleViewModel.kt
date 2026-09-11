package com.qingke.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingke.schedule.ScheduleAppDependencies
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.draft.SemesterDraft
import com.qingke.schedule.state.ScheduleAppState
import com.qingke.schedule.state.ScheduleState
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class PeriodFormState(val id: String, val number: Int, val start: LocalTime, val end: LocalTime)

data class SemesterFormState(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val periods: List<PeriodFormState>,
    val periodsExpanded: Boolean = false,
    val validationMessage: String? = null,
)

class ScheduleViewModel(
    private val appState: ScheduleAppState,
    private val now: () -> LocalDate = LocalDate::now,
    private val idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
) : ViewModel() {
    val state: StateFlow<ScheduleState> = appState.state
    private val mutableForm = MutableStateFlow<SemesterFormState?>(null)
    val form: StateFlow<SemesterFormState?> = mutableForm.asStateFlow()
    private val mutableSelectedTab = MutableStateFlow(MainTab.TODAY)
    val selectedTab: StateFlow<MainTab> = mutableSelectedTab.asStateFlow()
    private var draft: SemesterDraft? = null
    private var saveRequested = false
    private var loadJob: Job? = null

    init { loadInitial() }

    private fun loadInitial() = requestLoad()

    fun retryLoad() = requestLoad()

    private fun requestLoad() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { loadAndPrepare() }
    }

    private suspend fun loadAndPrepare() {
        appState.load()
        if (state.value.needsOnboarding && draft == null) {
            draft = SemesterDraft.create(now(), idFactory)
            publishForm()
        }
    }

    fun selectTab(tab: MainTab) { mutableSelectedTab.value = tab }

    fun updateName(value: String) = edit { it.name = value }
    fun updateStartDate(value: LocalDate) = edit { it.startDate = value }
    fun updateTotalWeeks(value: Int) = edit { it.totalWeeks = value.coerceIn(1, 52) }
    fun updatePeriodStart(id: String, value: LocalTime) = edit { draft -> draft.periods.firstOrNull { it.id == id }?.startTime = value }
    fun updatePeriodEnd(id: String, value: LocalTime) = edit { draft -> draft.periods.firstOrNull { it.id == id }?.endTime = value }
    fun addPeriod() = edit { if (it.periods.size < 20) it.addPeriod() }
    fun removePeriod(id: String) = edit { it.removePeriod(id) }
    fun togglePeriods() = mutableForm.value?.let { mutableForm.value = it.copy(periodsExpanded = !it.periodsExpanded) }

    private fun edit(change: (SemesterDraft) -> Unit) {
        draft?.let { change(it); publishForm() }
    }

    fun saveSemester() {
        val current = draft ?: return
        if (saveRequested || state.value.isSaving) return
        val issues = current.validationIssues()
        if (issues.isNotEmpty()) {
            mutableForm.value = snapshot(current).copy(validationMessage = issues.first().message)
            return
        }
        saveRequested = true
        mutableForm.value = snapshot(current).copy(validationMessage = null)
        val semester = current.semester()
        viewModelScope.launch {
            try {
                appState.saveSemester(semester)
            } catch (error: CancellationException) {
                throw error
            } finally {
                saveRequested = false
            }
        }
    }

    fun dismissError() = appState.clearError()

    private fun publishForm() { draft?.let { mutableForm.value = snapshot(it) } }

    private fun snapshot(value: SemesterDraft): SemesterFormState = SemesterFormState(
        id = value.id,
        name = value.name,
        startDate = value.startDate,
        totalWeeks = value.totalWeeks,
        periods = value.periods.map { PeriodFormState(it.id, it.number, it.startTime, it.endTime) },
        periodsExpanded = mutableForm.value?.periodsExpanded ?: false,
    )

    class Factory(
        dependencies: ScheduleAppDependencies,
        private val now: () -> LocalDate = LocalDate::now,
        private val idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    ) : ViewModelProvider.Factory {
        private val state = ScheduleAppState(dependencies.scheduleRepository, dependencies.preferencesRepository)
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(state, now, idFactory) as T
    }
}

enum class MainTab { TODAY, SCHEDULE, SETTINGS }
