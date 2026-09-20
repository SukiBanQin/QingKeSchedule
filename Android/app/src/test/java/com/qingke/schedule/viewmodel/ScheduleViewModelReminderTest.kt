package com.qingke.schedule.viewmodel

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.ReminderPreferences
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.reminder.CourseReminderIdentity
import com.qingke.schedule.reminder.ReminderAlarm
import com.qingke.schedule.reminder.ReminderAvailability
import com.qingke.schedule.reminder.ReminderControl
import com.qingke.schedule.reminder.ReminderReconcileReason
import com.qingke.schedule.reminder.ReminderReconciliation
import com.qingke.schedule.reminder.ReminderStatusSnapshot
import com.qingke.schedule.state.ScheduleAppState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A08 second batch: the reminder settings entry in the ViewModel. The preference is always written first and a
 * failed reminder update may never roll back the schedule or the preference.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelReminderTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeReminderScheduleRepository()
    private val preferences = FakeReminderPreferencesRepository()
    private val control = RecordingReminderControl()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun enablingRemindersPersistsThePreferenceAndReconciles() = runTest {
        val model = model()
        advanceUntilIdle()
        assertFalse(model.reminderUi.value.remindersEnabled)

        model.setRemindersEnabled(true)
        advanceUntilIdle()

        assertEquals(listOf(ReminderReconcileReason.PREFERENCES_CHANGED), control.reconcileReasons)
        assertTrue(preferences.preferences.reminder.remindersEnabled)
        assertTrue(model.reminderUi.value.remindersEnabled)
        assertEquals(2, model.reminderUi.value.activeCount)
        assertTrue(model.reminderUi.value.statusMessage.contains("已安排最近 2 条课程提醒"))
    }

    @Test fun disablingRemindersCancelsEverythingAndReportsThePartialFailure() = runTest {
        preferences.preferences = SchedulePreferences.defaults.copy(reminder = ReminderPreferences(remindersEnabled = true))
        control.cancelAllResult = ReminderReconciliation(
            reason = ReminderReconcileReason.PREFERENCES_CHANGED,
            generation = 4,
            remindersEnabled = false,
            availability = ReminderAvailability(true, true, false),
            failed = listOf("failed-uri"),
            activeAlarms = listOf(reminderAlarm(0, exact = false)),
        )
        val model = model()
        advanceUntilIdle()

        model.setRemindersEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(ReminderReconcileReason.PREFERENCES_CHANGED), control.cancelAllReasons)
        assertTrue(control.reconcileReasons.isEmpty())
        assertFalse(preferences.preferences.reminder.remindersEnabled)
        assertEquals(1, model.reminderUi.value.activeCount)
        assertEquals(1, model.reminderUi.value.lastFailureCount)
        assertTrue(model.reminderUi.value.retriesLater)
        assertTrue(model.reminderUi.value.degraded)
        assertEquals("提醒已关闭", model.reminderUi.value.statusMessage)
    }

    @Test fun leadTimeSelectionsFollowTheStoredPreferenceAndRejectInvalidMinutes() = runTest {
        val model = model()
        advanceUntilIdle()

        model.setReminderLeadMinutes(30, false)
        advanceUntilIdle()
        assertEquals(30, preferences.preferences.reminder.reminderLeadMinutes)
        assertFalse(preferences.preferences.reminder.usesCustomLeadTime)

        model.setReminderLeadMinutes(45, true)
        advanceUntilIdle()
        assertEquals(45, preferences.preferences.reminder.reminderLeadMinutes)
        assertTrue(preferences.preferences.reminder.usesCustomLeadTime)
        assertEquals(45, model.reminderUi.value.leadMinutes)
        assertTrue(model.reminderUi.value.usesCustomLeadTime)
        assertEquals(2, control.reconcileReasons.size)

        model.setReminderLeadMinutes(181, true)
        advanceUntilIdle()
        assertEquals(45, preferences.preferences.reminder.reminderLeadMinutes)
        assertEquals(2, control.reconcileReasons.size)
    }

    @Test fun aCustomLeadTimeOutsideThePresetsIsStoredAsCustom() = runTest {
        val model = model()
        advanceUntilIdle()

        model.setReminderLeadMinutes(20)
        advanceUntilIdle()

        assertEquals(20, preferences.preferences.reminder.reminderLeadMinutes)
        assertTrue(preferences.preferences.reminder.usesCustomLeadTime)
        assertEquals(1, control.reconcileReasons.size)
    }

    @Test fun committedScheduleWritesReconcileWhileDraftsDoNot() = runTest {
        val model = model()
        advanceUntilIdle()

        model.openNewCourse()
        model.updateCourseName("只改草稿")
        advanceUntilIdle()
        assertTrue("a draft edit must not touch the platform reminders", control.reconcileReasons.isEmpty())

        model.saveCourse()
        advanceUntilIdle()
        assertEquals(listOf(ReminderReconcileReason.DATA_SAVED), control.reconcileReasons)

        model.openCourseAt(0)
        model.requestDeleteCourse()
        model.confirmDeleteCourse()
        advanceUntilIdle()
        assertEquals(
            listOf(ReminderReconcileReason.DATA_SAVED, ReminderReconcileReason.DATA_SAVED),
            control.reconcileReasons,
        )

        model.saveSemester()
        advanceUntilIdle()
        assertEquals(3, control.reconcileReasons.size)
        assertEquals(ReminderReconcileReason.DATA_SAVED, control.reconcileReasons.last())
    }

    @Test fun aFailedScheduleWriteNeverReconcilesAndKeepsTheStoredData() = runTest {
        val model = model()
        advanceUntilIdle()
        repository.courseFail = true

        model.openNewCourse()
        model.updateCourseName("会失败")
        model.saveCourse()
        advanceUntilIdle()

        assertTrue(control.reconcileReasons.isEmpty())
        assertEquals(listOf("已有课程"), repository.data.courses.map { it.name })
        assertNotNull(model.state.value.error)
    }

    @Test fun calendarWritesReconcileAndPreferenceFailuresNeverRollBack() = runTest {
        val model = model()
        advanceUntilIdle()

        model.setWeekendsAreNonTeachingDays(true)
        advanceUntilIdle()
        assertTrue(preferences.preferences.academicCalendar.weekendsAreNonTeachingDays)
        assertEquals(listOf(ReminderReconcileReason.PREFERENCES_CHANGED), control.reconcileReasons)

        preferences.failUpdate = true
        model.setLunchBreakEnabled(false)
        advanceUntilIdle()
        assertEquals(1, control.reconcileReasons.size)
        assertTrue(preferences.preferences.academicCalendar.lunchBreak.isEnabled)
        assertNotNull(model.state.value.error)
    }

    @Test fun reminderStatusReflectsCapabilitiesAndScheduledCount() = runTest {
        preferences.preferences = SchedulePreferences.defaults.copy(reminder = ReminderPreferences(remindersEnabled = true))
        control.snapshotResult = ReminderStatusSnapshot(
            availability = ReminderAvailability(notificationsPermitted = false, channelReady = true, exactAlarmsAvailable = false),
            activeAlarms = listOf(reminderAlarm(0, exact = false)),
        )
        val model = model()
        advanceUntilIdle()

        val state = model.reminderUi.value
        assertTrue(state.remindersEnabled)
        assertTrue(state.loaded)
        assertFalse(state.notificationsPermitted)
        assertTrue(state.asksForNotificationPermission)
        assertTrue(state.showsInexactNote)
        assertEquals("系统通知权限未开启，提醒不会投递", state.statusMessage)

        control.snapshotResult = ReminderStatusSnapshot(
            availability = ReminderAvailability(true, channelReady = false, exactAlarmsAvailable = true),
            activeAlarms = emptyList(),
        )
        model.refreshReminderStatus()
        advanceUntilIdle()
        assertEquals("提醒渠道不可用，提醒不会投递", model.reminderUi.value.statusMessage)

        control.snapshotResult = ReminderStatusSnapshot(
            availability = ReminderAvailability(true, true, false),
            activeAlarms = listOf(reminderAlarm(0, exact = false), reminderAlarm(1, exact = false), reminderAlarm(2)),
        )
        model.refreshReminderStatus()
        advanceUntilIdle()
        assertEquals("已安排最近 3 条课程提醒（部分可能延迟）", model.reminderUi.value.statusMessage)
        assertTrue(model.reminderUi.value.degraded)
        assertEquals(3, model.reminderUi.value.activeCount)
    }

    @Test fun aDisabledReminderReportsTheStoredStateWithoutAlarms() = runTest {
        control.snapshotResult = ReminderStatusSnapshot(ReminderAvailability(false, false, false), emptyList())
        val model = model()
        advanceUntilIdle()

        assertEquals("提醒已关闭", model.reminderUi.value.statusMessage)
        assertFalse(model.reminderUi.value.asksForNotificationPermission)
        assertFalse(model.reminderUi.value.showsInexactNote)
    }

    @Test fun reminderUpdateFailuresAreDiagnosticsAndNeverRollBackThePreference() = runTest {
        control.failReconcile = true
        val model = model()
        advanceUntilIdle()

        model.setRemindersEnabled(true)
        advanceUntilIdle()

        assertTrue(preferences.preferences.reminder.remindersEnabled)
        assertTrue(model.reminderUi.value.remindersEnabled)
        assertNotNull(model.reminderUi.value.diagnostic)
        assertTrue(model.reminderUi.value.statusMessage.startsWith("课表已保存，但提醒更新失败："))
    }

    @Test fun anUnreadableReminderStatusSurfacesAsADiagnostic() = runTest {
        control.failSnapshot = true
        val model = model()
        advanceUntilIdle()

        assertTrue(model.reminderUi.value.loaded)
        assertNotNull(model.reminderUi.value.diagnostic)
        assertNull(model.state.value.error)
    }

    private fun model(): ScheduleViewModel = ScheduleViewModel(
        appState = ScheduleAppState(repository, preferences),
        reminders = control,
        now = { LocalDateTime.parse("2026-09-01T09:00") },
        idFactory = ids(),
    )

    private fun ids(): () -> String {
        var number = 0
        return { "id-" + number++ }
    }
}

