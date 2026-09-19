package com.qingke.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qingke.schedule.ScheduleAppDependencies
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleConflict
import com.qingke.schedule.domain.SemesterCascadePlan
import com.qingke.schedule.domain.SemesterCascadePlanner
import com.qingke.schedule.domain.withLunchBreakEnabled
import com.qingke.schedule.domain.withLunchBreakTimes
import com.qingke.schedule.domain.withMakeupTeachingDay
import com.qingke.schedule.domain.withNonTeachingDate
import com.qingke.schedule.domain.lunchBreakOverlappingPeriods
import com.qingke.schedule.domain.withWeekendsAreNonTeachingDays
import com.qingke.schedule.domain.withoutMakeupTeachingDay
import com.qingke.schedule.domain.withoutNonTeachingDate
import com.qingke.schedule.draft.CourseDraft
import com.qingke.schedule.draft.CourseSaveEvaluation
import com.qingke.schedule.draft.CourseScheduleDraft
import com.qingke.schedule.draft.SemesterDraft
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.LunchBreakSettings
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
)

/**
 * P3-06-R7: the single semester-save state machine behind both save entries. The three states stay
 * distinguishable so a blocked input can never turn into a forced write, and a cascaded write is never
 * reported as saved before the transaction returned.
 */
sealed interface SemesterSaveState {
    data object Idle : SemesterSaveState

    /** Non-continuable input or business validation: only a close action, never a forced save. */
    data class Blocked(val message: String) : SemesterSaveState

    /** Deleting or reordering periods changes saved course references, so one confirmation is required. */
    data class AwaitingCascade(val plan: SemesterCascadePlan) : SemesterSaveState

    /** The atomic semester + courses write is in flight; [plan] is set while a confirmed cascade executes. */
    data class Writing(val plan: SemesterCascadePlan?) : SemesterSaveState
}

enum class CourseEditorMode { CHOOSER, CREATE, EDIT, APPEND }

/**
 * P3-07-R1: a valid lunch range that overlaps the currently visible or the saved periods waits for one
 * explicit confirmation before it is written.
 */
