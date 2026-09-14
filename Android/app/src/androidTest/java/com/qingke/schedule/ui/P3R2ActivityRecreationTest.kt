package com.qingke.schedule.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.state.ScheduleAppState
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.ScheduleViewModel
import com.qingke.schedule.viewmodel.CourseEditorMode
import com.qingke.schedule.viewmodel.CourseEditorConfirmation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P3R2ActivityRecreationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun activityRecreationKeepsActivityViewModelDraftAndSelectedTab() {
        var ids = 0
        val repository = HostRepository()
        val preferences = object : SchedulePreferencesRepository {
            override suspend fun load() = SchedulePreferences.defaults
            override suspend fun save(preferences: SchedulePreferences) = preferences
            override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences) = transform(SchedulePreferences.defaults)
        }
        var creations = 0
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                creations++
                return ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDate.parse("2026-07-01").atStartOfDay() }, { "recreate-${ids++}" }) as T
            }
        }
        val originalModel = ViewModelProvider(rule.activity, factory)[ScheduleViewModel::class.java]
        rule.setContent { QingKeApp(originalModel) }
        rule.waitUntil(5_000) { originalModel.form.value != null }
        val first = originalModel.form.value!!.periods.first().id
        rule.onNodeWithTag("semester-name").performTextReplacement("重建保留")
        rule.onNodeWithTag("daily-periods-toggle").performClick()
        originalModel.updatePeriodStart(first, LocalTime.of(7, 20))
        rule.onNodeWithTag("period-$first-start").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        lateinit var firstRecreatedModel: ScheduleViewModel
        rule.activityRule.scenario.onActivity { activity ->
            firstRecreatedModel = ViewModelProvider(activity, factory)[ScheduleViewModel::class.java]
            assertSame(originalModel, firstRecreatedModel)
            activity.setContent { QingKeApp(firstRecreatedModel) }
        }
        assertEquals("重建保留", firstRecreatedModel.form.value!!.name)
        assertTrue(firstRecreatedModel.form.value!!.periodsExpanded)
        assertEquals(LocalTime.of(7, 20), firstRecreatedModel.form.value!!.periods.first().start)
        rule.onNodeWithTag("semester-name").assertTextContains("重建保留")
        rule.onNodeWithTag("period-$first-start").assertIsDisplayed()
        rule.onNodeWithText("07:20").assertIsDisplayed()
        assertEquals(1, creations)

        firstRecreatedModel.saveSemester()
        rule.waitUntil(5_000) { !firstRecreatedModel.state.value.needsOnboarding }
        firstRecreatedModel.selectTab(MainTab.SETTINGS)
        rule.onNodeWithText("设置（壳层）").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        lateinit var secondRecreatedModel: ScheduleViewModel
        rule.activityRule.scenario.onActivity { activity ->
            secondRecreatedModel = ViewModelProvider(activity, factory)[ScheduleViewModel::class.java]
            assertSame(originalModel, secondRecreatedModel)
            activity.setContent { QingKeApp(secondRecreatedModel) }
        }
        rule.onNodeWithText("设置（壳层）").assertIsDisplayed()
        assertEquals(MainTab.SETTINGS, secondRecreatedModel.selectedTab.value)
        assertEquals(1, creations)
    }

    @Test fun activityRecreationKeepsCourseEditorRouteDraftColorDialogAndConfirmation() {
        var ids = 0
        val repository = HostRepository(ScheduleData(1, Semester("term", "学期", "2026-09-01", 18, listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"))), emptyList(), "now"))
        val preferences = object : SchedulePreferencesRepository {
            override suspend fun load() = SchedulePreferences.defaults
            override suspend fun save(preferences: SchedulePreferences) = preferences
            override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences) = transform(SchedulePreferences.defaults)
        }
        val factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDateTime.parse("2026-09-01T09:00") }, { "editor-${ids++}" }) as T }
        val original = ViewModelProvider(rule.activity, factory)[ScheduleViewModel::class.java]
        rule.setContent { QingKeApp(original) }; rule.waitUntil(5_000) { original.state.value.loadStatus.name == "READY" }
        original.openAddCourse(); original.updateCourseName("重建课程"); original.updateCourseTeacher("老师"); original.addCourseSchedule()
        val second = original.editor.value!!.schedules.last().id; original.updateCourseScheduleDay(second, 2); original.updateCourseColorInput("#NOPE"); original.showColorDialog(); original.requestCloseEditor()
        rule.activityRule.scenario.recreate()
        lateinit var recreated: ScheduleViewModel
        rule.activityRule.scenario.onActivity { activity -> recreated = ViewModelProvider(activity, factory)[ScheduleViewModel::class.java]; assertSame(original, recreated); activity.setContent { QingKeApp(recreated) } }
        val editor = recreated.editor.value!!
        assertEquals(CourseEditorMode.CREATE, editor.mode); assertEquals("重建课程", editor.name); assertEquals("老师", editor.teacher); assertEquals("#NOPE", editor.colorInput)
        assertTrue(editor.isColorDialogOpen); assertEquals(2, editor.schedules.size); assertEquals(2, editor.schedules.last().dayOfWeek); assertTrue(editor.confirmation is CourseEditorConfirmation.Discard)
        rule.onNodeWithTag("course-color-dialog").assertIsDisplayed(); rule.onNodeWithTag("course-discard-confirm").assertIsDisplayed()
    }

    @Test fun todayClockTicksOnlyWhileStartedRefreshesImmediatelyAndKeepsOneActivityModelAfterRecreate() {
        var clock = LocalDateTime.parse("2026-08-31T09:41:52")
        val repository = HostRepository(
            ScheduleData(
                1,
                Semester("semester", "已保存学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45"))),
                emptyList(),
                "1970-01-01T00:00:00Z",
            ),
        )
        val preferences = object : SchedulePreferencesRepository {
            override suspend fun load() = SchedulePreferences.defaults
            override suspend fun save(preferences: SchedulePreferences) = preferences
            override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences) = transform(SchedulePreferences.defaults)
        }
        var creations = 0
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                creations++
                return ScheduleViewModel(ScheduleAppState(repository, preferences), { clock }) as T
            }
        }
        val original = ViewModelProvider(rule.activity, factory)[ScheduleViewModel::class.java]
        rule.setContent { QingKeApp(original) }
        rule.waitUntil(5_000) { original.state.value.loadStatus.name == "READY" }
        rule.onNodeWithTag("today-screen").assertIsDisplayed()

        clock = clock.plusSeconds(1)
        rule.waitUntil(2_500) { original.currentTime.value == clock }
        val startedTime = original.currentTime.value
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        SystemClock.sleep(1_200)
        clock = clock.plusSeconds(1)
        SystemClock.sleep(1_200)
        assertEquals(startedTime, original.currentTime.value)

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(2_000) { original.currentTime.value == clock }
        rule.activityRule.scenario.recreate()
        lateinit var recreated: ScheduleViewModel
        rule.activityRule.scenario.onActivity { activity ->
            recreated = ViewModelProvider(activity, factory)[ScheduleViewModel::class.java]
            assertSame(original, recreated)
            activity.setContent { QingKeApp(recreated) }
        }
        rule.waitUntil(2_000) { recreated.currentTime.value == clock }
        rule.onNodeWithTag("today-screen").assertIsDisplayed()
        assertEquals(1, creations)
        assertEquals(1, repository.loads)
    }

    private class HostRepository(
        private var data: ScheduleData = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"),
    ) : ScheduleRepository {
        var loads = 0
        override suspend fun load() = data.also { loads++ }
        override suspend fun replace(data: ScheduleData) = data.also { this.data = it }
        override suspend fun saveSemester(semester: Semester) = data.copy(semester = semester).also { data = it }
        override suspend fun saveCourse(course: Course) = data
        override suspend fun deleteCourse(id: String) = data
    }
}