private fun remainderSemester() = Semester(
    "term",
    "秋季学期",
    "2026-09-01",
    18,
    listOf(Period(1, "08:00", "08:45")),
)

private fun reminderAlarm(index: Int, exact: Boolean = true) = ReminderAlarm(
    identity = CourseReminderIdentity(
        courseIndex = index,
        scheduleIndex = 0,
        courseId = "c" + index,
        scheduleId = "s1",
        week = 1,
        date = LocalDate.parse("2026-09-07"),
        isMakeup = false,
    ),
    fireAt = Instant.parse("2026-09-07T00:50:00Z"),
    startAt = Instant.parse("2026-09-07T01:00:00Z"),
    title = "数学",
    body = "08:00–08:45",
    exact = exact,
)

private class RecordingReminderControl : ReminderControl {
    var snapshotResult = ReminderStatusSnapshot(
        availability = ReminderAvailability(true, true, true),
        activeAlarms = listOf(reminderAlarm(0), reminderAlarm(1)),
    )
    var reconcileResult = ReminderReconciliation(
        reason = ReminderReconcileReason.PREFERENCES_CHANGED,
        generation = 2,
        remindersEnabled = true,
        availability = ReminderAvailability(true, true, true),
        activeAlarms = listOf(reminderAlarm(0), reminderAlarm(1)),
    )
    var cancelAllResult = ReminderReconciliation(
        reason = ReminderReconcileReason.PREFERENCES_CHANGED,
        generation = 3,
        remindersEnabled = false,
        availability = ReminderAvailability(true, true, true),
    )
    var failSnapshot = false
    var failReconcile = false
    val reconcileReasons = mutableListOf<ReminderReconcileReason>()
    val cancelAllReasons = mutableListOf<ReminderReconcileReason>()

