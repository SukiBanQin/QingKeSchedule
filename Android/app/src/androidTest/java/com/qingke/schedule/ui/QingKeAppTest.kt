package com.qingke.schedule.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.state.ScheduleAppState
import com.qingke.schedule.viewmodel.ScheduleViewModel
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QingKeAppTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun onboardingShowsDefaultCollapsedPeriodsAndExpands() {
        val model = ScheduleViewModel(
            ScheduleAppState(EmptyRepository(), DefaultPreferences()),
            now = { LocalDate.parse("2026-07-01") }, idFactory = { "id" },
        )
        rule.setContent { QingKeApp(model) }
        rule.onNodeWithTag("onboarding-screen").assertIsDisplayed()
        rule.onNodeWithText("总周数：18").assertIsDisplayed()
        rule.onNodeWithTag("daily-periods-toggle").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithTag("add-period").performScrollTo().assertIsDisplayed()
    }

    private class EmptyRepository : ScheduleRepository {
        private var data = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z")
        override suspend fun load() = data
        override suspend fun replace(data: ScheduleData) = data
        override suspend fun saveSemester(semester: Semester) = data.copy(semester = semester).also { data = it }
        override suspend fun saveCourse(course: Course) = data
        override suspend fun deleteCourse(id: String) = data
    }
    private class DefaultPreferences : SchedulePreferencesRepository {
        override suspend fun load() = SchedulePreferences.defaults
        override suspend fun save(preferences: SchedulePreferences) = preferences
        override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences) = transform(SchedulePreferences.defaults)
    }
}
