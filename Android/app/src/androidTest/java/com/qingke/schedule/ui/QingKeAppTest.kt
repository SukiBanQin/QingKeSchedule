package com.qingke.schedule.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleState
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.PeriodFormState
import com.qingke.schedule.viewmodel.SemesterFormState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QingKeAppTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun loadingFailureRetryAndReadyBranchesNeverRenderBlank() {
        var state by mutableStateOf(loading())
        var form by mutableStateOf<SemesterFormState?>(null)
        var retryCalls = 0
        rule.setContent { QingKeAppContent(state, form, MainTab.TODAY, QingKeAppActions(retryLoad = { retryCalls++; state = onboarding(); form = defaultForm() })) }
        rule.onNodeWithTag("app-loading").assertIsDisplayed()
        state = failed("读取失败")
        rule.onNodeWithTag("app-load-error").assertIsDisplayed()
        rule.onAllNodesWithTag("onboarding-screen").assertCountEquals(0)
        rule.onNodeWithTag("app-load-retry").performClick()
        rule.onNodeWithTag("onboarding-screen").assertIsDisplayed(); assertEquals(1, retryCalls)
        form = null
        rule.onNodeWithTag("app-loading").assertIsDisplayed()
        state = readyWithSemester()
        rule.onNodeWithTag("main-shell").assertIsDisplayed()
    }

    @Test fun onboardingDefaultsExpandAndUseMeaningfulControls() {
        var form by mutableStateOf(defaultForm())
        var saves = 0
        rule.setContent { QingKeAppContent(onboarding(), form, MainTab.TODAY, formActions(
            onName = { form = form.copy(name = it) },
            onToggle = { form = form.copy(periodsExpanded = !form.periodsExpanded) },
            onAdd = { if (form.periods.size < 20) form = form.copy(periods = form.periods + PeriodFormState("p${form.periods.size + 1}", form.periods.size + 1, LocalTime.of(21, 0), LocalTime.of(21, 45))) },
            onSave = { saves++ },
        )) }
        rule.onNodeWithTag("onboarding-screen").assertIsDisplayed()
        rule.onNodeWithText("总周数：18").assertIsDisplayed()
        rule.onNodeWithTag("daily-periods-toggle").performClick()
        rule.onNodeWithTag("period-p1-row").assertIsDisplayed()
        rule.onNodeWithTag("period-p1-delete").assertIsDisplayed()
        rule.onNodeWithTag("semester-name").performTextReplacement("新学期")
        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.onNodeWithTag("semester-save").performScrollTo().performClick()
        assertEquals("新学期", form.name); assertEquals(2, saves)
    }

    @Test fun periodBoundariesDeleteAndSavingButtonsAreBehaviorallyDisabled() {
        var form by mutableStateOf(defaultForm(count = 1, expanded = true))
        rule.setContent { QingKeAppContent(onboarding(saving = true), form, MainTab.TODAY, formActions()) }
        rule.onNodeWithTag("semester-save-toolbar").assertIsNotEnabled()
        rule.onNodeWithTag("semester-save").assertIsNotEnabled()
        rule.onAllNodesWithTag("period-p1-delete").assertCountEquals(0)
        form = defaultForm(count = 20, expanded = true)
        rule.onNodeWithTag("add-period").assertIsNotEnabled()
        rule.onNodeWithTag("period-p20-delete").performScrollTo().assertIsDisplayed()
    }

    @Test fun validationErrorAndSaveFailureKeepFormAndDismissDialog() {
        var state by mutableStateOf(onboarding(error = "保存失败"))
        var form by mutableStateOf(defaultForm(expanded = true, validation = "学期名称不能为空"))
        var dismisses = 0
        rule.setContent { QingKeAppContent(state, form, MainTab.TODAY, formActions(onDismiss = { dismisses++; state = state.copy(error = null) })) }
        rule.onNodeWithTag("semester-validation-error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("app-error-dialog").assertIsDisplayed()
        rule.onNodeWithTag("app-error-dismiss").performClick()
        rule.onAllNodesWithTag("app-error-dialog").assertCountEquals(0)
        rule.onNodeWithTag("period-p1-row").performScrollTo().assertIsDisplayed()
        assertEquals(1, dismisses); assertEquals("学期名称不能为空", form.validationMessage)
    }

    @Test fun mainShellTabsInvokeOneStateOwnerAndHaveAccessibleTargets() {
        var selected by mutableStateOf(MainTab.TODAY)
        rule.setContent { QingKeAppContent(readyWithSemester(), null, selected, QingKeAppActions(selectTab = { selected = it })) }
        rule.onNodeWithTag("today-tab").assertIsEnabled()
        rule.onNodeWithTag("schedule-tab").performClick(); rule.onNodeWithText("课表（壳层）").assertIsDisplayed()
        rule.onNodeWithTag("settings-tab").performClick(); rule.onNodeWithText("设置（壳层）").assertIsDisplayed()
    }

    private fun formActions(
        onName: (String) -> Unit = {}, onToggle: () -> Unit = {}, onAdd: () -> Unit = {}, onSave: () -> Unit = {}, onDismiss: () -> Unit = {},
    ) = QingKeAppActions(updateName = onName, togglePeriods = onToggle, addPeriod = onAdd, saveSemester = onSave, dismissError = onDismiss)

    private fun defaultForm(count: Int = 10, expanded: Boolean = false, validation: String? = null) = SemesterFormState(
        id = "semester", name = "2026 秋季学期", startDate = LocalDate.parse("2026-07-01"), totalWeeks = 18,
        periods = (1..count).map { PeriodFormState("p$it", it, LocalTime.of(8, 0).plusMinutes((it - 1) * 55L), LocalTime.of(8, 45).plusMinutes((it - 1) * 55L)) },
        periodsExpanded = expanded, validationMessage = validation,
    )
    private fun loading() = ScheduleState(loadStatus = LoadStatus.LOADING)
    private fun failed(message: String) = ScheduleState(loadStatus = LoadStatus.FAILED, error = message)
    private fun onboarding(saving: Boolean = false, error: String? = null) = ScheduleState(loadStatus = LoadStatus.READY, isSaving = saving, error = error)
    private fun readyWithSemester() = ScheduleState(
        data = ScheduleData(1, Semester("semester", "已有", "2026-09-01", 18, listOf(Period(1, "08:00", "08:45"))), emptyList(), "1970-01-01T00:00:00Z"),
        preferences = SchedulePreferences.defaults, loadStatus = LoadStatus.READY,
    )
}