    override suspend fun snapshot(): ReminderStatusSnapshot {
        if (failSnapshot) error("status unavailable")
        return snapshotResult
    }

    override suspend fun reconcile(reason: ReminderReconcileReason): ReminderReconciliation {
        if (failReconcile) error("reconcile failed")
        reconcileReasons += reason
        return reconcileResult
    }

    override suspend fun cancelAll(reason: ReminderReconcileReason): ReminderReconciliation {
        if (failReconcile) error("cancel failed")
        cancelAllReasons += reason
        return cancelAllResult
    }
}

private class FakeReminderScheduleRepository : ScheduleRepository {
    var data = ScheduleData(
        1,
        remainderSemester(),
        listOf(
            Course(
                "existing",
                "已有课程",
                "",
                "#287B74",
                listOf(CourseSchedule("s1", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")),
            ),
        ),
        "1970-01-01T00:00:00Z",
    )
    var courseFail = false

    override suspend fun load(): ScheduleData = data

    override suspend fun replace(data: ScheduleData): ScheduleData = data.also { this.data = it }

    override suspend fun saveSemester(semester: Semester): ScheduleData =
        data.copy(semester = semester).also { data = it }

    override suspend fun saveSemesterWithCourses(semester: Semester, courses: List<Course>): ScheduleData =
        data.copy(semester = semester, courses = courses).also { data = it }

    override suspend fun saveCourse(course: Course): ScheduleData {
        if (courseFail) error("course write failed")
        val index = data.courses.indexOfFirst { it.id == course.id }
        return data.copy(courses = if (index < 0) data.courses + course else data.courses.toMutableList().also { it[index] = course })
            .also { data = it }
    }

    override suspend fun saveCourseAt(index: Int, expected: Course, course: Course): ScheduleData =
        data.copy(courses = data.courses.toMutableList().also { it[index] = course }).also { data = it }

    override suspend fun deleteCourseAt(index: Int, expected: Course): ScheduleData =
        data.copy(courses = data.courses.toMutableList().also { it.removeAt(index) }).also { data = it }

    override suspend fun deleteCourse(id: String): ScheduleData = data
}

private class FakeReminderPreferencesRepository : SchedulePreferencesRepository {
    var preferences = SchedulePreferences.defaults
    var failUpdate = false

    override suspend fun load(): SchedulePreferences = preferences

    override suspend fun save(preferences: SchedulePreferences): SchedulePreferences {
        if (failUpdate) error("preferences write failed")
        this.preferences = preferences
        return preferences
    }

    override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences): SchedulePreferences =
        save(transform(preferences))
}
