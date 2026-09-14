package com.qingke.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingke.schedule.ScheduleAppDependencies
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleConflict
import com.qingke.schedule.draft.CourseDraft
import com.qingke.schedule.draft.CourseSaveEvaluation
import com.qingke.schedule.draft.CourseScheduleDraft
import com.qingke.schedule.draft.SemesterDraft
import com.qingke.schedule.state.ScheduleAppState
import com.qingke.schedule.state.ScheduleState
import java.time.LocalDate
import java.time.LocalDateTime
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

enum class CourseEditorMode { CHOOSER, CREATE, EDIT, APPEND }

data class CourseScheduleFormState(
    val id: String, val dayOfWeek: Int, val startPeriod: Int, val endPeriod: Int,
    val startWeek: Int, val endWeek: Int, val repeatRule: RepeatRule, val classroom: String,
)

sealed interface CourseEditorConfirmation {
    data object Discard : CourseEditorConfirmation
    data object Delete : CourseEditorConfirmation
    data class Conflicts(val candidate: Course, val conflicts: List<ScheduleConflict>) : CourseEditorConfirmation
}

data class CourseEditorState(
    val mode: CourseEditorMode,
    val courseId: String? = null,
    val name: String = "",
    val teacher: String = "",
    val color: String = "#287B74",
    /** Raw user input is retained independently so an invalid hexadecimal candidate survives recreation. */
    val colorInput: String = color,
    val isColorDialogOpen: Boolean = false,
    val schedules: List<CourseScheduleFormState> = emptyList(),
    val originalScheduleCount: Int = 0,
    val validationMessage: String? = null,
    val confirmation: CourseEditorConfirmation? = null,
    val isInFlight: Boolean = false,
) {
    val isAppend: Boolean get() = mode == CourseEditorMode.APPEND
    val visibleSchedules: List<CourseScheduleFormState> get() = if (isAppend) schedules.drop(originalScheduleCount) else schedules
}

