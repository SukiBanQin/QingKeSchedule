package com.qingke.schedule.ui

import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.Configuration
import android.util.Xml
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.platform.LocalDensity
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
import com.qingke.schedule.viewmodel.CourseEditorState
import com.qingke.schedule.viewmodel.CourseEditorMode
import com.qingke.schedule.viewmodel.CourseScheduleFormState
import com.qingke.schedule.viewmodel.CourseEditorConfirmation
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
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.R
import java.io.File

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

    @Test fun todayAddAndChooserUseRealCallbacksAndStableSourceTags() {
        var addCalls = 0; var appendedIndex = -1
        var editor by mutableStateOf<CourseEditorState?>(null)
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(openAddCourse = { addCalls++ }, appendCourseAt = { appendedIndex = it }), LocalDateTime.parse("2026-08-31T09:00"), editor) }
        rule.onNodeWithTag("today-add-course").performClick(); assertEquals(1, addCalls)
        editor = CourseEditorState(CourseEditorMode.CHOOSER); rule.waitForIdle()
        rule.onNodeWithTag("course-append-1").performClick(); assertEquals(1, appendedIndex)
        rule.onNodeWithTag("course-append-0").assertIsDisplayed(); rule.onNodeWithTag("course-append-1").assertIsDisplayed()
    }

    @Test fun appendOverlayIsReadOnlyAndInFlightScheduleControlsAreDisabled() {
        val schedule = CourseScheduleFormState("new", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.APPEND, name = "算法", teacher = "老师", schedules = listOf(schedule), originalScheduleCount = 0, isInFlight = true)) }
        rule.onNodeWithTag("course-append-readonly").assertIsDisplayed()
        rule.onAllNodesWithTag("course-name").assertCountEquals(0)
        rule.onNodeWithTag("course-day-new-plus").assertIsNotEnabled()
        rule.onNodeWithTag("course-repeat-new-EVERY").assertIsNotEnabled()
        rule.onNodeWithTag("course-add-schedule").assertIsNotEnabled()
    }

    @Test fun editorHeadersUseCourseCodesAndNumberedSections() {
        val schedule = CourseScheduleFormState("new", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.EDIT, name = "算法", schedules = listOf(schedule))) }
        rule.onNodeWithTag("course-editor-toolbar").assertIsDisplayed()
        rule.onNodeWithTag("course-editor-brand-header").assertIsDisplayed()
        rule.onNodeWithText("EDIT / 04", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("01 / 课程资料").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("02 / 安排 1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("course-danger-zone").performScrollTo().assertIsDisplayed()
    }

    @Test fun editorCloseUsesVisibleInverseInkAcrossThemesAndLargeFont() {
        val schedule = CourseScheduleFormState("close", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var appearanceMode by mutableStateOf(AppearanceMode.LIGHT)
        var editorFontScale by mutableStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, editorFontScale)) {
                QingKeAppContent(
                    readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearanceMode)),
                    null,
                    MainTab.TODAY,
                    QingKeAppActions(),
                    editor = CourseEditorState(CourseEditorMode.EDIT, name = "算法", schedules = listOf(schedule)),
                )
            }
        }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            listOf(1f, 1.3f).forEach { scale ->
                appearanceMode = mode
                editorFontScale = scale
                rule.waitForIdle()
                rule.onNodeWithTag("course-editor-close").assertIsDisplayed().assertIsEnabled()
                assertEditorCloseHasVisibleLightInk(mode, scale)
            }
        }
    }

    @Test fun successNoticeIsReadableAcrossThemesDoesNotBlockAddAndExpires() {
        var state by mutableStateOf(readyToday()); var notice by mutableStateOf<String?>("课程添加成功"); var addCalls = 0
        rule.setContent { QingKeAppContent(state, null, MainTab.TODAY, QingKeAppActions(openAddCourse = { addCalls++ }), LocalDateTime.parse("2026-08-31T09:00"), courseSuccess = notice, consumeCourseSuccess = { notice = null }) }
        rule.onNodeWithTag("course-success-notice").assertIsDisplayed()
        rule.onNodeWithTag("today-add-course").performClick(); assertEquals(1, addCalls)
        state = state.copy(preferences = state.preferences.copy(appearanceMode = AppearanceMode.DARK)); rule.waitForIdle()
        rule.onNodeWithTag("course-success-notice").assertIsDisplayed()
        rule.waitUntil(3_200) { notice == null }
        rule.onAllNodesWithTag("course-success-notice").assertCountEquals(0)
    }

    @Test fun editorBackToolbarAndDangerControlsInvokeTheirRealCallbacks() {
        val schedule = CourseScheduleFormState("back", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var editor by mutableStateOf<CourseEditorState?>(CourseEditorState(CourseEditorMode.EDIT, name = "课", schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Delete))
        var saves = 0; var deletes = 0; var backs = 0
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(saveCourse = { saves++ }, deleteCourse = { deletes++ }, editorBack = { backs++; editor = editor?.copy(confirmation = null) }), editor = editor) }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle()
        assertEquals(1, backs); rule.onNodeWithTag("course-editor").assertIsDisplayed(); rule.onAllNodesWithTag("course-delete-confirm").assertCountEquals(0)
        rule.onNodeWithTag("course-save-toolbar").performClick(); assertEquals(1, saves)
        rule.onNodeWithTag("course-delete").performScrollTo().performClick(); assertEquals(1, deletes)
    }

    @Test fun newestSuccessNoticeRestartsItsOwnTimer() {
        var notice by mutableStateOf<String?>("A")
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), courseSuccess = notice, consumeCourseSuccess = { notice = null }) }
        rule.waitForIdle()
        val firstNoticeStartedAt = SystemClock.elapsedRealtime()
        rule.waitUntil(1_700) { SystemClock.elapsedRealtime() - firstNoticeStartedAt >= 1_500 }
        notice = "B"; rule.waitForIdle()
        rule.onNodeWithTag("course-success-notice").assertIsDisplayed()
        rule.onNodeWithText("B", useUnmergedTree = true).assertIsDisplayed()
        val replacementNoticeStartedAt = SystemClock.elapsedRealtime()
        rule.waitUntil(1_500) { SystemClock.elapsedRealtime() - replacementNoticeStartedAt >= 1_300 }
        rule.onNodeWithText("B", useUnmergedTree = true).assertIsDisplayed()
        rule.waitUntil(2_000) { notice == null }; rule.onAllNodesWithTag("course-success-notice").assertCountEquals(0)
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

    @Test fun terminalTabsExposeCenteredIconTitleAndNumberGroups() {
        rule.setContent { QingKeAppContent(readyWithSemester(), null, MainTab.TODAY, QingKeAppActions()) }
        listOf("today", "schedule", "settings").forEach { id ->
            val tab = rule.onNodeWithTag("$id-tab", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val content = rule.onNodeWithTag("$id-tab-content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val tabCenter = (tab.left + tab.right) / 2f
            val contentCenter = (content.left + content.right) / 2f
            assertTrue("$id content width=${content.width} must remain wrap-content within tab width=${tab.width}", content.width < tab.width)
            assertTrue("$id content center=$contentCenter tab center=$tabCenter", kotlin.math.abs(contentCenter - tabCenter) <= 1.5f)
        }
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

    @Test fun todayApi37MatrixShowsOrderedStatusesColorsDetailsFeaturedAndBottomTabs() {
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), LocalDateTime.parse("2026-08-31T09:41:52")) }
        listOf("terminal-backdrop", "today-brand-logo", "today-month-code", "today-day-number", "today-hero-code", "today-activity-rail").forEach {
            rule.onNodeWithTag(it).assertIsDisplayed()
        }
        listOf(
            Triple("today-course-0-0", "COMPLETE", "#287B74"),
            Triple("today-course-1-0", "CURRENT", "#287B74"),
            Triple("today-course-2-0", "NEXT", "#287B74"),
            Triple("today-course-3-0", "UPCOMING", "#28B9D6"),
        ).also {
            rule.onNodeWithTag("today-featured-course-1-0").assertIsDisplayed()
            rule.onAllNodesWithTag("today-featured-course-0-0").assertCountEquals(0)
        }.forEachIndexed { courseIndex, (course, status, color) ->
            rule.onNodeWithTag("today-scroll-content").performScrollToIndex(courseIndex + 3)
            rule.onNodeWithTag(course).assertIsDisplayed()
            rule.onNodeWithTag(course.replace("today-course-", "today-course-status-"), useUnmergedTree = true).assertTextContains(status)
            rule.onNodeWithTag(course.replace("today-course-", "today-course-color-"), useUnmergedTree = true).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("课程颜色：$color")),
            )
            when (course) {
                "today-course-0-0" -> rule.onNodeWithTag("today-course-details-0-0", useUnmergedTree = true).assertTextContains("老师")
                "today-course-1-0" -> rule.onNodeWithTag("today-course-details-1-0", useUnmergedTree = true).assertTextContains("第 2 节")
            }
        }
        listOf("today-tab", "schedule-tab", "settings-tab").forEach { rule.onAllNodesWithTag(it).assertCountEquals(1) }
    }

    @Test fun todayApi37MatrixSwitchesFeaturedAndReportsAllThreeEmptyStates() {
        var state by mutableStateOf(readyToday())
        var now by mutableStateOf(LocalDateTime.parse("2026-08-31T08:50:00"))
        rule.setContent { QingKeAppContent(state, null, MainTab.TODAY, QingKeAppActions(), now) }
        rule.onNodeWithTag("today-featured-course-1-0").assertIsDisplayed()
        rule.onNodeWithTag("today-featured-status", useUnmergedTree = true).assertTextContains("NEXT").assertIsDisplayed()

        now = LocalDateTime.parse("2026-08-31T15:00:00")
        rule.waitForIdle()
        rule.onAllNodesWithTag("today-featured-course-0-0").assertCountEquals(0)
        rule.onAllNodesWithTag("today-featured-course-1-0").assertCountEquals(0)
        rule.onNodeWithTag("today-scroll-content").performScrollToIndex(6)
        rule.onNodeWithTag("today-end-marker").assertIsDisplayed()

        now = LocalDateTime.parse("2026-06-01T09:00:00")
        rule.waitForIdle()
        rule.onNodeWithTag("today-empty").assertIsDisplayed()
        rule.onNodeWithText("当前日期不在这个学期内", substring = true).assertIsDisplayed()

        now = LocalDateTime.parse("2026-09-01T09:00:00")
        rule.waitForIdle()
        rule.onNodeWithTag("today-empty").assertIsDisplayed()
        rule.onNodeWithText("今天没有课程，享受空闲时间吧。", substring = true).assertIsDisplayed()

        state = readyToday().copy(preferences = SchedulePreferences.defaults.copy(
            academicCalendar = AcademicCalendarPreferences(nonTeachingDates = listOf("2026-08-31")),
        ))
        now = LocalDateTime.parse("2026-08-31T09:00:00")
        rule.waitForIdle()
        rule.onNodeWithTag("today-empty").assertIsDisplayed()
        rule.onNodeWithText("已设为停课日", substring = true).assertIsDisplayed()
    }

    @Test fun todayPullToRefreshOnCoursesAndEmptyStateGatesReentryAndFinishesFeedback() {
        var refreshes = 0
        var state by mutableStateOf(readyToday())
        var now by mutableStateOf(LocalDateTime.parse("2026-08-31T09:41:52"))
        rule.setContent { QingKeAppContent(state, null, MainTab.TODAY, QingKeAppActions(refreshTime = { refreshes++ }), now) }
        pullAndAssertOneRefresh(refreshes) { refreshes }

        now = LocalDateTime.parse("2026-09-01T09:00:00")
        pullAndAssertOneRefresh(refreshes) { refreshes }
    }

    @Test fun todayApi37ScreenshotsCoverLightDarkAndLargeFontTestHosts() {
        var state by mutableStateOf(readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = AppearanceMode.LIGHT)))
        var fontScale by mutableStateOf(1f)
        val now = LocalDateTime.parse("2026-08-31T09:41:52")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale = fontScale)) {
                QingKeAppContent(state, null, MainTab.TODAY, QingKeAppActions(), now)
            }
        }
        saveTodayScreenshot("android-api37-today-light-testhost.png")
        state = state.copy(preferences = state.preferences.copy(appearanceMode = AppearanceMode.DARK))
        rule.waitForIdle()
        saveTodayScreenshot("android-api37-today-dark-testhost.png")
        fontScale = 1.3f
        rule.waitForIdle()
        saveTodayScreenshot("android-api37-today-font130-testhost.png")
        fontScale = 1f
        state = state.copy(preferences = state.preferences.copy(appearanceMode = AppearanceMode.LIGHT))
        rule.waitForIdle()
        rule.onNodeWithTag("today-scroll-content").performScrollToIndex(8)
        rule.onNodeWithTag("today-end-marker").assertIsDisplayed()
        saveTodayScreenshot("android-api37-course-sequence-testhost.png")
    }

    @Test fun launcherForegroundUsesIndependentCoverArtworkAndAppLabel() {
        val resources = rule.activity.resources
        val parser = resources.getXml(R.drawable.ic_launcher_foreground)
        var foregroundSource = 0
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "bitmap") {
                foregroundSource = Xml.asAttributeSet(parser).getAttributeResourceValue(
                    "http://schemas.android.com/apk/res/android", "src", 0,
                )
            }
        }
        assertEquals(R.drawable.qingke_cover, foregroundSource)
        assertTrue(foregroundSource != R.mipmap.ic_launcher)
        assertEquals("青课", rule.activity.applicationInfo.loadLabel(rule.activity.packageManager).toString())
    }

    @Test fun nightLogoUsesReadableNonCyanForegroundVariant() {
        val baseConfiguration = rule.activity.resources.configuration
        val nightConfiguration = Configuration(baseConfiguration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val nightResources = rule.activity.createConfigurationContext(nightConfiguration).resources
        val qualifierLogo = requireNotNull(BitmapFactory.decodeResource(nightResources, R.drawable.qingke_logo))
        val logo = requireNotNull(BitmapFactory.decodeResource(nightResources, R.drawable.qingke_logo_dark))
        assertEquals(qualifierLogo.width, logo.width)
        assertEquals(qualifierLogo.height, logo.height)
        val visibleNonCyan = buildList {
            for (y in 0 until logo.height step 8) for (x in 0 until logo.width step 8) {
                val pixel = logo.getPixel(x, y)
                val alpha = pixel ushr 24 and 0xff
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                val cyan = blue > 100 && blue > red * 1.25 && green > red * 1.1
                if (alpha > 200 && !cyan) add((red + green + blue) / 3)
            }
        }
        assertTrue(visibleNonCyan.isNotEmpty())
        assertTrue(visibleNonCyan.all { it >= 200 })
    }

    @Test fun logoResourcesUseIosSizedTrimmedCanvas() {
        val logo = requireNotNull(BitmapFactory.decodeResource(rule.activity.resources, R.drawable.qingke_logo))
        val darkLogo = requireNotNull(BitmapFactory.decodeResource(rule.activity.resources, R.drawable.qingke_logo_dark))
        assertEquals(1300, logo.width)
        assertEquals(500, logo.height)
        assertEquals(1300, darkLogo.width)
        assertEquals(500, darkLogo.height)
    }

    @Test fun todayApi37ScreenshotsCoverAllThreeEmptyStates() {
        var state by mutableStateOf(readyToday())
        var now by mutableStateOf(LocalDateTime.parse("2026-06-01T09:00:00"))
        rule.setContent { QingKeAppContent(state, null, MainTab.TODAY, QingKeAppActions(), now) }
        rule.onNodeWithTag("today-empty").assertIsDisplayed()
        saveTodayScreenshot("android-api37-empty-outside-semester.png")

        now = LocalDateTime.parse("2026-09-01T09:00:00")
        rule.waitForIdle()
        saveTodayScreenshot("android-api37-empty-no-courses.png")

        state = readyToday().copy(preferences = SchedulePreferences.defaults.copy(
            academicCalendar = AcademicCalendarPreferences(nonTeachingDates = listOf("2026-08-31")),
        ))
        now = LocalDateTime.parse("2026-08-31T09:00:00")
        rule.waitForIdle()
        saveTodayScreenshot("android-api37-empty-non-teaching.png")
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

    private fun assertEditorCloseHasVisibleLightInk(appearance: AppearanceMode, fontScale: Float) {
        val bitmap = rule.onNodeWithTag("course-editor-close").captureToImage().asAndroidBitmap()
        val lightInk = (bitmap.height / 4 until bitmap.height * 3 / 4).sumOf { y ->
            (bitmap.width / 4 until bitmap.width * 3 / 4).count { x ->
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel ushr 24 and 0xff
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                alpha > 220 && red >= 220 && green >= 220 && blue >= 220
            }
        }
        assertTrue("$appearance fontScale=$fontScale close text lightInk=$lightInk", lightInk >= 20)
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

    private fun pullAndAssertOneRefresh(before: Int, refreshes: () -> Int) {
        // Pull from the real nested scrolling surface, matching an end-user gesture rather than
        // sending a synthetic swipe to PullToRefreshBox's non-visual semantic wrapper.
        val container = rule.onNodeWithTag("today-scroll-content")
        container.performTouchInput { swipeDown() }
        rule.waitUntil(2_000) { refreshes() == before + 1 }
        rule.onNodeWithTag("today-refresh-status").assertIsDisplayed()
        container.performTouchInput { swipeDown() }
        assertEquals(before + 1, refreshes())
        SystemClock.sleep(500)
        rule.waitForIdle()
        rule.onAllNodesWithTag("today-refresh-status").assertCountEquals(0)
    }

    private fun saveTodayScreenshot(name: String) {
        val directory = File(
            requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),
            "p3-03-r2",
        ).also { check(it.exists() || it.mkdirs()) }
        File(directory, name).outputStream().use { output ->
            check(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
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
