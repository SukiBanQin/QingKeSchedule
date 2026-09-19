package com.qingke.schedule.viewmodel

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleAppState
import java.time.LocalDate
import java.time.LocalDateTime
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
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-06-30T09:00") }, idFactory = ids())
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
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-07-01T09:00") }, idFactory = ids())
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
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-07-01T09:00") }, idFactory = ids())
        advanceUntilIdle()
        model.updateName("  自定义学期  "); model.updateStartDate(LocalDate.parse("2026-09-02")); model.updateTotalWeeks(20)
        model.togglePeriods(); val first = model.form.value!!.periods.first(); model.updatePeriodStart(first.id, LocalTime.of(7, 30)); model.updatePeriodEnd(first.id, LocalTime.of(8, 20)); model.addPeriod(); model.selectTab(MainTab.SETTINGS)
        assertTrue(model.form.value!!.periodsExpanded); assertEquals(LocalTime.of(7,30), model.form.value!!.periods.first().start); assertEquals(11, model.form.value!!.periods.size); assertEquals(MainTab.SETTINGS, model.selectedTab.value)
        model.saveSemester(); advanceUntilIdle()
        assertEquals("自定义学期", repository.saved!!.name); assertEquals("2026-09-02", repository.saved!!.startDate); assertEquals(20, repository.saved!!.totalWeeks); assertEquals(11, repository.saved!!.periods.size)
    }

    @Test fun failedLoadRetriesAndExistingSemesterBuildsEditableDraftOnce() = runTest {
        val repository = FakeScheduleRepository().also { it.failLoad = true }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        assertEquals(LoadStatus.FAILED, model.state.value.loadStatus); assertNull(model.form.value)
        repository.failLoad = false
        repository.data = repository.data.copy(
            semester = Semester("s", "已有", "2026-09-01", 18, listOf(Period(2, "08:55", "09:40"), Period(1, "10:00", "10:45"))),
        )
        model.retryLoad(); advanceUntilIdle(); assertEquals(LoadStatus.READY, model.state.value.loadStatus)
        val form = model.form.value!!
        assertEquals("已有", form.name); assertEquals(LocalDate.parse("2026-09-01"), form.startDate); assertEquals(18, form.totalWeeks)
        assertEquals(listOf(2, 1), form.periods.map { it.number })
        assertTrue(form.periodsExpanded)
        assertEquals(2, repository.loadCalls)
    }

    @Test fun existingSemesterDraftKeepsUnsavedEditsAcrossTabsClockRefreshAndReload() = runTest {
        val repository = FakeScheduleRepository().also {
            it.data = ScheduleData(1, Semester("term", "秋季", "2026-09-01", 16, listOf(Period(2, "08:55", "09:40"), Period(1, "10:00", "10:45"))), emptyList(), "now")
        }
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-09-01T09:00") }, idFactory = ids())
        advanceUntilIdle()
        assertTrue(model.form.value!!.periodsExpanded)
        model.updateName("未保存的名字"); model.updateTotalWeeks(12)
        model.togglePeriods(); model.selectTab(MainTab.SETTINGS)
        model.refreshCurrentTime(); model.retryLoad(); advanceUntilIdle()
        assertEquals("未保存的名字", model.form.value!!.name)
        assertEquals(12, model.form.value!!.totalWeeks)
        assertEquals(listOf(2, 1), model.form.value!!.periods.map { it.number })
        assertFalse(model.form.value!!.periodsExpanded)
        assertEquals(MainTab.SETTINGS, model.selectedTab.value)
        assertEquals(2, repository.loadCalls)
        assertEquals(0, repository.saveCalls)
    }

    @Test fun semesterSaveReportsCourseBreakingChangesKeepsDraftAndWritesPreservedNumbers() = runTest {
        val course = Course("course", "课", "", "#287B74", listOf(com.qingke.schedule.domain.CourseSchedule("s", 1, 1, 2, 1, 16, com.qingke.schedule.domain.RepeatRule.EVERY, "")))
        val semester = Semester("term", "秋季", "2026-09-01", 16, listOf(Period(2, "08:55", "09:40"), Period(1, "10:00", "10:45")))
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, semester, listOf(course), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()

        model.updateName("改名"); model.updateTotalWeeks(20)
        val used = model.form.value!!.periods[1]
        model.updatePeriodStart(used.id, LocalTime.of(10, 5)); model.updatePeriodEnd(used.id, LocalTime.of(10, 50))
        model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertEquals("term", repository.saved!!.id)
        assertEquals("改名", repository.saved!!.name)
        assertEquals(listOf(2, 1), repository.saved!!.periods.map { it.number })
        assertEquals(listOf("08:55" to "09:40", "10:05" to "10:50"), repository.saved!!.periods.map { it.startTime to it.endTime })
        assertEquals("SYSTEM // 学期与节次设置已保存", model.semesterSuccess.value)
        assertEquals("改名", model.state.value.data.semester!!.name)
        assertEquals(listOf(course), model.state.value.data.courses)
        model.consumeSemesterSuccess(); assertNull(model.semesterSuccess.value)

        model.updateTotalWeeks(10); model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertTrue(model.form.value!!.validationMessage!!.contains("周次"))

        model.updateTotalWeeks(20)
        model.removePeriod(model.form.value!!.periods.first().id)
        model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertTrue(model.form.value!!.validationMessage!!.contains("节次"))
        assertEquals(1, model.form.value!!.periods.size)
        assertEquals(20, model.form.value!!.totalWeeks)
        assertEquals("改名", model.form.value!!.name)
    }

    @Test fun deletingAPeriodThatShiftsAReferencedNumberIsRejectedAndKeepsDraft() = runTest {
        val course = Course("course", "课", "", "#287B74", listOf(com.qingke.schedule.domain.CourseSchedule("s", 1, 2, 2, 1, 18, com.qingke.schedule.domain.RepeatRule.EVERY, "")))
        val semester = Semester("term", "秋季", "2026-09-01", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"),
        ))
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, semester, listOf(course), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()

        model.removePeriod(model.form.value!!.periods.first().id)
        model.saveSemester(); advanceUntilIdle()
        assertEquals(0, repository.saveCalls)
        assertTrue(model.form.value!!.validationMessage!!.contains("请先在课程编辑中调整"))
        assertEquals(listOf(1, 2), model.form.value!!.periods.map { it.number })
        assertEquals(semester, repository.data.semester)
    }

    @Test fun onboardingSaveRebasesPeriodIdentitySoCoursesCanLaterReferenceThoseNumbers() = runTest {
        val repository = FakeScheduleRepository()
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-07-01T09:00") }, idFactory = ids())
        advanceUntilIdle()
        model.updateName("新学期")
        model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertEquals((1..10).toList(), repository.data.semester!!.periods.map { it.number })

        val course = Course("course", "课", "", "#287B74", listOf(com.qingke.schedule.domain.CourseSchedule("s", 1, 3, 3, 1, 18, com.qingke.schedule.domain.RepeatRule.EVERY, "")))
        repository.data = repository.data.copy(courses = listOf(course))
        model.retryLoad(); advanceUntilIdle()
        assertEquals("新学期", model.form.value!!.name)

        model.updateName("改名")
        val used = model.form.value!!.periods[2]
        model.updatePeriodStart(used.id, LocalTime.of(10, 5))
        model.updatePeriodEnd(used.id, LocalTime.of(10, 50))
        model.saveSemester(); advanceUntilIdle()
        assertEquals(2, repository.saveCalls)
        assertNull(model.form.value!!.validationMessage)
        assertEquals("改名", repository.saved!!.name)
        assertEquals((1..10).toList(), repository.saved!!.periods.map { it.number })
        assertEquals("10:05" to "10:50", repository.saved!!.periods[2].startTime to repository.saved!!.periods[2].endTime)
        assertEquals(listOf(course), model.state.value.data.courses)
    }

    @Test fun savedNewPeriodBecomesTheIdentityBaselineAndDeletingItIsStillRejected() = runTest {
        val semester = Semester("term", "秋季", "2026-09-01", 18, listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")))
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, semester, emptyList(), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()

        model.addPeriod(); model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertEquals(listOf(1, 2, 3), repository.data.semester!!.periods.map { it.number })

        val course = Course("course", "课", "", "#287B74", listOf(com.qingke.schedule.domain.CourseSchedule("s", 1, 3, 3, 1, 18, com.qingke.schedule.domain.RepeatRule.EVERY, "")))
        repository.data = repository.data.copy(courses = listOf(course))
        model.retryLoad(); advanceUntilIdle()

        model.updateName("改名"); model.saveSemester(); advanceUntilIdle()
        assertEquals(2, repository.saveCalls)
        assertNull(model.form.value!!.validationMessage)
        assertEquals("改名", repository.saved!!.name)

        model.removePeriod(model.form.value!!.periods.last().id)
        model.saveSemester(); advanceUntilIdle()
        assertEquals(2, repository.saveCalls)
        assertTrue(model.form.value!!.validationMessage!!.contains("请先在课程编辑中调整"))
        assertEquals(listOf(1, 2), model.form.value!!.periods.map { it.number })
        assertEquals("改名", repository.data.semester!!.name)
    }

    @Test fun failedSemesterSaveKeepsThePersistedBaselineForTheNextJudgement() = runTest {
        val semester = Semester("term", "秋季", "2026-09-01", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"), Period(4, "10:55", "11:40"),
        ))
        val first = com.qingke.schedule.domain.CourseSchedule("s", 1, 1, 1, 1, 18, com.qingke.schedule.domain.RepeatRule.EVERY, "")
        val course = Course("course", "课", "", "#287B74", listOf(first))
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, semester, listOf(course), "now"); it.failSave = true }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()

        model.removePeriod(model.form.value!!.periods[2].id)
        assertEquals(listOf(1, 2, 3), model.form.value!!.periods.map { it.number })
        model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertEquals(semester, repository.data.semester)

        repository.failSave = false
        repository.data = repository.data.copy(courses = listOf(course, Course("other", "另一门", "", "#287B74", listOf(
            first.copy(id = "s3", startPeriod = 3, endPeriod = 3),
        ))))
        model.retryLoad(); advanceUntilIdle()
        model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertTrue(model.form.value!!.validationMessage!!.contains("请先在课程编辑中调整"))
        assertEquals(listOf(1, 2, 3), model.form.value!!.periods.map { it.number })
        assertEquals(semester, repository.data.semester)
    }

    @Test fun editsDuringAnInFlightSemesterSaveAreNotMarkedPersisted() = runTest {
        val semester = Semester("term", "秋季", "2026-09-01", 18, listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")))
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, semester, emptyList(), "now"); it.saveGate = CompletableDeferred() }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        assertTrue(model.form.value!!.periodsExpanded)
        model.togglePeriods()
        model.addPeriod()
        model.saveSemester()
        assertEquals(1, repository.saveCalls)

        model.removePeriod(model.form.value!!.periods.last().id)
        model.addPeriod()
        model.updateName("保存期间改名")
        assertEquals(listOf(1, 2, 3), model.form.value!!.periods.map { it.number })
        repository.saveGate!!.complete(Unit); advanceUntilIdle()

        assertEquals("保存期间改名", model.form.value!!.name)
        assertFalse(model.form.value!!.periodsExpanded)
        assertEquals(listOf(1, 2, 3), repository.data.semester!!.periods.map { it.number })

        val course = Course("course", "课", "", "#287B74", listOf(com.qingke.schedule.domain.CourseSchedule("s", 1, 3, 3, 1, 18, com.qingke.schedule.domain.RepeatRule.EVERY, "")))
        repository.data = repository.data.copy(courses = listOf(course))
        model.retryLoad(); advanceUntilIdle()
        model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertTrue(model.form.value!!.validationMessage!!.contains("请先在课程编辑中调整"))
        assertEquals("保存期间改名", model.form.value!!.name)
    }

    @Test fun onboardingSaveKeepsDraftWithoutPublishingSettingsNotice() = runTest {
        val repository = FakeScheduleRepository()
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.updateName("新学期"); model.saveSemester(); advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertNull(model.semesterSuccess.value)
        assertEquals("新学期", model.form.value!!.name)
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

    @Test fun refreshingClockOnlyUpdatesMemoryAndPreservesDraftTabAndRepositoryState() = runTest {
        var point = LocalDateTime.parse("2026-08-31T09:41:52")
        val repository = FakeScheduleRepository()
        val model = ScheduleViewModel(appState(repository), now = { point }, idFactory = ids())
        advanceUntilIdle()
        model.updateName("保留草稿")
        model.selectTab(MainTab.SETTINGS)
        val before = model.form.value
        point = LocalDateTime.parse("2026-08-31T09:41:53")
        model.refreshCurrentTime()
        assertEquals(point, model.currentTime.value)
        assertEquals(1, repository.loadCalls)
        assertEquals(before, model.form.value)
        assertEquals(MainTab.SETTINGS, model.selectedTab.value)
    }

    @Test fun defaultDraftHasAllTimesInOrderAndUniqueIds() = runTest {
        val model = ScheduleViewModel(appState(FakeScheduleRepository()), now = { LocalDateTime.parse("2026-07-01T09:00") }, idFactory = ids())
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

    @Test fun courseEditorClampsPeriodsCreatesSchedulesAndKeepsDirtyDraftUntilDiscarded() = runTest {
        val repository = FakeScheduleRepository().also {
            it.data = ScheduleData(1, Semester("term", "秋季", "2026-09-01", 18, listOf(Period(1, "08:00", "08:45"))), emptyList(), "now")
        }
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-09-01T09:00") }, idFactory = ids())
        advanceUntilIdle()
        model.openAddCourse()
        assertEquals(CourseEditorMode.CREATE, model.editor.value!!.mode)
        val first = model.editor.value!!.schedules.single().id
        model.updateCourseName("数据结构")
        model.updateCourseScheduleStartPeriod(first, 9)
        assertEquals(1, model.editor.value!!.schedules.single().startPeriod)
        assertEquals(1, model.editor.value!!.schedules.single().endPeriod)
        model.addCourseSchedule()
        assertEquals(2, model.editor.value!!.schedules.size)
        model.requestCloseEditor()
        assertTrue(model.editor.value!!.confirmation is CourseEditorConfirmation.Discard)
        model.confirmDiscardEditor()
        assertNull(model.editor.value)
    }

    @Test fun courseAddRoutesToChooserAndAppendKeepsIdentityReadOnly() = runTest {
        val existing = testCourse("same", "算法", "王老师")
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), listOf(existing), "now") }
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-09-02T09:00") }, idFactory = ids())
        advanceUntilIdle()
        model.openAddCourse(); assertEquals(CourseEditorMode.CHOOSER, model.editor.value!!.mode)
        model.appendCourseAt(0)
        val schedule = model.editor.value!!.visibleSchedules.single()
        model.updateCourseName("不应修改"); model.updateCourseTeacher("不应修改"); model.updateCourseColor("#D96952"); model.updateCourseColorInput("#oops")
        assertEquals("算法", model.editor.value!!.name); assertEquals("王老师", model.editor.value!!.teacher); assertEquals("#287B74", model.editor.value!!.color)
        model.updateCourseScheduleDay(schedule.id, 3)
        assertEquals(3, model.editor.value!!.visibleSchedules.single().dayOfWeek)
    }

    @Test fun editorBackClosesConfirmationBeforeRequestingDiscard() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.openAddCourse(); model.updateCourseName("离散数学"); model.requestCloseEditor()
        assertTrue(model.editor.value!!.confirmation is CourseEditorConfirmation.Discard)
        model.onEditorBack(); assertNull(model.editor.value!!.confirmation); assertNotNull(model.editor.value)
        model.onEditorBack(); assertTrue(model.editor.value!!.confirmation is CourseEditorConfirmation.Discard)
    }

    @Test fun courseSavePublishesSuccessAndEditsSecondDuplicateAtSourceIndex() = runTest {
        val first = testCourse("same", "第一门", "甲")
        val second = testCourse("same", "第二门", "乙", day = 2)
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), listOf(first, second), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.openCourseAt(1); model.updateCourseName("第二门已改"); model.saveCourse(); advanceUntilIdle()
        assertEquals(listOf("第一门", "第二门已改"), repository.data.courses.map { it.name })
        assertEquals("SYSTEM // 课程修改已保存", model.courseSuccess.value); assertNull(model.editor.value)
    }

    @Test fun newCourseSuccessUsesTheExactIosSystemPrefix() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.openNewCourse(); model.updateCourseName("新课程"); model.saveCourse(); advanceUntilIdle()
        assertEquals("SYSTEM // 课程添加成功", model.courseSuccess.value)
        assertNull(model.editor.value)
    }

    @Test fun courseInvalidAndDuplicateKeepEditorWithoutWriting() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.openAddCourse(); model.saveCourse()
        assertEquals(0, repository.courseWrites); assertNotNull(model.editor.value!!.validationMessage)
        model.updateCourseName("课程"); val first = model.editor.value!!.schedules.single(); model.addCourseSchedule(); model.updateCourseScheduleDay(model.editor.value!!.schedules.last().id, first.dayOfWeek)
        model.saveCourse(); assertEquals(0, repository.courseWrites); assertEquals("该上课安排已存在，请勿重复添加", model.editor.value!!.validationMessage)
    }

    @Test fun conflictingConfirmationFreezesCandidateAndSaveFailureKeepsDraft() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), listOf(testCourse("other", "冲突课", "", 1)), "now") }
        val model = ScheduleViewModel(appState(repository), now = { LocalDateTime.parse("2026-09-07T09:00") }, idFactory = ids()); advanceUntilIdle()
        model.openNewCourse(); model.updateCourseName("冻结名"); model.updateCourseScheduleDay(model.editor.value!!.schedules.single().id, 1); model.saveCourse()
        assertTrue(model.editor.value!!.confirmation is CourseEditorConfirmation.Conflicts); model.updateCourseName("后来修改"); model.confirmSaveDespiteConflicts(); advanceUntilIdle()
        assertEquals("冻结名", repository.lastCourse!!.name)
        repository.courseFail = true; model.openNewCourse(); model.updateCourseName("失败保留"); model.updateCourseScheduleDay(model.editor.value!!.schedules.single().id, 2); model.saveCourse(); advanceUntilIdle()
        assertNotNull(model.editor.value); assertEquals("失败保留", model.editor.value!!.name); assertNotNull(model.state.value.error)
    }

    @Test fun deleteCancelSuccessAndStaleFingerprintKeepExactTarget() = runTest {
        val first = testCourse("same", "第一", "", 1); val second = testCourse("same", "第二", "", 2)
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), listOf(first, second), "now") }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.openCourseAt(1); model.requestDeleteCourse(); model.dismissEditorConfirmation(); assertEquals(0, repository.deleteWrites)
        model.requestDeleteCourse(); model.confirmDeleteCourse(); advanceUntilIdle(); assertEquals(listOf("第一"), repository.data.courses.map { it.name }); assertEquals("SYSTEM // 课程删除成功", model.courseSuccess.value)
        repository.data = repository.data.copy(courses = listOf(first, second.copy(name = "外部更新"))); model.retryLoad(); advanceUntilIdle(); model.openCourseAt(1); repository.data = repository.data.copy(courses = listOf(first, second.copy(name = "再次变化"))); model.updateCourseName("不应写入"); model.saveCourse(); advanceUntilIdle()
        assertNotNull(model.editor.value); assertEquals("第一", repository.data.courses.first().name)
    }

    @Test fun appendSuccessAndSuspendedSaveDeleteAreSingleFlight() = runTest {
        val existing = testCourse("id", "原课", "老师", 1)
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), listOf(existing), "now"); it.courseGate = CompletableDeferred() }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.appendCourseAt(0); model.saveCourse(); model.saveCourse(); assertEquals(1, repository.courseWrites)
        repository.courseGate!!.complete(Unit); advanceUntilIdle(); assertEquals("SYSTEM // 添加上课安排成功", model.courseSuccess.value); assertNull(model.editor.value)
        repository.deleteGate = CompletableDeferred(); model.openCourseAt(0); model.requestDeleteCourse(); model.confirmDeleteCourse(); model.confirmDeleteCourse(); assertEquals(1, repository.deleteWrites)
        repository.deleteGate!!.complete(Unit); advanceUntilIdle(); assertEquals("SYSTEM // 课程删除成功", model.courseSuccess.value)
    }

    @Test fun courseCancellationAndDeleteFailureKeepEditorWithoutOrdinaryError() = runTest {
        val course = testCourse("id", "原课", "", 1)
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), listOf(course), "now"); it.courseCancel = true }
        val model = ScheduleViewModel(appState(repository), idFactory = ids()); advanceUntilIdle()
        model.openCourseAt(0); model.updateCourseName("取消后保留"); model.saveCourse(); advanceUntilIdle()
        assertNotNull(model.editor.value); assertFalse(model.editor.value!!.isInFlight); assertNull(model.state.value.error)
        repository.courseCancel = false; repository.deleteFail = true; model.requestDeleteCourse(); model.confirmDeleteCourse(); advanceUntilIdle()
        assertNotNull(model.editor.value); assertFalse(model.editor.value!!.isInFlight); assertNotNull(model.state.value.error)
    }

    @Test
    fun calendarWritesTransformTheLatestStoredPreferencesAndPublishTheResult() = runTest {
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(FakeScheduleRepository(), preferences), now = { LocalDateTime.parse("2026-09-02T09:00") }, idFactory = ids())
        advanceUntilIdle()

        model.setWeekendsAreNonTeachingDays(true)
        model.addNonTeachingDate(LocalDate.parse("2026-10-01"))
        model.addMakeupTeachingDay(LocalDate.parse("2026-10-10"), 4)
        model.setLunchBreakEnabled(false)
        advanceUntilIdle()

        assertEquals(4, preferences.updateCalls)
        val calendar = model.state.value.preferences.academicCalendar
        assertTrue(calendar.weekendsAreNonTeachingDays)
        assertEquals(listOf("2026-10-01"), calendar.nonTeachingDates)
        assertEquals(listOf(MakeupTeachingDay("2026-10-10", 4)), calendar.makeupTeachingDays)
        assertFalse(calendar.lunchBreak.isEnabled)
        assertEquals(calendar, preferences.preferences.academicCalendar)
    }

    @Test
    fun calendarDuplicateDatesDeduplicateSortAndReplaceTheMakeupSourceWeekday() = runTest {
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(FakeScheduleRepository(), preferences), idFactory = ids())
        advanceUntilIdle()

        model.addNonTeachingDate(LocalDate.parse("2026-10-05"))
        model.addNonTeachingDate(LocalDate.parse("2026-10-01"))
        model.addNonTeachingDate(LocalDate.parse("2026-10-05"))
        model.addMakeupTeachingDay(LocalDate.parse("2026-10-10"), 2)
        model.addMakeupTeachingDay(LocalDate.parse("2026-10-10"), 6)
        model.addMakeupTeachingDay(LocalDate.parse("2026-09-26"), 3)
        advanceUntilIdle()

        val calendar = model.state.value.preferences.academicCalendar
        assertEquals(listOf("2026-10-01", "2026-10-05"), calendar.nonTeachingDates)
        assertEquals(listOf(MakeupTeachingDay("2026-09-26", 3), MakeupTeachingDay("2026-10-10", 6)), calendar.makeupTeachingDays)

        model.removeNonTeachingDate("2026-10-01")
        model.removeMakeupTeachingDay("2026-09-26")
        advanceUntilIdle()
        assertEquals(listOf("2026-10-05"), model.state.value.preferences.academicCalendar.nonTeachingDates)
        assertEquals(listOf(MakeupTeachingDay("2026-10-10", 6)), model.state.value.preferences.academicCalendar.makeupTeachingDays)
    }

    @Test
    fun calendarSameDateMutexAppliesThroughTheViewModel() = runTest {
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(FakeScheduleRepository(), preferences), idFactory = ids())
        advanceUntilIdle()

        model.addMakeupTeachingDay(LocalDate.parse("2026-10-01"), 2)
        model.addNonTeachingDate(LocalDate.parse("2026-10-01"))
        advanceUntilIdle()
        assertEquals(listOf("2026-10-01"), model.state.value.preferences.academicCalendar.nonTeachingDates)
        assertEquals(emptyList<MakeupTeachingDay>(), model.state.value.preferences.academicCalendar.makeupTeachingDays)

        model.addMakeupTeachingDay(LocalDate.parse("2026-10-01"), 5)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), model.state.value.preferences.academicCalendar.nonTeachingDates)
        assertEquals(listOf(MakeupTeachingDay("2026-10-01", 5)), model.state.value.preferences.academicCalendar.makeupTeachingDays)
    }

    @Test
    fun invalidLunchRangeIsRejectedWithoutWritingWhileAValidRangePersists() = runTest {
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(FakeScheduleRepository(), preferences), idFactory = ids())
        advanceUntilIdle()

        model.requestLunchBreakTimes(LocalTime.of(14, 0), LocalTime.of(11, 40))
        model.requestLunchBreakTimes(LocalTime.of(12, 0), LocalTime.of(12, 0))
        advanceUntilIdle()
        assertEquals(0, preferences.updateCalls)
        assertEquals(LunchBreakSettings.defaults, model.state.value.preferences.academicCalendar.lunchBreak)

        model.requestLunchBreakTimes(LocalTime.of(12, 0), LocalTime.of(13, 30))
        advanceUntilIdle()
        assertEquals(1, preferences.updateCalls)
        assertEquals("12:00", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        assertEquals("13:30", model.state.value.preferences.academicCalendar.lunchBreak.endTime)
        assertEquals("午休", model.state.value.preferences.academicCalendar.lunchBreak.title)
        assertTrue(model.state.value.preferences.academicCalendar.lunchBreak.isEnabled)
    }

    @Test
    fun conflictingLunchRangeWaitsForOneConfirmationBeforeWriting() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(repository, preferences), idFactory = ids())
        advanceUntilIdle()

        model.requestLunchBreakTimes(LocalTime.of(8, 30), LocalTime.of(9, 0))
        advanceUntilIdle()
        assertEquals(0, preferences.updateCalls)
        assertEquals(
            LunchBreakConflict("08:30", "09:00", persistedPeriodNumbers = listOf(1, 2), draftPeriodNumbers = listOf(1, 2)),
            model.lunchBreakConflict.value,
        )
        assertEquals(LunchBreakSettings.defaults, model.state.value.preferences.academicCalendar.lunchBreak)

        model.confirmLunchBreakDespiteConflicts()
        advanceUntilIdle()
        assertEquals(1, preferences.updateCalls)
        assertNull(model.lunchBreakConflict.value)
        assertEquals("08:30", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        assertEquals("09:00", model.state.value.preferences.academicCalendar.lunchBreak.endTime)
        assertTrue(model.state.value.preferences.academicCalendar.lunchBreak.isEnabled)
    }

    @Test
    fun dismissedLunchBreakConflictWritesNothingAndClearsTheWarning() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(repository, preferences), idFactory = ids())
        advanceUntilIdle()

        model.requestLunchBreakTimes(LocalTime.of(8, 0), LocalTime.of(10, 0))
        advanceUntilIdle()
        assertNotNull(model.lunchBreakConflict.value)

        model.dismissLunchBreakConfirmation()
        model.confirmLunchBreakDespiteConflicts()
        advanceUntilIdle()
        assertNull(model.lunchBreakConflict.value)
        assertEquals(0, preferences.updateCalls)
        assertEquals(LunchBreakSettings.defaults, model.state.value.preferences.academicCalendar.lunchBreak)
    }

    @Test
    fun lunchRangeTouchingPeriodEdgesIsWrittenWithoutAConflict() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(repository, preferences), idFactory = ids())
        advanceUntilIdle()

        model.requestLunchBreakTimes(LocalTime.of(9, 40), LocalTime.of(10, 0))
        advanceUntilIdle()

        assertNull(model.lunchBreakConflict.value)
        assertEquals(1, preferences.updateCalls)
        assertEquals("09:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        assertEquals("10:00", model.state.value.preferences.academicCalendar.lunchBreak.endTime)
    }

    @Test
    fun firstBootLunchConflictUsesTheVisibleDraftPeriodsAndWaitsForConfirmation() = runTest {
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(FakeScheduleRepository(), preferences), now = { LocalDateTime.parse("2026-07-01T09:00") }, idFactory = ids())
        advanceUntilIdle()
        val form = model.form.value!!
        assertEquals(10, form.periods.size)

        model.requestLunchBreakTimes(LocalTime.of(8, 40), LocalTime.of(9, 0))
        advanceUntilIdle()

        assertEquals(0, preferences.updateCalls)
        val conflict = model.lunchBreakConflict.value!!
        assertEquals("08:40", conflict.startTime)
        assertEquals("09:00", conflict.endTime)
        assertEquals(emptyList<Int>(), conflict.persistedPeriodNumbers)
        assertEquals(listOf(1, 2), conflict.draftPeriodNumbers)
        assertFalse("the still unsaved semester cannot hide a week row yet", conflict.hidesWeekMatrixRow)
        assertEquals(LunchBreakSettings.defaults, model.state.value.preferences.academicCalendar.lunchBreak)

        model.confirmLunchBreakDespiteConflicts()
        advanceUntilIdle()
        assertEquals(1, preferences.updateCalls)
        assertNull(model.lunchBreakConflict.value)
        assertEquals("08:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        assertEquals("09:00", model.state.value.preferences.academicCalendar.lunchBreak.endTime)
    }

    @Test
    fun settingsDraftPeriodEditsParticipateInTheLunchConflictCheck() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val preferences = FakePreferencesRepository()
        val model = ScheduleViewModel(appState(repository, preferences), idFactory = ids())
        advanceUntilIdle()
        val periods = model.form.value!!.periods
        assertEquals(listOf(1, 2), periods.map { it.number })

        model.updatePeriodStart(periods[0].id, LocalTime.of(7, 0))
        model.updatePeriodEnd(periods[0].id, LocalTime.of(7, 45))
        model.requestLunchBreakTimes(LocalTime.of(8, 20), LocalTime.of(8, 30))
        advanceUntilIdle()

        val persistedOnly = model.lunchBreakConflict.value!!
        assertEquals("the saved semester still overlaps the candidate", listOf(1), persistedOnly.persistedPeriodNumbers)
        assertEquals("the edited draft no longer overlaps it", emptyList<Int>(), persistedOnly.draftPeriodNumbers)
        assertTrue(persistedOnly.hidesWeekMatrixRow)
        assertEquals(0, preferences.updateCalls)

        model.updatePeriodEnd(periods[0].id, LocalTime.of(8, 45))
        model.dismissLunchBreakConfirmation()
        model.requestLunchBreakTimes(LocalTime.of(8, 20), LocalTime.of(8, 30))
        advanceUntilIdle()
        val bothSources = model.lunchBreakConflict.value!!
        assertEquals(listOf(1), bothSources.persistedPeriodNumbers)
        assertEquals(listOf(1), bothSources.draftPeriodNumbers)
        assertEquals(listOf(1), bothSources.periodNumbers)

        model.updatePeriodStart(periods[0].id, LocalTime.of(7, 0))
        model.updatePeriodEnd(periods[0].id, LocalTime.of(7, 45))
        model.updatePeriodEnd(periods[1].id, LocalTime.of(12, 0))
        model.dismissLunchBreakConfirmation()
        model.requestLunchBreakTimes(LocalTime.of(11, 40), LocalTime.of(14, 0))
        advanceUntilIdle()

        val draftOnly = model.lunchBreakConflict.value!!
        assertEquals(emptyList<Int>(), draftOnly.persistedPeriodNumbers)
        assertEquals(listOf(2), draftOnly.draftPeriodNumbers)
        assertFalse("the saved semester still shows the row", draftOnly.hidesWeekMatrixRow)
        assertEquals(0, preferences.updateCalls)

        model.dismissLunchBreakConfirmation()
        model.requestLunchBreakTimes(LocalTime.of(12, 30), LocalTime.of(13, 30))
        advanceUntilIdle()
        assertNull(model.lunchBreakConflict.value)
        assertEquals(1, preferences.updateCalls)
        assertEquals("12:30", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
    }

    @Test
    fun confirmLunchBreakWriteFailureKeepsARetryableConfirmationAndTheStoredValue() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val preferences = FakePreferencesRepository().also { it.failUpdate = true }
        val model = ScheduleViewModel(appState(repository, preferences), idFactory = ids())
        advanceUntilIdle()

        model.requestLunchBreakTimes(LocalTime.of(8, 30), LocalTime.of(9, 0))
        advanceUntilIdle()
        model.confirmLunchBreakDespiteConflicts()
        advanceUntilIdle()

        assertEquals(1, preferences.updateCalls)
        val conflict = model.lunchBreakConflict.value
        assertNotNull("a failed write must stay retryable", conflict)
        assertEquals("08:30", conflict!!.startTime)
        assertEquals(LunchBreakSettings.defaults, model.state.value.preferences.academicCalendar.lunchBreak)
        assertFalse(model.state.value.isSaving)
        assertNotNull(model.state.value.error)

        preferences.failUpdate = false
        model.confirmLunchBreakDespiteConflicts()
        advanceUntilIdle()

        assertEquals(2, preferences.updateCalls)
        assertNull(model.lunchBreakConflict.value)
        assertEquals("08:30", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        assertEquals("09:00", model.state.value.preferences.academicCalendar.lunchBreak.endTime)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.isSaving)
    }

    @Test
    fun cancelledLunchBreakConfirmationKeepsTheStoredValueAndThePendingWarning() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val preferences = FakePreferencesRepository().also { it.cancelUpdate = true }
        val model = ScheduleViewModel(appState(repository, preferences), idFactory = ids())
        advanceUntilIdle()

        model.requestLunchBreakTimes(LocalTime.of(8, 30), LocalTime.of(9, 0))
        advanceUntilIdle()
        model.confirmLunchBreakDespiteConflicts()
        advanceUntilIdle()

        assertNotNull(model.lunchBreakConflict.value)
        assertEquals(LunchBreakSettings.defaults, model.state.value.preferences.academicCalendar.lunchBreak)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.isSaving)
    }

    @Test
    fun lunchBreakConfirmationIsSingleFlightAndIgnoresDismissWhileWriting() = runTest {
        val repository = FakeScheduleRepository().also { it.data = ScheduleData(1, testSemester(), emptyList(), "now") }
        val preferences = FakePreferencesRepository().also { it.updateGate = CompletableDeferred() }
        val model = ScheduleViewModel(appState(repository, preferences), idFactory = ids())
        advanceUntilIdle()

        model.requestLunchBreakTimes(LocalTime.of(8, 30), LocalTime.of(9, 0))
        advanceUntilIdle()
        model.confirmLunchBreakDespiteConflicts()
        model.confirmLunchBreakDespiteConflicts()
        model.dismissLunchBreakConfirmation()
        assertEquals(1, preferences.updateCalls)
        assertNotNull(model.lunchBreakConflict.value)

        preferences.updateGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, preferences.updateCalls)
        assertNull(model.lunchBreakConflict.value)
        assertEquals("08:30", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
    }

    @Test
    fun calendarWriteFailureKeepsTheStoredValueAndReportsTheError() = runTest {
        val preferences = FakePreferencesRepository().also { it.failUpdate = true }
        val model = ScheduleViewModel(appState(FakeScheduleRepository(), preferences), idFactory = ids())
        advanceUntilIdle()

        model.setWeekendsAreNonTeachingDays(true)
        model.addNonTeachingDate(LocalDate.parse("2026-10-01"))
        advanceUntilIdle()

        assertFalse(model.state.value.preferences.academicCalendar.weekendsAreNonTeachingDays)
        assertEquals(emptyList<String>(), model.state.value.preferences.academicCalendar.nonTeachingDates)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.isSaving)
    }

    @Test
    fun calendarWriteCancellationKeepsThePreviousStateWithoutAnOrdinaryError() = runTest {
        val preferences = FakePreferencesRepository().also { it.cancelUpdate = true }
        val model = ScheduleViewModel(appState(FakeScheduleRepository(), preferences), idFactory = ids())
        advanceUntilIdle()

        model.addNonTeachingDate(LocalDate.parse("2026-10-01"))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), model.state.value.preferences.academicCalendar.nonTeachingDates)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.isSaving)
    }

    private fun testSemester() = Semester("term", "秋季", "2026-09-01", 18, listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")))
    private fun testCourse(id: String, name: String, teacher: String, day: Int = 1) = Course(id, name, teacher, "#287B74", listOf(com.qingke.schedule.domain.CourseSchedule("schedule-$name", day, 1, 1, 1, 18, com.qingke.schedule.domain.RepeatRule.EVERY, "A101")))

    private fun appState(repository: FakeScheduleRepository) = ScheduleAppState(repository, object : SchedulePreferencesRepository {
        override suspend fun load() = SchedulePreferences.defaults
        override suspend fun save(preferences: SchedulePreferences) = preferences
        override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences) = transform(SchedulePreferences.defaults)
    })

    private fun appState(repository: FakeScheduleRepository, preferences: SchedulePreferencesRepository) = ScheduleAppState(repository, preferences)

    /** Stateful preferences fake: `update` transforms the currently stored value, like DataStore does. */
    private class FakePreferencesRepository(initial: SchedulePreferences = SchedulePreferences.defaults) : SchedulePreferencesRepository {
        var preferences = initial
        var updateCalls = 0
        var failUpdate = false
        var cancelUpdate = false
        var updateGate: CompletableDeferred<Unit>? = null
        override suspend fun load(): SchedulePreferences = preferences
        override suspend fun save(preferences: SchedulePreferences): SchedulePreferences {
            updateCalls++
            updateGate?.await()
            if (cancelUpdate) throw CancellationException("preferences")
            if (failUpdate) error("preferences")
            this.preferences = preferences
            return preferences
        }
        override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences): SchedulePreferences = save(transform(preferences))
    }

    private class FakeScheduleRepository : ScheduleRepository {
        var data = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z")
        var loadCalls = 0; var saveCalls = 0; var courseWrites = 0; var deleteWrites = 0; var courseFail = false; var failLoad = false; var failSave = false; var cancelSave = false; var saved: Semester? = null; var lastCourse: Course? = null
        var loadGate: CompletableDeferred<Unit>? = null; var saveGate: CompletableDeferred<Unit>? = null; var courseGate: CompletableDeferred<Unit>? = null; var deleteGate: CompletableDeferred<Unit>? = null; var deleteFail = false; var courseCancel = false
        override suspend fun load(): ScheduleData { loadCalls++; loadGate?.await(); if (failLoad) error("load") ; return data }
        override suspend fun replace(data: ScheduleData) = data
        override suspend fun saveSemester(semester: Semester): ScheduleData { saveCalls++; saveGate?.await(); if (cancelSave) throw CancellationException("cancelled"); if (failSave) error("save"); saved = semester; return data.copy(semester = semester).also { data = it } }
        override suspend fun saveCourse(course: Course): ScheduleData {
            courseWrites++; courseGate?.await(); if (courseCancel) throw CancellationException("course"); if (courseFail) error("course") ; lastCourse = course
            val index = data.courses.indexOfFirst { it.id == course.id }
            data = data.copy(courses = if (index < 0) data.courses + course else data.courses.toMutableList().also { it[index] = course })
            return data
        }
        override suspend fun saveCourseAt(index: Int, expected: Course, course: Course): ScheduleData {
            courseWrites++; courseGate?.await(); if (courseCancel) throw CancellationException("course"); if (courseFail) error("course"); lastCourse = course
            require(data.courses.getOrNull(index) == expected)
            data = data.copy(courses = data.courses.toMutableList().also { it[index] = course }); return data
        }
        override suspend fun deleteCourseAt(index: Int, expected: Course): ScheduleData {
            deleteWrites++; deleteGate?.await(); if (deleteFail) error("delete")
            require(data.courses.getOrNull(index) == expected)
            data = data.copy(courses = data.courses.toMutableList().also { it.removeAt(index) }); return data
        }
        override suspend fun deleteCourse(id: String) = data
    }

    private fun ids(): () -> String { var number = 0; return { "id-${number++}" } }
}