class ScheduleViewModel(
    private val appState: ScheduleAppState,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
) : ViewModel() {
    val state: StateFlow<ScheduleState> = appState.state
    private val mutableForm = MutableStateFlow<SemesterFormState?>(null)
    val form: StateFlow<SemesterFormState?> = mutableForm.asStateFlow()
    private val mutableSelectedTab = MutableStateFlow(MainTab.TODAY)
    val selectedTab: StateFlow<MainTab> = mutableSelectedTab.asStateFlow()
    private val mutableCurrentTime = MutableStateFlow(now())
    val currentTime: StateFlow<LocalDateTime> = mutableCurrentTime.asStateFlow()
    private var draft: SemesterDraft? = null
    private val mutableEditor = MutableStateFlow<CourseEditorState?>(null)
    val editor: StateFlow<CourseEditorState?> = mutableEditor.asStateFlow()
    private val mutableCourseSuccess = MutableStateFlow<String?>(null)
    val courseSuccess: StateFlow<String?> = mutableCourseSuccess.asStateFlow()
    private var courseDraft: CourseDraft? = null
    private var editorSourceIndex: Int? = null
    private var editorFingerprint: Course? = null
    private var editorInFlight = false
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
            draft = SemesterDraft.create(mutableCurrentTime.value.toLocalDate(), idFactory)
            publishForm()
        }
    }

    fun selectTab(tab: MainTab) { mutableSelectedTab.value = tab }

    /** Updates only the observable local clock. It deliberately does not reload persistent state. */
    fun refreshCurrentTime() { mutableCurrentTime.value = now() }

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

    fun consumeCourseSuccess() { mutableCourseSuccess.value = null }

    fun openAddCourse() {
        if (state.value.data.semester == null || editorInFlight) return
        if (state.value.data.courses.isEmpty()) openNewCourse() else mutableEditor.value = CourseEditorState(CourseEditorMode.CHOOSER)
    }

    fun openNewCourse() {
        val semester = state.value.data.semester ?: return
        courseDraft = CourseDraft.create(semester, currentTime.value.toLocalDate(), idFactory)
        editorSourceIndex = null; editorFingerprint = null
        publishEditor(CourseEditorMode.CREATE)
    }

    fun openCourseAt(index: Int) {
        val semester = state.value.data.semester ?: return
        val course = state.value.data.courses.getOrNull(index) ?: return
        courseDraft = CourseDraft.edit(course, semester, currentTime.value.toLocalDate(), idFactory = idFactory)
        editorSourceIndex = index; editorFingerprint = course
        publishEditor(CourseEditorMode.EDIT)
    }

    fun appendCourseAt(index: Int) {
        val semester = state.value.data.semester ?: return
        val course = state.value.data.courses.getOrNull(index) ?: return
        courseDraft = CourseDraft.edit(course, semester, currentTime.value.toLocalDate(), appendSchedule = true, idFactory = idFactory)
        editorSourceIndex = index; editorFingerprint = course
        publishEditor(CourseEditorMode.APPEND)
    }

    fun requestCloseEditor() {
        val current = courseDraft
        if (editorInFlight) return
        if (current == null || !current.isDirty) closeEditor() else updateEditor { it.copy(confirmation = CourseEditorConfirmation.Discard, validationMessage = null) }
    }

    fun dismissEditorConfirmation() = updateEditor { it.copy(confirmation = null) }
    /** System back closes a visible confirmation, rather than replacing it with a discard confirmation. */
    fun onEditorBack() {
        if (editorInFlight) return
        if (mutableEditor.value?.confirmation != null) dismissEditorConfirmation() else requestCloseEditor()
    }
    fun confirmDiscardEditor() { if (!editorInFlight) closeEditor() }
    fun requestDeleteCourse() { if (editorSourceIndex != null && !editorInFlight) updateEditor { it.copy(confirmation = CourseEditorConfirmation.Delete) } }

    fun updateCourseName(value: String) = editIdentity { it.name = value }
    fun updateCourseTeacher(value: String) = editIdentity { it.teacher = value }
    fun updateCourseColor(value: String) {
        if (!value.matches(Regex("^#[0-9A-Fa-f]{6}$"))) return
        editIdentity(colorInput = value.uppercase()) { it.color = value.uppercase() }
    }
    fun updateCourseColorInput(value: String) {
        if (mutableEditor.value?.isAppend == true || editorInFlight) return
        val input = value.uppercase()
        updateEditor { it.copy(colorInput = input) }
        if (input.matches(Regex("^#[0-9A-Fa-f]{6}$"))) updateCourseColor(input)
    }
    fun showColorDialog() { if (!editorInFlight && mutableEditor.value?.isAppend == false) updateEditor { it.copy(isColorDialogOpen = true) } }
    fun dismissColorDialog() = updateEditor { it.copy(isColorDialogOpen = false) }
    fun updateCourseScheduleDay(id: String, value: Int) = editSchedule(id) { it.dayOfWeek = value.coerceIn(1, 7) }
    fun updateCourseScheduleStartPeriod(id: String, value: Int) = editSchedule(id) { schedule ->
        val bounded = value.coerceIn(1, maximumPeriod())
        schedule.startPeriod = bounded; if (schedule.endPeriod < bounded) schedule.endPeriod = bounded
    }
    fun updateCourseScheduleEndPeriod(id: String, value: Int) = editSchedule(id) { schedule ->
        val bounded = value.coerceIn(1, maximumPeriod())
        schedule.endPeriod = bounded; if (schedule.startPeriod > bounded) schedule.startPeriod = bounded
    }
    fun updateCourseScheduleStartWeek(id: String, value: Int) = editSchedule(id) { schedule ->
        val bounded = value.coerceIn(1, state.value.data.semester?.totalWeeks ?: 52); schedule.startWeek = bounded; if (schedule.endWeek < bounded) schedule.endWeek = bounded
    }
    fun updateCourseScheduleEndWeek(id: String, value: Int) = editSchedule(id) { schedule ->
        val bounded = value.coerceIn(1, state.value.data.semester?.totalWeeks ?: 52); schedule.endWeek = bounded; if (schedule.startWeek > bounded) schedule.startWeek = bounded
    }
    fun updateCourseScheduleRepeat(id: String, value: RepeatRule) = editSchedule(id) { it.repeatRule = value }
    fun updateCourseScheduleClassroom(id: String, value: String) = editSchedule(id) { it.classroom = value }
    fun addCourseSchedule() = editCourse { it.addSchedule() }
    fun removeCourseSchedule(id: String) = editCourse { it.removeSchedule(id) }

    fun saveCourse() {
        val current = courseDraft ?: return
        val semester = state.value.data.semester ?: return
        if (editorInFlight) return
        when (val evaluation = current.evaluateSave(semester, state.value.data.courses, editorSourceIndex)) {
            is CourseSaveEvaluation.Invalid -> updateEditor { it.copy(validationMessage = evaluation.issues.firstOrNull()?.message, confirmation = null) }
            is CourseSaveEvaluation.Conflicting -> updateEditor { it.copy(validationMessage = null, confirmation = CourseEditorConfirmation.Conflicts(current.course(), evaluation.conflicts)) }
            CourseSaveEvaluation.Ready -> submitCourse(current.course())
        }
    }

    fun confirmSaveDespiteConflicts() {
        val candidate = (mutableEditor.value?.confirmation as? CourseEditorConfirmation.Conflicts)?.candidate ?: return
        if (!editorInFlight) submitCourse(candidate)
    }

    private fun submitCourse(candidate: Course) {
        if (editorInFlight) return
        editorInFlight = true
        updateEditor { it.copy(isInFlight = true, confirmation = null, validationMessage = null) }
        val source = editorSourceIndex; val fingerprint = editorFingerprint; val mode = mutableEditor.value?.mode
        viewModelScope.launch {
            try {
                val success = if (source == null) appState.saveCourse(candidate) else appState.saveCourseAt(source, requireNotNull(fingerprint), candidate)
                if (success) {
                    mutableCourseSuccess.value = if (mode == CourseEditorMode.APPEND) "上课安排添加成功" else if (source == null) "课程添加成功" else "课程修改已保存"
                    closeEditor()
                } else updateEditor { it.copy(isInFlight = false) }
            } catch (error: CancellationException) { throw error
            } finally { editorInFlight = false; mutableEditor.value?.let { if (it.isInFlight) mutableEditor.value = it.copy(isInFlight = false) } }
        }
    }

    fun confirmDeleteCourse() {
        val source = editorSourceIndex ?: return; val fingerprint = editorFingerprint ?: return
        if (editorInFlight) return
        editorInFlight = true
        updateEditor { it.copy(isInFlight = true, confirmation = null) }
        viewModelScope.launch {
            try {
                if (appState.deleteCourseAt(source, fingerprint)) { mutableCourseSuccess.value = "课程删除成功"; closeEditor() }
                else updateEditor { it.copy(isInFlight = false) }
            } catch (error: CancellationException) { throw error
            } finally { editorInFlight = false; mutableEditor.value?.let { if (it.isInFlight) mutableEditor.value = it.copy(isInFlight = false) } }
        }
    }

    private fun editCourse(change: (CourseDraft) -> Unit) {
        if (editorInFlight) return
        courseDraft?.let { change(it); publishEditor(mutableEditor.value?.mode ?: return) }
    }
    private fun editIdentity(colorInput: String? = null, change: (CourseDraft) -> Unit) {
        if (mutableEditor.value?.isAppend == true || editorInFlight) return
        courseDraft?.let { change(it); publishEditor(mutableEditor.value?.mode ?: return, colorInput) }
    }
    private fun maximumPeriod(): Int = state.value.data.semester?.periods?.maxOfOrNull { it.number } ?: 1
    private fun editSchedule(id: String, change: (CourseScheduleDraft) -> Unit) = editCourse { draft -> draft.schedules.firstOrNull { it.id == id }?.let(change) }
    private fun updateEditor(change: (CourseEditorState) -> CourseEditorState) { mutableEditor.value?.let { mutableEditor.value = change(it) } }
    private fun publishEditor(mode: CourseEditorMode, colorInput: String? = null) {
        val value = courseDraft ?: return
        val previous = mutableEditor.value
        mutableEditor.value = CourseEditorState(mode = mode, courseId = value.id, name = value.name, teacher = value.teacher, color = value.color,
            colorInput = colorInput ?: previous?.colorInput ?: value.color,
            isColorDialogOpen = previous?.isColorDialogOpen ?: false,
            schedules = value.schedules.map { CourseScheduleFormState(it.id, it.dayOfWeek, it.startPeriod, it.endPeriod, it.startWeek, it.endWeek, it.repeatRule, it.classroom) },
            originalScheduleCount = if (mode == CourseEditorMode.APPEND) editorFingerprint?.schedules?.size ?: 0 else 0,
            validationMessage = previous?.validationMessage, confirmation = previous?.confirmation, isInFlight = editorInFlight)
    }
    private fun closeEditor() { courseDraft = null; editorSourceIndex = null; editorFingerprint = null; mutableEditor.value = null }

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
        private val now: () -> LocalDateTime = LocalDateTime::now,
        private val idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    ) : ViewModelProvider.Factory {
        private val state = ScheduleAppState(dependencies.scheduleRepository, dependencies.preferencesRepository)
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(state, now, idFactory) as T
    }
}

enum class MainTab { TODAY, SCHEDULE, SETTINGS }
