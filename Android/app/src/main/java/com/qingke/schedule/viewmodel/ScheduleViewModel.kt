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
import com.qingke.schedule.domain.SemesterCascadeEvaluation
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
import com.qingke.schedule.preferences.ReminderPreferences
import com.qingke.schedule.reminder.ReminderControl
import com.qingke.schedule.reminder.ReminderReconcileReason
import com.qingke.schedule.reminder.ReminderReconciliation
import com.qingke.schedule.state.ScheduleAppState
import com.qingke.schedule.state.ScheduleState
import com.qingke.schedule.transfer.ScheduleDataDecoder
import com.qingke.schedule.transfer.ScheduleDataException
import com.qingke.schedule.transfer.ScheduleDataTransfer
import com.qingke.schedule.transfer.ScheduleFileException
import com.qingke.schedule.transfer.ScheduleImportPreview
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

    /**
     * P3-04-R8: a save blocked by non-continuable input. It lives in the same modal hierarchy as the other
     * course-editor confirmations so it survives recreation, renders as one centered dialog and can never be
     * bypassed by a "save anyway" action.
     */
    data class Invalid(val message: String) : CourseEditorConfirmation
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
    private val reminders: ReminderControl? = null,
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
    private val mutableReminderUi = MutableStateFlow(ReminderUiState())
    val reminderUi: StateFlow<ReminderUiState> = mutableReminderUi.asStateFlow()
    private val mutableTransfer = MutableStateFlow(TransferUiState())
    val transfer: StateFlow<TransferUiState> = mutableTransfer.asStateFlow()
    private var courseDraft: CourseDraft? = null
    private var editorSourceIndex: Int? = null
    private var editorFingerprint: Course? = null
    private var editorInFlight = false
    private var lunchBreakConfirmationInFlight = false
    private var loadJob: Job? = null
    private var awaitingSystemSettings = false
    private var importInFlight = false
    private var exportInFlight = false

    init { loadInitial() }

    private fun loadInitial() = requestLoad()

    fun retryLoad() = requestLoad()

    private fun requestLoad() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { loadAndPrepare() }
    }

    private suspend fun loadAndPrepare() {
        appState.load()
        refreshReminderStatus()
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
    fun saveSemester() {
        if (mutableSemesterSave.value is SemesterSaveState.Writing || state.value.isSaving) return
        val current = draft ?: return
        when (val evaluation = evaluateDraft(current)) {
            is DraftEvaluation.Invalid -> mutableSemesterSave.value = SemesterSaveState.Blocked(evaluation.message)
            is DraftEvaluation.Unmappable -> mutableSemesterSave.value = SemesterSaveState.Blocked(evaluation.message)
            is DraftEvaluation.Impact -> mutableSemesterSave.value = SemesterSaveState.AwaitingCascade(evaluation.plan)
            DraftEvaluation.Unaffected -> submitSemester(current, null, state.value.data.courses)
        }
    }

    /**
     * P3-06-R7-R1: the confirmation is only accepted from [SemesterSaveState.AwaitingCascade]. Every other
     * state, including a stale confirmation after the dialog was dismissed and a repeated tap during the
     * write, is a no-op. The draft and the stored data are re-evaluated first, so a confirmation can never
     * write a plan the user has not just seen.
     */
    fun confirmSemesterCascade() {
        val confirmed = mutableSemesterSave.value as? SemesterSaveState.AwaitingCascade ?: return
        if (state.value.isSaving) return
        val current = draft ?: return
        when (val evaluation = evaluateDraft(current)) {
            is DraftEvaluation.Invalid -> mutableSemesterSave.value = SemesterSaveState.Blocked(evaluation.message)
            is DraftEvaluation.Unmappable -> mutableSemesterSave.value = SemesterSaveState.Blocked(evaluation.message)
            // The confirmed destruction no longer exists: this click must not write, the next save uses the
            // ordinary no-impact path instead of silently approving a plan the user never saw.
            DraftEvaluation.Unaffected -> mutableSemesterSave.value = SemesterSaveState.Idle
            is DraftEvaluation.Impact ->
                if (evaluation.plan == confirmed.plan) submitSemester(current, evaluation.plan, state.value.data.courses)
                else mutableSemesterSave.value = SemesterSaveState.AwaitingCascade(evaluation.plan)
        }
    }

    /**
     * "返回修改" of either dialog. Nothing is written and the draft stays untouched; an in-flight write ignores
     * the request, so a confirmed cascade cannot be replaced by a half-cancelled state.
     */
    fun dismissSemesterSave() {
        if (mutableSemesterSave.value is SemesterSaveState.Writing) return
        mutableSemesterSave.value = SemesterSaveState.Idle
    }

    /** The single evaluation shared by both save paths; none of these outcomes writes anything by itself. */
    private sealed interface DraftEvaluation {
        data class Invalid(val message: String) : DraftEvaluation

        data class Unmappable(val message: String) : DraftEvaluation

        data class Impact(val plan: SemesterCascadePlan) : DraftEvaluation

        data object Unaffected : DraftEvaluation
    }

    private fun evaluateDraft(current: SemesterDraft): DraftEvaluation {
        val previous = state.value.data.semester
        val courses = state.value.data.courses
        val issues = current.validationIssues() + current.courseRangeIssues(previous, courses)
        if (issues.isNotEmpty()) return DraftEvaluation.Invalid(issues.first().message)
        return when (val evaluation = SemesterCascadePlanner.evaluate(previous, courses, current.periodIdentities())) {
            is SemesterCascadeEvaluation.Blocked -> DraftEvaluation.Unmappable(evaluation.message)
            is SemesterCascadeEvaluation.Plan ->
                if (evaluation.plan.hasImpact) DraftEvaluation.Impact(evaluation.plan) else DraftEvaluation.Unaffected
        }
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
                    reconcileReminders(ReminderReconcileReason.DATA_SAVED)
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

    /* P4／A10: JSON import and export over Android's system document pickers. The state machine below only ever
       decodes into a preview; the committed schedule, the local preferences and the reminder registry change once,
       after an explicit confirmation and a successful replace. */

    /** Name suggested to the system CreateDocument panel; the clock is the ViewModel's injectable one. */
    fun suggestedExportFileName(): String =
        ScheduleDataTransfer.exportFileName(mutableCurrentTime.value.toLocalDate())

    /**
     * One system OpenDocument result. A null argument is the system cancel: it must stay silent and leave the
     * preview, the committed schedule, the preferences and the reminder registry untouched.
     */
    fun importSelected(read: (suspend () -> ByteArray)?) {
        if (read == null) return
        if (importInFlight) return
        importInFlight = true
        mutableTransfer.value = TransferUiState()
        viewModelScope.launch {
            try {
                val preview = ScheduleImportPreview.of(ScheduleDataDecoder.decode(read()))
                mutableTransfer.value = TransferUiState(preview = preview)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableTransfer.value = TransferUiState(importFailure = importErrorMessage(error))
            } finally {
                importInFlight = false
            }
        }
    }

    /**
     * The destructive step. It publishes only the repository snapshot returned by the committed transaction, so a
     * failure or a cancellation keeps the preview retryable and can never report a replace that did not commit.
     */
    fun confirmImport() {
        val current = mutableTransfer.value
        val preview = current.preview ?: return
        if (importInFlight) return
        importInFlight = true
        mutableTransfer.value = current.copy(isWriting = true, writeFailure = null, importFailure = null)
        viewModelScope.launch {
            try {
                if (appState.replace(preview.data)) {
                    rebuildSemesterDraft()
                    mutableTransfer.value = TransferUiState(
                        statusMessage = ScheduleDataTransfer.importSuccessMessage(preview.courseCount),
                    )
                    reconcileReminders(ReminderReconcileReason.DATA_SAVED)
                } else {
                    val message = appState.state.value.error ?: "导入失败，请重试"
                    appState.clearError()
                    mutableTransfer.value = current.copy(isWriting = false, writeFailure = message)
                }
            } catch (error: CancellationException) {
                mutableTransfer.value = current.copy(isWriting = false)
                throw error
            } catch (error: Throwable) {
                mutableTransfer.value = current.copy(isWriting = false, writeFailure = importErrorMessage(error))
            } finally {
                importInFlight = false
            }
        }
    }

    /** Cancels the preview or closes a failure dialog without touching any committed data. */
    fun dismissTransferPrompt() {
        mutableTransfer.value = mutableTransfer.value.copy(
            preview = null,
            importFailure = null,
            writeFailure = null,
            exportFailure = null,
            isWriting = false,
        )
    }

    /**
     * One system CreateDocument result. A null argument is the system cancel and stays silent; the bytes are the
     * validated version-1 encoding of the currently committed schedule only.
     */
    fun exportSelected(write: (suspend (ByteArray) -> Unit)?) {
        if (write == null) return
        if (exportInFlight) return
        val data = state.value.data
        if (data.semester == null) {
            mutableTransfer.value = TransferUiState(exportFailure = ScheduleDataTransfer.IMPORT_BLOCKED_NO_SEMESTER)
            return
        }
        exportInFlight = true
        viewModelScope.launch {
            try {
                write(ScheduleDataDecoder.encode(data))
                mutableTransfer.value = TransferUiState(statusMessage = ScheduleDataTransfer.EXPORT_SUCCESS_MESSAGE)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableTransfer.value = mutableTransfer.value.copy(
                    exportFailure = exportErrorMessage(error),
                    statusMessage = null,
                )
            } finally {
                exportInFlight = false
            }
        }
    }

    /**
     * A10: the settings form must never keep editing the replaced draft. The new draft comes from the committed
     * snapshot only, and an imported empty semester lands on the first-boot screen with a fresh create draft.
     */
    private fun rebuildSemesterDraft() {
        val semester = state.value.data.semester
        val rebuilt = when {
            semester != null -> SemesterDraft.edit(semester, idFactory)
            state.value.needsOnboarding -> SemesterDraft.create(mutableCurrentTime.value.toLocalDate(), idFactory)
            else -> null
        }
        draft = rebuilt
        if (rebuilt != null) publishInitialForm(rebuilt) else mutableForm.value = null
    }

    private fun importErrorMessage(error: Throwable): String = when (error) {
        is ScheduleDataException -> error.message ?: "课表文件无效"
        is ScheduleFileException -> error.message ?: "文件操作失败"
        else -> "导入失败：" + (error.message ?: "未知错误")
    }

    private fun exportErrorMessage(error: Throwable): String = when (error) {
        is ScheduleDataException -> error.message ?: "课表数据无效"
        is ScheduleFileException -> error.message ?: "文件操作失败"
        else -> "导出失败：" + (error.message ?: "未知错误")
    }

    /* A08 second batch: reminder settings. Preferences are written first and the platform is only touched after
       a successful write, so a failed reminder update can never roll back the schedule or the preferences. */

    /** Reads the current capabilities and the persisted alarms; it never creates the channel or writes. */
    fun refreshReminderStatus() {
        val control = reminders ?: return
        viewModelScope.launch {
            val snapshot = runCatching { control.snapshot() }
            val base = mutableReminderUi.value.copy(
                remindersEnabled = state.value.preferences.reminder.remindersEnabled,
                leadMinutes = state.value.preferences.reminder.reminderLeadMinutes,
                usesCustomLeadTime = state.value.preferences.reminder.usesCustomLeadTime,
            )
            snapshot.fold(
                onSuccess = { status ->
                    mutableReminderUi.value = base.copy(
                        loaded = true,
                        notificationsPermitted = status.availability.notificationsPermitted,
                        channelReady = status.availability.channelReady,
                        exactAlarmsAvailable = status.availability.exactAlarmsAvailable,
                        activeCount = status.activeCount,
                        degraded = status.degraded,
                        diagnostic = null,
                    )
                },
                onFailure = { error ->
                    mutableReminderUi.value = base.copy(loaded = true, diagnostic = error.message ?: "提醒状态读取失败")
                },
            )
        }
    }

    /**
     * iOS `setRemindersEnabled`: the preference is persisted first; enabling re-plans the rolling window while
     * switching off cancels every registered alarm. Requesting the system permission stays an explicit UI action.
     */
    fun setRemindersEnabled(enabled: Boolean) {
        if (state.value.preferences.reminder.remindersEnabled == enabled) return
        viewModelScope.launch {
            val saved = appState.updatePreferences { preferences ->
                preferences.copy(reminder = preferences.reminder.copy(remindersEnabled = enabled))
            }
            if (!saved) {
                refreshReminderStatus()
                return@launch
            }
            publishReminderPreferences()
            runReminderOperation { control ->
                if (enabled) control.reconcile(ReminderReconcileReason.PREFERENCES_CHANGED)
                else control.cancelAll(ReminderReconcileReason.PREFERENCES_CHANGED)
            }
        }
    }

    /** iOS `setReminderLeadMinutes`: 0–180 minutes only, and the stored selection follows the caller's choice. */
    fun setReminderLeadMinutes(minutes: Int, usesCustomSelection: Boolean? = null) {
        if (minutes !in ReminderPreferences.VALID_LEAD_MINUTES) return
        val custom = usesCustomSelection ?: (minutes !in ReminderPreferences.PRESET_LEAD_MINUTES)
        if (state.value.preferences.reminder.reminderLeadMinutes == minutes &&
            state.value.preferences.reminder.usesCustomLeadTime == custom
        ) {
            return
        }
        viewModelScope.launch {
            val saved = appState.updatePreferences { preferences ->
                preferences.copy(
                    reminder = preferences.reminder.copy(
                        reminderLeadMinutes = minutes,
                        usesCustomLeadTime = custom,
                    ),
                )
            }
            if (!saved) {
                refreshReminderStatus()
                return@launch
            }
            publishReminderPreferences()
            runReminderOperation { control -> control.reconcile(ReminderReconcileReason.PREFERENCES_CHANGED) }
        }
    }

    /** A08: a committed schedule or calendar write re-plans the window without ever rolling the write back. */
    private fun reconcileReminders(reason: ReminderReconcileReason) {
        viewModelScope.launch { runReminderOperation { control -> control.reconcile(reason) } }
    }

    /**
     * A08 R1: the user left the app for a system notification, channel or exact-alarm page. Only that handoff
     * makes the next foreground resume run a capability recovery; an ordinary resume stays a read-only refresh.
     */
    fun markSystemSettingsHandoff() {
        awaitingSystemSettings = true
    }

    /**
     * A08 R1: capability recovery. Idempotent while reminders are on and every capability is available, so a
     * permission granted after the first reconciliation - or a channel or exact-alarm switch flipped in system
     * settings - really re-registers the rolling window instead of only repainting the status. While reminders
     * are off, or while the notification permission is still missing, it only refreshes the status: a denial
     * never triggers another prompt.
     */
    fun recoverReminderCapabilities() {
        val control = reminders
        if (control == null || !state.value.preferences.reminder.remindersEnabled) {
            refreshReminderStatus()
            return
        }
        viewModelScope.launch {
            val snapshot = runCatching { control.snapshot() }.getOrNull()
            if (snapshot != null && !snapshot.availability.notificationsPermitted) {
                publishReminderSnapshot(snapshot)
                return@launch
            }
            runReminderOperation { it.reconcile(ReminderReconcileReason.MANUAL) }
        }
    }

    /** Every foreground resume: recover the capabilities after a system settings handoff, otherwise just read. */
    fun onForegroundResumed() {
        if (awaitingSystemSettings) {
            awaitingSystemSettings = false
            recoverReminderCapabilities()
        } else {
            refreshReminderStatus()
        }
    }

    private fun publishReminderSnapshot(snapshot: com.qingke.schedule.reminder.ReminderStatusSnapshot) {
        mutableReminderUi.value = mutableReminderUi.value.copy(
            loaded = true,
            remindersEnabled = state.value.preferences.reminder.remindersEnabled,
            leadMinutes = state.value.preferences.reminder.reminderLeadMinutes,
            usesCustomLeadTime = state.value.preferences.reminder.usesCustomLeadTime,
            notificationsPermitted = snapshot.availability.notificationsPermitted,
            channelReady = snapshot.availability.channelReady,
            exactAlarmsAvailable = snapshot.availability.exactAlarmsAvailable,
            activeCount = snapshot.activeCount,
            degraded = snapshot.degraded,
            diagnostic = null,
        )
    }

    private fun publishReminderPreferences() {
        val reminder = state.value.preferences.reminder
        mutableReminderUi.value = mutableReminderUi.value.copy(
            remindersEnabled = reminder.remindersEnabled,
            leadMinutes = reminder.reminderLeadMinutes,
            usesCustomLeadTime = reminder.usesCustomLeadTime,
        )
    }

    /**
     * One reminder operation: its result feeds the settings status, and a thrown platform error is reported as a
     * diagnostic instead of propagating, because a reminder failure must never look like a schedule failure.
     */
    private suspend fun runReminderOperation(
        block: suspend (ReminderControl) -> ReminderReconciliation,
    ) {
        val control = reminders ?: return
        runCatching { block(control) }.fold(
            onSuccess = { reconciliation ->
                mutableReminderUi.value = mutableReminderUi.value.copy(
                    loaded = true,
                    remindersEnabled = reconciliation.remindersEnabled,
                    notificationsPermitted = reconciliation.availability.notificationsPermitted,
                    channelReady = reconciliation.availability.channelReady,
                    exactAlarmsAvailable = reconciliation.availability.exactAlarmsAvailable,
                    activeCount = reconciliation.activeCount,
                    degraded = reconciliation.degraded,
                    lastFailureCount = reconciliation.failed.size,
                    diagnostic = null,
                )
            },
            onFailure = { error ->
                val status = runCatching { control.snapshot() }.getOrNull()
                val failed = mutableReminderUi.value.copy(
                    loaded = true,
                    diagnostic = error.message ?: "提醒更新失败",
                )
                mutableReminderUi.value = if (status == null) failed else failed.copy(
                    notificationsPermitted = status.availability.notificationsPermitted,
                    channelReady = status.availability.channelReady,
                    exactAlarmsAvailable = status.availability.exactAlarmsAvailable,
                    activeCount = status.activeCount,
                    degraded = status.degraded,
                )
            },
        )
    }

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
                if (written && mutableLunchBreakConflict.value == conflict) {
                    mutableLunchBreakConflict.value = null
                    reconcileReminders(ReminderReconcileReason.PREFERENCES_CHANGED)
                }
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
            val saved = appState.updatePreferences { preferences ->
                preferences.copy(academicCalendar = transform(preferences.academicCalendar))
            }
            if (saved) reconcileReminders(ReminderReconcileReason.PREFERENCES_CHANGED)
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
        if (current == null || !current.isDirty) closeEditor() else updateEditor { it.copy(confirmation = CourseEditorConfirmation.Discard) }
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
            is CourseSaveEvaluation.Invalid -> updateEditor { it.copy(confirmation = CourseEditorConfirmation.Invalid(evaluation.issues.first().message)) }
            is CourseSaveEvaluation.Conflicting -> updateEditor { it.copy(confirmation = CourseEditorConfirmation.Conflicts(current.course(), evaluation.conflicts)) }
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
        updateEditor { it.copy(isInFlight = true, confirmation = null) }
        val source = editorSourceIndex; val fingerprint = editorFingerprint; val mode = mutableEditor.value?.mode
        viewModelScope.launch {
            try {
                val success = if (source == null) appState.saveCourse(candidate) else appState.saveCourseAt(source, requireNotNull(fingerprint), candidate)
                if (success) {
                    mutableCourseSuccess.value = if (mode == CourseEditorMode.APPEND) "SYSTEM // 添加上课安排成功" else if (source == null) "SYSTEM // 课程添加成功" else "SYSTEM // 课程修改已保存"
                    reconcileReminders(ReminderReconcileReason.DATA_SAVED)
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
                if (appState.deleteCourseAt(source, fingerprint)) {
                    mutableCourseSuccess.value = "SYSTEM // 课程删除成功"
                    reconcileReminders(ReminderReconcileReason.DATA_SAVED)
                    closeEditor()
                }
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
            confirmation = previous?.confirmation, isInFlight = editorInFlight)
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
        private val reminderControl = dependencies.reminderCoordinator
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(state, now, idFactory, reminderControl) as T
    }
}

enum class MainTab { TODAY, SCHEDULE, SETTINGS }