data class LunchBreakConflict(
    val startTime: String,
    val endTime: String,
    /** Periods of the saved semester that overlap; the week matrix hides its lunch row while any exist. */
    val persistedPeriodNumbers: List<Int>,
    /** Periods of the current form configuration, which may still carry unsaved time edits. */
    val draftPeriodNumbers: List<Int>,
) {
    val periodNumbers: List<Int> get() = (persistedPeriodNumbers + draftPeriodNumbers).distinct().sorted()
    val hidesWeekMatrixRow: Boolean get() = persistedPeriodNumbers.isNotEmpty()
}

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
    private val mutableSemesterSuccess = MutableStateFlow<String?>(null)
    val semesterSuccess: StateFlow<String?> = mutableSemesterSuccess.asStateFlow()
    private val mutableSemesterSave = MutableStateFlow<SemesterSaveState>(SemesterSaveState.Idle)
    val semesterSave: StateFlow<SemesterSaveState> = mutableSemesterSave.asStateFlow()
    private val mutableLunchBreakConflict = MutableStateFlow<LunchBreakConflict?>(null)
    val lunchBreakConflict: StateFlow<LunchBreakConflict?> = mutableLunchBreakConflict.asStateFlow()
    private var courseDraft: CourseDraft? = null
    private var editorSourceIndex: Int? = null
    private var editorFingerprint: Course? = null
    private var editorInFlight = false
    private var lunchBreakConfirmationInFlight = false
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
        if (draft != null) return
        val semester = state.value.data.semester
        draft = when {
            semester != null -> SemesterDraft.edit(semester, idFactory)
            state.value.needsOnboarding -> SemesterDraft.create(mutableCurrentTime.value.toLocalDate(), idFactory)
            else -> null
        }
        draft?.let { publishInitialForm(it) }
    }

    /** Matches iOS: few periods start expanded so short timetables are editable straight away. */
    private fun publishInitialForm(value: SemesterDraft) {
        mutableForm.value = snapshot(value).copy(periodsExpanded = value.periods.size < 5)
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

    /**
     * P3-06-R7: both save entries call this one evaluation. Blocking input becomes a red error dialog, a
     * course-affecting period change waits for one destructive confirmation, and everything else is written
     * immediately. No branch writes a partial semester.
     */
    fun saveSemester() = evaluateSemesterSave(confirmed = false)

    /** The destructive confirmation action: re-evaluates the live draft first, then performs the atomic write. */
    fun confirmSemesterCascade() = evaluateSemesterSave(confirmed = true)

    /**
     * "返回修改" of either dialog. Nothing is written and the draft stays untouched; an in-flight write ignores
     * the request, so a confirmed cascade cannot be replaced by a half-cancelled state.
     */
    fun dismissSemesterSave() {
        if (mutableSemesterSave.value is SemesterSaveState.Writing) return
        mutableSemesterSave.value = SemesterSaveState.Idle
    }

    private fun evaluateSemesterSave(confirmed: Boolean) {
        val current = draft ?: return
        if (mutableSemesterSave.value is SemesterSaveState.Writing || state.value.isSaving) return
        val previous = state.value.data.semester
        val courses = state.value.data.courses
        val issues = current.validationIssues() + current.courseRangeIssues(previous, courses)
        if (issues.isNotEmpty()) {
            mutableSemesterSave.value = SemesterSaveState.Blocked(issues.first().message)
            return
        }
        val plan = SemesterCascadePlanner.plan(previous, courses, current.periodIdentities())
        if (plan.hasImpact && !confirmed) {
            mutableSemesterSave.value = SemesterSaveState.AwaitingCascade(plan)
            return
        }
        submitSemester(current, plan.takeIf { it.hasImpact }, courses)
    }

    /**
     * The draft is snapshotted before the write, so edits made while the transaction is in flight cannot be
     * reported as persisted. The confirmation is cleared only after a successful write; a failure or a
     * cancellation restores it in full so the same plan stays retryable.
     */
    private fun submitSemester(current: SemesterDraft, plan: SemesterCascadePlan?, courses: List<Course>) {
        val semester = current.semester()
        val persistedNumbers = current.periods.associate { it.id to it.number }
        val onboarding = state.value.data.semester == null
        val nextCourses = plan?.courses ?: courses
        mutableSemesterSave.value = SemesterSaveState.Writing(plan)
        viewModelScope.launch {
            try {
                if (appState.saveSemesterWithCourses(semester, nextCourses)) {
                    current.markPersisted(persistedNumbers)
                    if (!onboarding) mutableSemesterSuccess.value = "SYSTEM // 学期与节次设置已保存"
                    mutableSemesterSave.value = SemesterSaveState.Idle
                } else {
                    mutableSemesterSave.value = plan?.let { SemesterSaveState.AwaitingCascade(it) } ?: SemesterSaveState.Idle
                }
            } catch (error: CancellationException) {
                mutableSemesterSave.value = plan?.let { SemesterSaveState.AwaitingCascade(it) } ?: SemesterSaveState.Idle
                throw error
            }
        }
    }

    fun dismissError() = appState.clearError()

    /* A07 academic calendar: every write transforms the latest stored preferences inside the repository
       update, so consecutive edits cannot overwrite each other with a stale snapshot. */

    fun setWeekendsAreNonTeachingDays(enabled: Boolean) = updateCalendar { it.withWeekendsAreNonTeachingDays(enabled) }

    fun addNonTeachingDate(date: LocalDate) = updateCalendar { it.withNonTeachingDate(date) }

    fun removeNonTeachingDate(date: String) = updateCalendar { it.withoutNonTeachingDate(date) }

    fun addMakeupTeachingDay(date: LocalDate, followsDayOfWeek: Int) = updateCalendar { it.withMakeupTeachingDay(date, followsDayOfWeek) }

    fun removeMakeupTeachingDay(date: String) = updateCalendar { it.withoutMakeupTeachingDay(date) }

    fun setLunchBreakEnabled(enabled: Boolean) = updateCalendar { it.withLunchBreakEnabled(enabled) }

    /**
     * P3-07-R1: a range whose start is not earlier than its end is rejected without writing. A valid
     * range that overlaps a persisted period is not written yet either: it is held for the one-off red
     * warning (periods win, so the week matrix will keep hiding the lunch break row).
     */
    fun requestLunchBreakTimes(startTime: LocalTime, endTime: LocalTime) {
        val start = startTime.toString()
        val end = endTime.toString()
        if (!LunchBreakSettings.isValidRange(start, end)) return
        val conflict = lunchBreakConflictFor(start, end)
        if (conflict == null) {
            updateCalendar { it.withLunchBreakTimes(start, end) ?: it }
            return
        }
        mutableLunchBreakConflict.value = conflict
    }

    /**
     * The form configuration and the saved semester can differ while the settings page carries unsaved
     * period edits, and the week matrix keeps rendering the saved periods. A candidate range therefore
     * needs confirmation when either source overlaps it, and the conflict records which source did.
     */
    private fun lunchBreakConflictFor(start: String, end: String): LunchBreakConflict? {
        val persisted = lunchBreakOverlappingPeriods(start, end, state.value.data.semester?.periods.orEmpty())
            .map { it.number }.distinct().sorted()
        val draftPeriods = draft?.periods.orEmpty().map { period ->
            Period(period.number, timeText(period.startTime), timeText(period.endTime))
        }
        val draftOverlap = lunchBreakOverlappingPeriods(start, end, draftPeriods).map { it.number }.distinct().sorted()
        if (persisted.isEmpty() && draftOverlap.isEmpty()) return null
        return LunchBreakConflict(start, end, persisted, draftOverlap)
    }

    private fun timeText(value: LocalTime): String = "%02d:%02d".format(value.hour, value.minute)

    /**
     * P3-07-R1: the confirmation is completed only by a successfully stored range. A failed or cancelled
     * write keeps the pending confirmation retryable (and keeps the ordinary error feedback), so the page
     * never shows an unsaved candidate range as saved.
     */
    fun confirmLunchBreakDespiteConflicts() {
        val conflict = mutableLunchBreakConflict.value ?: return
        if (lunchBreakConfirmationInFlight) return
        lunchBreakConfirmationInFlight = true
        viewModelScope.launch {
            try {
                val written = appState.updatePreferences { preferences ->
                    preferences.copy(
                        academicCalendar = preferences.academicCalendar
                            .withLunchBreakTimes(conflict.startTime, conflict.endTime) ?: preferences.academicCalendar,
                    )
                }
                if (written && mutableLunchBreakConflict.value == conflict) mutableLunchBreakConflict.value = null
            } finally {
                lunchBreakConfirmationInFlight = false
            }
        }
    }

    /** "返回修改": the conflicting range is dropped and the stored lunch break stays unchanged. */
    fun dismissLunchBreakConfirmation() {
        if (lunchBreakConfirmationInFlight) return
        mutableLunchBreakConflict.value = null
    }

    private fun updateCalendar(transform: (AcademicCalendarPreferences) -> AcademicCalendarPreferences) {
        viewModelScope.launch {
            appState.updatePreferences { preferences -> preferences.copy(academicCalendar = transform(preferences.academicCalendar)) }
        }
    }

    fun consumeCourseSuccess() { mutableCourseSuccess.value = null }

    fun consumeSemesterSuccess() { mutableSemesterSuccess.value = null }

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
                    mutableCourseSuccess.value = if (mode == CourseEditorMode.APPEND) "SYSTEM // 添加上课安排成功" else if (source == null) "SYSTEM // 课程添加成功" else "SYSTEM // 课程修改已保存"
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
                if (appState.deleteCourseAt(source, fingerprint)) { mutableCourseSuccess.value = "SYSTEM // 课程删除成功"; closeEditor() }
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
