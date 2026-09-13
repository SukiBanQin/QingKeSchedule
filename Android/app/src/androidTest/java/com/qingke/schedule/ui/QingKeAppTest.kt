package com.qingke.schedule.ui

import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.RepeatRule
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
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp

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

    @Test fun systemDateAndTimeDialogsConfirmNewValuesAndCancelLeavesExistingValues() {
        var form by mutableStateOf(defaultForm(expanded = true))
        var dateUpdates = 0; var startUpdates = 0; var endUpdates = 0
        rule.setContent { QingKeAppContent(onboarding(), form, MainTab.TODAY, QingKeAppActions(
            updateStartDate = { value -> dateUpdates++; form = form.copy(startDate = value) },
            updatePeriodStart = { id, value -> startUpdates++; form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(start = value) else it }) },
            updatePeriodEnd = { id, value -> endUpdates++; form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(end = value) else it }) },
        )) }
        rule.onNodeWithTag("semester-start-date").performClick(); waitForSystemDialog()
        onView(isAssignableFrom(DatePicker::class.java)).perform(setDate(2026, 8, 2)); onView(withId(android.R.id.button1)).perform(click())
        rule.onNodeWithText("开始日期：2026-08-02").assertIsDisplayed(); assertEquals(1, dateUpdates)
        rule.onNodeWithTag("semester-start-date").performClick(); waitForSystemDialog(); onView(isAssignableFrom(DatePicker::class.java)).perform(setDate(2026, 8, 3)); onView(withId(android.R.id.button2)).perform(click())
        rule.onNodeWithText("开始日期：2026-08-02").assertIsDisplayed(); assertEquals(1, dateUpdates)
        rule.onNodeWithTag("period-p1-start").performClick(); waitForSystemDialog(); onView(isAssignableFrom(TimePicker::class.java)).perform(setTime(7, 20)); onView(withId(android.R.id.button1)).perform(click())
        rule.onNodeWithText("07:20").assertIsDisplayed(); assertEquals(1, startUpdates)
        rule.onNodeWithTag("period-p1-end").performClick(); waitForSystemDialog(); onView(isAssignableFrom(TimePicker::class.java)).perform(setTime(8, 10)); onView(withId(android.R.id.button1)).perform(click())
        rule.onNodeWithText("08:10").assertIsDisplayed(); assertEquals(1, endUpdates)
        rule.onNodeWithTag("period-p1-end").performClick(); waitForSystemDialog(); onView(isAssignableFrom(TimePicker::class.java)).perform(setTime(8, 30)); onView(withId(android.R.id.button2)).perform(click())
        rule.onNodeWithText("08:10").assertIsDisplayed(); assertEquals(1, endUpdates)
    }

    @Test fun addAndDeleteUseCallbacksRenumberAndExposeNumberedDeleteSemantics() {
        var form by mutableStateOf(defaultForm(expanded = true))
        rule.setContent { QingKeAppContent(onboarding(), form, MainTab.TODAY, formActions(
            onAdd = {
                val last = form.periods.last(); val start = last.end.plusMinutes(10)
                form = form.copy(periods = form.periods + PeriodFormState("p11", 11, start, start.plusMinutes(45)))
            },
            onRemove = { id -> form = form.copy(periods = form.periods.filterNot { it.id == id }.mapIndexed { index, period -> period.copy(number = index + 1) }) },
        )) }
        rule.onNodeWithTag("add-period").performScrollTo().performClick()
        rule.onNodeWithTag("period-p11-row").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("第 11 节").assertIsDisplayed()
        rule.onNodeWithTag("period-p5-delete").performScrollTo().performClick()
        rule.onAllNodesWithTag("period-p5-row").assertCountEquals(0)
        rule.onNodeWithTag("period-p6-row").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("第 5 节").assertIsDisplayed()
        rule.onNodeWithContentDescription("删除第 1 节").performScrollTo().assertIsDisplayed()
        form = defaultForm(count = 1, expanded = true)
        rule.onAllNodesWithTag("period-p1-delete").assertCountEquals(0)
        form = defaultForm(count = 20, expanded = true)
        rule.onNodeWithTag("add-period").assertIsNotEnabled()
    }

    @Test fun touchTargetsAndTabSemanticsMeetTheContract() {
        var selected by mutableStateOf(MainTab.TODAY)
        var form by mutableStateOf(defaultForm(expanded = true))
        var mainShell by mutableStateOf(false)
        rule.setContent {
            if (mainShell) QingKeAppContent(readyWithSemester(), null, selected, QingKeAppActions(selectTab = { selected = it }))
            else QingKeAppContent(onboarding(), form, selected, formActions())
        }
        listOf("semester-save-toolbar", "semester-start-date", "daily-periods-toggle", "period-p1-start", "period-p1-end", "period-p1-delete").forEach(::assertAtLeast48Dp)
        assertAtLeast48Dp("semester-save", scroll = true)
        mainShell = true
        listOf("today-tab", "schedule-tab", "settings-tab").forEach(::assertAtLeast48Dp)
        rule.onNodeWithTag("today-tab").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithContentDescription("今日").assertIsDisplayed(); rule.onNodeWithContentDescription("课表").assertIsDisplayed(); rule.onNodeWithContentDescription("设置").assertIsDisplayed()
        rule.onNodeWithTag("schedule-tab").performClick()
        rule.onNodeWithTag("schedule-tab").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test fun todayPageUsesPresentationProgressAndStableOccurrenceTagsForDuplicateBusinessIds() {
        var refreshes = 0
        rule.setContent {
            QingKeAppContent(
                readyToday(), null, MainTab.TODAY,
                QingKeAppActions(refreshTime = { refreshes++ }),
                LocalDateTime.parse("2026-08-31T09:41:52"),
            )
        }
        rule.onNodeWithTag("today-screen").assertIsDisplayed()
        rule.onNodeWithTag("today-brand-header").assertIsDisplayed()
        rule.onNodeWithTag("today-course-count").assertTextContains("04")
        rule.onNodeWithTag("today-featured-course-1-0").assertIsDisplayed()
        rule.onNodeWithTag("today-featured-progress").assertIsDisplayed()
        rule.onNodeWithText("已进行 46 分钟 · 剩余 63:08").assertIsDisplayed()
        assertEquals(0, refreshes)
    }

    private fun formActions(
        onName: (String) -> Unit = {}, onToggle: () -> Unit = {}, onAdd: () -> Unit = {}, onRemove: (String) -> Unit = {}, onSave: () -> Unit = {}, onDismiss: () -> Unit = {},
    ) = QingKeAppActions(updateName = onName, togglePeriods = onToggle, addPeriod = onAdd, removePeriod = onRemove, saveSemester = onSave, dismissError = onDismiss)

    private fun assertAtLeast48Dp(tag: String, scroll: Boolean = false) {
        val node = rule.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val minimum = with(rule.density) { 48.dp.toPx() }
        assertTrue("$tag width=${bounds.width}", bounds.width >= minimum)
        assertTrue("$tag height=${bounds.height}", bounds.height >= minimum)
    }

    private fun setDate(year: Int, month: Int, day: Int) = object : ViewAction {
        override fun getDescription() = "set DatePicker value"
        override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
        override fun perform(uiController: UiController, view: View) { (view as DatePicker).updateDate(year, month - 1, day) }
    }

    private fun setTime(hour: Int, minute: Int) = object : ViewAction {
        override fun getDescription() = "set TimePicker value"
        override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
        override fun perform(uiController: UiController, view: View) { (view as TimePicker).hour = hour; view.minute = minute }
    }

    private fun waitForSystemDialog() {
        rule.waitForIdle()
        SystemClock.sleep(250)
    }

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

    private fun readyToday(): ScheduleState {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "10:45"),
            Period(3, "11:00", "11:45"), Period(4, "14:00", "14:45"),
        ))
        fun schedule(id: String, period: Int) = CourseSchedule(id, 1, period, period, 1, 18, RepeatRule.EVERY, "")
        return ScheduleState(
            data = ScheduleData(1, semester, listOf(
                Course("duplicate", "已结束", "老师", "#287B74", listOf(schedule("duplicate", 1))),
                Course("duplicate", "进行中", "", "#287B74", listOf(schedule("duplicate", 2))),
                Course("future", "下一门", "", "#287B74", listOf(schedule("duplicate", 3))),
                Course("future", "后续", "", "not-a-color", listOf(schedule("duplicate", 4))),
            ), "1970-01-01T00:00:00Z"),
            preferences = SchedulePreferences.defaults,
            loadStatus = LoadStatus.READY,
        )
    }
}
