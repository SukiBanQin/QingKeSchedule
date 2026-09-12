package com.qingke.schedule.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.state.ScheduleAppState
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
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
                return ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDate.parse("2026-07-01") }, { "recreate-${ids++}" }) as T
            }
        }
        val model = ViewModelProvider(rule.activity, factory)[ScheduleViewModel::class.java]
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.form.value != null }
        val first = model.form.value!!.periods.first().id
        rule.onNodeWithTag("semester-name").performTextReplacement("重建保留")
        rule.onNodeWithTag("daily-periods-toggle").performClick()
        model.updatePeriodStart(first, LocalTime.of(7, 20))
        rule.onNodeWithTag("period-$first-start").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.activityRule.scenario.onActivity { activity -> activity.setContent { QingKeApp(model) } }
        rule.onNodeWithTag("semester-name").assertTextContains("重建保留")
        rule.onNodeWithTag("period-$first-start").assertIsDisplayed()
        rule.onNodeWithText("07:20").assertIsDisplayed()
        assertEquals(1, creations)

        model.saveSemester()
        rule.waitUntil(5_000) { !model.state.value.needsOnboarding }
        model.selectTab(MainTab.SETTINGS)
        rule.onNodeWithText("设置（壳层）").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.activityRule.scenario.onActivity { activity -> activity.setContent { QingKeApp(model) } }
        rule.onNodeWithText("设置（壳层）").assertIsDisplayed()
        assertEquals(MainTab.SETTINGS, model.selectedTab.value)
        assertEquals(1, creations)
    }

    private class HostRepository : ScheduleRepository {
        private var data = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z")
        override suspend fun load() = data
        override suspend fun replace(data: ScheduleData) = data.also { this.data = it }
        override suspend fun saveSemester(semester: Semester) = data.copy(semester = semester).also { data = it }
        override suspend fun saveCourse(course: Course) = data
        override suspend fun deleteCourse(id: String) = data
    }
}
