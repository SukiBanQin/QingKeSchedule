package com.qingke.schedule.viewmodel

import com.qingke.schedule.domain.MAX_IMPORT_BYTES
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
import com.qingke.schedule.transfer.ScheduleDataDecoder
import com.qingke.schedule.transfer.ScheduleDataTransfer
import com.qingke.schedule.transfer.ScheduleFileException
import java.io.File
import java.time.LocalDate
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P4／A10 state machine: a preview never writes, only an explicit confirmation runs the atomic replace, and a
 * failure, a cancellation or a reminder problem can never be reported as a committed import.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTransferTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val fixtureRoot = File(requireNotNull(System.getProperty("sharedFixturesDirectory")))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aPreviewAndACancelNeverWriteTheCommittedSchedule() = runTest {
        val repository = TransferRepository(seededSchedule())
        val preferences = TransferPreferencesRepository(fullPreferences())
        val reminders = TransferReminderControl()
        val model = model(repository, preferences, reminders)
        advanceUntilIdle()
        val original = repository.data

        model.importSelected({ fixture("valid", "complete-schedule.json") })
        advanceUntilIdle()

        assertEquals(6, model.transfer.value.preview?.courseCount)
        assertEquals("2026 秋季学期", model.transfer.value.preview?.semesterName)
        assertEquals("2026-09-02T12:00:00.000Z", model.transfer.value.preview?.updatedAt)
        assertEquals(0, repository.replaceCalls)
        assertEquals(original, repository.data)
        assertEquals(original, model.state.value.data)
        assertEquals(fullPreferences(), preferences.preferences)
        assertTrue(reminders.reconcileReasons.isEmpty())

        model.dismissTransferPrompt()
        assertNull(model.transfer.value.preview)
        assertEquals(0, repository.replaceCalls)
        assertEquals(original, repository.data)
    }

    @Test
    fun aSystemCancelIsSilentAndTouchesNoState() = runTest {
        val repository = TransferRepository(seededSchedule())
        val preferences = TransferPreferencesRepository(fullPreferences())
        val model = model(repository, preferences, TransferReminderControl())
        advanceUntilIdle()
        val before = model.transfer.value

        model.importSelected(null)
        model.exportSelected(null)
        advanceUntilIdle()

        assertEquals(before, model.transfer.value)
        assertEquals(0, repository.replaceCalls)
        assertEquals(fullPreferences(), preferences.preferences)
    }

    @Test
    fun aConfirmedImportPublishesTheCommittedSnapshotRebuildsTheDraftAndReconcilesOnce() = runTest {
        val repository = TransferRepository(seededSchedule())
        val reminders = TransferReminderControl()
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), reminders)
        advanceUntilIdle()
        model.updateName("未保存的旧草稿")

        model.importSelected({ fixture("valid", "complete-schedule.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()

        assertEquals(1, repository.replaceCalls)
        assertEquals(6, repository.data.courses.size)
        assertEquals("2026 秋季学期", model.state.value.data.semester?.name)
        assertEquals(6, model.state.value.data.courses.size)
        assertEquals("已导入 6 门课程", model.transfer.value.statusMessage)
        assertNull(model.transfer.value.preview)
        assertFalse(model.transfer.value.isWriting)
        assertEquals("2026 秋季学期", model.form.value?.name)
        assertEquals(LocalDate.parse("2026-09-02"), model.form.value?.startDate)
        assertEquals(18, model.form.value?.totalWeeks)
        assertEquals(listOf(1, 2, 3, 4), model.form.value?.periods?.map { it.number })
        assertEquals(listOf(ReminderReconcileReason.DATA_SAVED), reminders.reconcileReasons)
    }

    @Test
    fun aSecondConfirmationWhileTheTransactionIsInFlightIsIgnored() = runTest {
        val repository = TransferRepository(seededSchedule())
        val gate = CompletableDeferred<Unit>()
        repository.replaceGate = gate
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), TransferReminderControl())
        advanceUntilIdle()

        model.importSelected({ fixture("valid", "web-export.json") })
        advanceUntilIdle()
        model.confirmImport()
        assertTrue(model.transfer.value.isWriting)

        model.confirmImport()
        model.confirmImport()
        assertEquals(1, repository.replaceCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.replaceCalls)
        assertEquals("已导入 6 门课程", model.transfer.value.statusMessage)
    }

    @Test
    fun aFailedWriteKeepsTheOriginalDataAndTheRetryablePreview() = runTest {
        val repository = TransferRepository(seededSchedule()).also { it.failReplace = true }
        val reminders = TransferReminderControl()
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), reminders)
        advanceUntilIdle()
        val original = repository.data

        model.importSelected({ fixture("valid", "complete-schedule.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()

        assertTrue(model.transfer.value.writeFailure.orEmpty().contains("磁盘写入失败"))
        assertNotNull(model.transfer.value.preview)
        assertNull(model.transfer.value.statusMessage)
        assertFalse(model.transfer.value.isWriting)
        assertEquals(original, repository.data)
        assertEquals(original, model.state.value.data)
        assertNull(model.state.value.error)
        assertTrue(reminders.reconcileReasons.isEmpty())

        repository.failReplace = false
        model.confirmImport()
        advanceUntilIdle()

        assertEquals(2, repository.replaceCalls)
        assertEquals("已导入 6 门课程", model.transfer.value.statusMessage)
        assertEquals(listOf(ReminderReconcileReason.DATA_SAVED), reminders.reconcileReasons)
    }

    @Test
    fun aCancelledWriteRestoresThePendingPreviewWithoutReportingSuccess() = runTest {
        val repository = TransferRepository(seededSchedule()).also { it.cancelReplace = true }
        val reminders = TransferReminderControl()
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), reminders)
        advanceUntilIdle()
        val original = repository.data

        model.importSelected({ fixture("valid", "empty-schedule.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()

        assertNotNull(model.transfer.value.preview)
        assertFalse(model.transfer.value.isWriting)
        assertNull(model.transfer.value.writeFailure)
        assertNull(model.transfer.value.statusMessage)
        assertNull(model.state.value.error)
        assertEquals(original, repository.data)
        assertEquals(original, model.state.value.data)
        assertTrue(reminders.reconcileReasons.isEmpty())
        assertEquals("未设置学期", model.transfer.value.preview?.semesterName)
    }

    @Test
    fun importingAnEmptySemesterLandsOnFirstBootWithAFreshDraftAndClearsOldReminders() = runTest {
        val repository = TransferRepository(seededSchedule())
        val reminders = TransferReminderControl()
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), reminders)
        advanceUntilIdle()
        val previousFormId = model.form.value?.id

        model.importSelected({ fixture("valid", "empty-schedule.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()

        assertTrue(model.state.value.needsOnboarding)
        assertNull(model.state.value.data.semester)
        assertTrue(model.state.value.data.courses.isEmpty())
        assertNotNull(model.form.value)
        assertNotEquals(previousFormId, model.form.value?.id)
        assertEquals("已导入 0 门课程", model.transfer.value.statusMessage)
        assertEquals(listOf(ReminderReconcileReason.DATA_SAVED), reminders.reconcileReasons)
    }

    @Test
    fun aReminderReconciliationFailureNeverRollsTheImportBack() = runTest {
        val repository = TransferRepository(seededSchedule())
        val reminders = TransferReminderControl().also { it.failReconcile = true }
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), reminders)
        advanceUntilIdle()

        model.importSelected({ fixture("valid", "complete-schedule.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()

        assertEquals(6, repository.data.courses.size)
        assertEquals(6, model.state.value.data.courses.size)
        assertEquals("已导入 6 门课程", model.transfer.value.statusMessage)
        assertNotNull(model.reminderUi.value.diagnostic)
    }

    @Test
    fun unknownVersionsOversizedFilesAndReadFailuresNeverPreviewOrWrite() = runTest {
        val repository = TransferRepository(seededSchedule())
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), TransferReminderControl())
        advanceUntilIdle()

        model.importSelected({ fixture("invalid", "unknown-version.json") })
        advanceUntilIdle()
        assertTrue(model.transfer.value.importFailure.orEmpty().contains("2"))
        assertNull(model.transfer.value.preview)

        model.importSelected({ ByteArray(MAX_IMPORT_BYTES + 1) })
        advanceUntilIdle()
        assertEquals("课表文件超过 5 MiB 输入上限", model.transfer.value.importFailure)

        model.importSelected({ throw ScheduleFileException("无法打开所选文件：文件不可读") })
        advanceUntilIdle()
        assertEquals("无法打开所选文件：文件不可读", model.transfer.value.importFailure)

        assertEquals(0, repository.replaceCalls)
    }

    @Test
    fun exportWritesTheCommittedVersionOneFileAndNothingElse() = runTest {
        val repository = TransferRepository(seededSchedule())
        val preferences = TransferPreferencesRepository(fullPreferences())
        val model = model(repository, preferences, TransferReminderControl())
        advanceUntilIdle()
        var written: ByteArray? = null

        model.exportSelected({ bytes -> written = bytes })
        advanceUntilIdle()

        val bytes = written ?: error("no export bytes")
        assertEquals(ScheduleDataTransfer.EXPORT_SUCCESS_MESSAGE, model.transfer.value.statusMessage)
        val tree = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertEquals(setOf("schemaVersion", "semester", "courses", "updatedAt"), tree.keys)
        assertEquals(repository.data, ScheduleDataDecoder.decode(bytes))
        assertEquals(fullPreferences(), preferences.preferences)
    }

    @Test
    fun exportWithoutASemesterIsBlockedAndWritesNothing() = runTest {
        val repository = TransferRepository(ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00.000Z"))
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), TransferReminderControl())
        advanceUntilIdle()
        var written: ByteArray? = null

        model.exportSelected({ bytes -> written = bytes })
        advanceUntilIdle()

        assertNull(written)
        assertEquals(ScheduleDataTransfer.IMPORT_BLOCKED_NO_SEMESTER, model.transfer.value.exportFailure)
        assertNull(model.transfer.value.statusMessage)
        assertEquals(0, repository.replaceCalls)
    }

    @Test
    fun aFailedExportKeepsTheAppDataAndReportsClearChineseCopy() = runTest {
        val repository = TransferRepository(seededSchedule())
        val model = model(repository, TransferPreferencesRepository(fullPreferences()), TransferReminderControl())
        advanceUntilIdle()
        val original = repository.data

        model.exportSelected({ throw ScheduleFileException("无法写入所选文件：写入中断") })
        advanceUntilIdle()

        assertEquals("无法写入所选文件：写入中断", model.transfer.value.exportFailure)
        assertNull(model.transfer.value.statusMessage)
        assertEquals(original, repository.data)
        assertEquals(original, model.state.value.data)
    }

    @Test
    fun everyOutcomeKeepsEverySchedulePreferenceFieldIdentical() = runTest {
        val expected = fullPreferences()
        val preferences = TransferPreferencesRepository(expected)
        val repository = TransferRepository(seededSchedule())
        val model = model(repository, preferences, TransferReminderControl())
        advanceUntilIdle()
        assertEquals(expected, model.state.value.preferences)

        model.importSelected({ fixture("valid", "web-export.json") })
        advanceUntilIdle()
        model.dismissTransferPrompt()

        model.importSelected({ byteArrayOf(1, 2, 3, -1) })
        advanceUntilIdle()
        model.dismissTransferPrompt()

        repository.failReplace = true
        model.importSelected({ fixture("valid", "web-export.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()
        model.dismissTransferPrompt()

        model.exportSelected({ error("write failed") })
        advanceUntilIdle()
        model.dismissTransferPrompt()

        repository.failReplace = false
        model.importSelected({ fixture("valid", "empty-schedule.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()

        model.importSelected({ fixture("valid", "complete-schedule.json") })
        advanceUntilIdle()
        model.confirmImport()
        advanceUntilIdle()
        model.exportSelected({ })
        advanceUntilIdle()

        assertEquals(expected, preferences.preferences)
        assertEquals(expected, model.state.value.preferences)
        assertEquals(expected.appearanceMode, model.state.value.preferences.appearanceMode)
        assertEquals(expected.reminder, model.state.value.preferences.reminder)
        assertEquals(expected.academicCalendar, model.state.value.preferences.academicCalendar)
        assertEquals(expected.academicCalendar.lunchBreak, model.state.value.preferences.academicCalendar.lunchBreak)
        assertEquals(expected.academicCalendar.nonTeachingDates, model.state.value.preferences.academicCalendar.nonTeachingDates)
        assertEquals(expected.academicCalendar.makeupTeachingDays, model.state.value.preferences.academicCalendar.makeupTeachingDays)
        assertEquals(0, preferences.saveCalls)
    }

    @Test
    fun theSuggestedExportNameUsesTheInjectedClockAndTheSharedCopyStaysStable() = runTest {
        val model = model(TransferRepository(seededSchedule()), TransferPreferencesRepository(fullPreferences()), TransferReminderControl())
        advanceUntilIdle()

        assertEquals("qingke-schedule-2026-09-03.json", model.suggestedExportFileName())
        assertEquals("设置学期后可导出备份", ScheduleDataTransfer.NO_SEMESTER_EXPORT_HINT)
        assertEquals("将整体替换当前课表", ScheduleDataTransfer.REPLACE_NOTICE)
        assertEquals("已导出备份文件", ScheduleDataTransfer.EXPORT_SUCCESS_MESSAGE)
    }

    private fun model(
        repository: TransferRepository,
        preferences: TransferPreferencesRepository,
        reminders: TransferReminderControl,
    ) = ScheduleViewModel(
        ScheduleAppState(repository, preferences),
        now = { LocalDateTime.parse("2026-09-03T09:00") },
        idFactory = ids(),
        reminders = reminders,
    )

    private fun fixture(kind: String, name: String): ByteArray = fixtureRoot.resolve("$kind/$name").readBytes()

    private fun ids(): () -> String {
        var number = 0
        return { "generated-${number++}" }
    }
}

private fun seededSchedule() = ScheduleData(
    1,
    Semester(
        "semester-existing",
        "原有学期",
        "2026-09-01",
        18,
        listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")),
    ),
    listOf(
        Course(
            "existing",
            "已有课程",
            "",
            "#287B74",
            listOf(CourseSchedule("existing-slot", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")),
        ),
    ),
    "2026-09-01T00:00:00.000Z",
)

private fun fullPreferences() = SchedulePreferences(
    appearanceMode = AppearanceMode.DARK,
    reminder = ReminderPreferences(remindersEnabled = true, reminderLeadMinutes = 25, usesCustomLeadTime = true),
    academicCalendar = AcademicCalendarPreferences(
        weekendsAreNonTeachingDays = true,
        nonTeachingDates = listOf("2026-10-01", "2026-10-02"),
        makeupTeachingDays = listOf(MakeupTeachingDay("2026-10-10", 3)),
        lunchBreak = LunchBreakSettings(isEnabled = false, title = "午休", startTime = "12:00", endTime = "13:30"),
    ),
)

private class TransferRepository(initial: ScheduleData) : ScheduleRepository {
    var data = initial
    var replaceCalls = 0
    var failReplace = false
    var cancelReplace = false
    var replaceGate: CompletableDeferred<Unit>? = null

    override suspend fun load(): ScheduleData = data

    override suspend fun replace(data: ScheduleData): ScheduleData {
        replaceCalls++
        replaceGate?.await()
        if (cancelReplace) throw CancellationException("replace cancelled")
        if (failReplace) throw IllegalStateException("磁盘写入失败")
        this.data = data
        return data
    }

    override suspend fun saveSemester(semester: Semester): ScheduleData = data

    override suspend fun saveCourse(course: Course): ScheduleData = data

    override suspend fun deleteCourse(id: String): ScheduleData = data
}

private class TransferPreferencesRepository(private val initial: SchedulePreferences) : SchedulePreferencesRepository {
    var preferences = initial
    var saveCalls = 0

    override suspend fun load(): SchedulePreferences = preferences

    override suspend fun save(preferences: SchedulePreferences): SchedulePreferences {
        saveCalls++
        this.preferences = preferences
        return preferences
    }

    override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences): SchedulePreferences =
        save(transform(preferences))
}

private class TransferReminderControl : ReminderControl {
    val reconcileReasons = mutableListOf<ReminderReconcileReason>()
    var failReconcile = false

    override suspend fun snapshot() = ReminderStatusSnapshot(
        availability = ReminderAvailability(true, true, true),
        activeAlarms = emptyList(),
    )

    override suspend fun reconcile(reason: ReminderReconcileReason): ReminderReconciliation {
        if (failReconcile) error("提醒平台不可用")
        reconcileReasons += reason
        return ReminderReconciliation(
            reason = reason,
            generation = reconcileReasons.size.toLong(),
            remindersEnabled = true,
            availability = ReminderAvailability(true, true, true),
            activeAlarms = emptyList(),
        )
    }

    override suspend fun cancelAll(reason: ReminderReconcileReason): ReminderReconciliation =
        ReminderReconciliation(reason, 0, false, ReminderAvailability(true, true, true))
}
