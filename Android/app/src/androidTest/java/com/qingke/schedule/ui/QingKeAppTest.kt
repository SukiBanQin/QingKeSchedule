package com.qingke.schedule.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.content.res.Configuration
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click as touchClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.domain.withLunchBreakEnabled
import com.qingke.schedule.domain.withLunchBreakTimes
import com.qingke.schedule.domain.withMakeupTeachingDay
import com.qingke.schedule.domain.withNonTeachingDate
import com.qingke.schedule.domain.withWeekendsAreNonTeachingDays
import com.qingke.schedule.domain.withoutMakeupTeachingDay
import com.qingke.schedule.domain.withoutNonTeachingDate
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.state.ScheduleAppState
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
import com.qingke.schedule.viewmodel.LunchBreakConflict
import com.qingke.schedule.viewmodel.ScheduleViewModel
import com.qingke.schedule.viewmodel.SemesterSaveState
import com.qingke.schedule.domain.CascadeRemovalReason
import com.qingke.schedule.domain.CourseCascade
import com.qingke.schedule.domain.RemovedSchedule
import com.qingke.schedule.domain.SemesterCascadePlan
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import com.qingke.schedule.preferences.SchedulePreferencesRepository
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

    @Test fun weekScheduleRendersMatrixHeadersAndRoutesAddAndCourseSource() {
        var selected by mutableStateOf(MainTab.TODAY)
        var adds = 0
        val opened = mutableListOf<Int>()
        rule.setContent {
            QingKeAppContent(readyToday(), null, selected,
                QingKeAppActions(selectTab = { selected = it }, openAddCourse = { adds++ }, openCourseAt = { opened += it }),
                LocalDateTime.parse("2026-08-31T09:00"))
        }
        rule.onNodeWithTag("schedule-tab").performClick()
        rule.onNodeWithTag("week-schedule").assertIsDisplayed()
        (1..7).forEach { rule.onNodeWithTag("week-column-header-$it").assertIsDisplayed() }
        rule.onNodeWithTag("week-time-header").assertIsDisplayed()
        rule.onNodeWithTag("week-period-1").assertIsDisplayed()
        rule.onNodeWithTag("week-add-course").performClick()
        assertEquals(1, adds)
        rule.onNodeWithTag("week-item-0:0:0").performClick()
        assertEquals(listOf(0), opened)
    }

    @Test fun weekHeaderControlsAndSectionTitlesFollowIosStructure() {
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.SCHEDULE, QingKeAppActions(), LocalDateTime.parse("2026-08-31T09:00")) }
        rule.onNodeWithTag("week-brand").assertTextContains("SCHEDULE :// WEEK MATRIX")
        rule.onNodeWithTag("week-title").assertTextContains("01")
        rule.onNodeWithTag("week-teaching-week", useUnmergedTree = true).assertTextContains("第 01 教学周")
        rule.onNodeWithTag("week-parity", useUnmergedTree = true).assertTextContains("ODD WEEK")
        rule.onNodeWithTag("week-previous").assertIsNotEnabled()
        rule.onNodeWithTag("week-current").assertIsNotEnabled()
        rule.onNodeWithTag("week-next").performClick()
        rule.onNodeWithTag("week-title").assertTextContains("02")
        rule.onNodeWithTag("week-teaching-week", useUnmergedTree = true).assertTextContains("第 02 教学周")
        rule.onNodeWithTag("week-parity", useUnmergedTree = true).assertTextContains("EVEN WEEK")
        rule.onNodeWithTag("week-previous").assertIsEnabled()
        rule.onNodeWithTag("week-current").assertIsEnabled()
        rule.onNodeWithTag("week-current").performClick()
        rule.onNodeWithTag("week-title").assertTextContains("01")
        rule.onNodeWithTag("week-matrix-header-index").assertTextContains("05")
        rule.onNodeWithTag("week-view-title").assertTextContains("周视图")
        rule.onNodeWithTag("week-matrix-summary").assertTextContains("MON–SUN / 4 PERIODS")
    }

    @Test fun weekDateStripSelectionMovesSignalUnderlineAndManifestTitleDropsIsoDate() {
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.SCHEDULE, QingKeAppActions(), LocalDateTime.parse("2026-08-31T09:00")) }
        rule.onNodeWithTag("week-date-strip").performScrollTo()
        rule.onNodeWithTag("week-day-1").assert(hasContentDescription("已选择", substring = true))
        val monday = signalUnderlineCenterX("week-date-strip")
        rule.onNodeWithTag("week-day-5").performClick()
        rule.waitForIdle()
        val friday = signalUnderlineCenterX("week-date-strip")
        assertTrue("selected underline must move right: $monday -> $friday", friday > monday + 60f)
        rule.onNodeWithTag("week-day-5").assert(hasContentDescription("已选择", substring = true))
        rule.onNodeWithTag("week-day-1").assert(hasContentDescription("未选择", substring = true))
        rule.onNodeWithTag("selected-day-title").assertTextContains("周五")
        rule.onNodeWithTag("week-manifest-detail").assertTextContains("0 ENTRIES")
    }

    @Test fun weekMatrixDrawsGridLinesAndShowsLunchBreakOnlyWhenEnabled() {
        var state by mutableStateOf(weekState(withLunchBreak = false))
        rule.setContent { QingKeAppContent(state, null, MainTab.SCHEDULE, QingKeAppActions(), LocalDateTime.parse("2026-08-31T09:00")) }
        rule.onNodeWithTag("week-matrix").performScrollTo()
        assertWeekMatrixGridLines()
        rule.onAllNodesWithTag("week-lunch-break").assertCountEquals(0)
        state = weekState(withLunchBreak = true)
        rule.waitForIdle()
        rule.onNodeWithTag("week-matrix").performScrollTo()
        rule.onNodeWithTag("week-lunch-break").assertIsDisplayed()
        rule.onNodeWithTag("week-lunch-break-title").assertTextContains("午休")
        assertLunchBreakCyanStrip()
        rule.onNodeWithTag("week-period-1").assertIsDisplayed()
    }

    @Test fun weekDayManifestOmitsIsoDateAndRoutesCourseClicks() {
        val opened = mutableListOf<Int>()
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.SCHEDULE, QingKeAppActions(openCourseAt = { opened += it }), LocalDateTime.parse("2026-08-31T09:00")) }
        rule.onNodeWithTag("selected-day-title").assertTextContains("周一")
        rule.onNodeWithTag("week-manifest-detail").assertTextContains("4 ENTRIES")
        rule.onAllNodes(hasText("2026-08-31", substring = true)).assertCountEquals(0)
        rule.onNodeWithTag("week-list-0-0").performScrollTo().performClick()
        rule.onNodeWithTag("week-list-2-0").performScrollTo().performClick()
        assertEquals(listOf(0, 2), opened)
    }

    @Test fun weekControlsKeepAllTextVisibleAndSpacedAcrossFontScales() {
        var fontScale by mutableStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) {
                QingKeAppContent(readyToday(), null, MainTab.SCHEDULE, QingKeAppActions(), LocalDateTime.parse("2026-08-31T09:00"))
            }
        }
        listOf(1f, 1.3f).forEach { scale ->
            fontScale = scale
            rule.waitForIdle()
            assertParityTextPainted(scale)
            assertWeekControlTextInsidePanel(scale)
        }
    }

    @Test fun weekControlsRestoreAutoFollowAndManualBrowsingSurvivesClockRefresh() {
        var now by mutableStateOf(LocalDateTime.parse("2026-08-31T09:00"))
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.SCHEDULE, QingKeAppActions(), now) }
        rule.onNodeWithTag("week-title").assertTextContains("01")
        rule.onNodeWithTag("week-current").assertIsNotEnabled()
        rule.onNodeWithTag("week-next").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("week-title").assertTextContains("02")
        rule.onNodeWithTag("week-current").assertIsEnabled()
        rule.onNodeWithTag("week-previous").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("week-title").assertTextContains("01")
        rule.onNodeWithTag("week-current").assertIsEnabled()
        rule.onNodeWithTag("week-current").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("week-current").assertIsNotEnabled()
        now = LocalDateTime.parse("2026-09-07T09:00")
        rule.waitForIdle()
        rule.onNodeWithTag("week-title").assertTextContains("02")
        rule.onNodeWithTag("week-next").performClick()
        rule.onNodeWithTag("week-next").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("week-title").assertTextContains("04")
        now = LocalDateTime.parse("2026-09-28T09:00")
        rule.waitForIdle()
        rule.onNodeWithTag("week-title").assertTextContains("04")
        rule.onNodeWithTag("week-current").assertIsEnabled()
    }

    @Test fun appendOverlayIsReadOnlyAndInFlightScheduleControlsAreDisabled() {
        val schedule = CourseScheduleFormState("new", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.APPEND, name = "算法", teacher = "老师", schedules = listOf(schedule), originalScheduleCount = 0, isInFlight = true)) }
        rule.onNodeWithTag("course-append-readonly").assertIsDisplayed()
        rule.onAllNodesWithTag("course-name").assertCountEquals(0)
        rule.onNodeWithTag("course-day-new").assertIsNotEnabled()
        rule.onNodeWithTag("course-repeat-new-EVERY").assertIsNotEnabled()
        rule.onNodeWithTag("course-add-schedule").assertIsNotEnabled()
    }

    @Test fun editorHeadersUseCourseCodesAndNumberedSections() {
        val schedule = CourseScheduleFormState("new", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.EDIT, name = "算法", schedules = listOf(schedule))) }
        rule.onNodeWithTag("course-editor-toolbar").assertIsDisplayed()
        rule.onNodeWithTag("course-editor-brand-header").assertIsDisplayed()
        rule.onNodeWithText("EDIT / 04", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-info-section").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("course-schedule-header-new").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("course-danger-zone").performScrollTo().assertIsDisplayed()
    }

    @Test fun terminalEditorUsesIndependentBackdropSectionsSwatchesAndOneSaveEntry() {
        val schedule = CourseScheduleFormState("new", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.CREATE, name = "算法", color = "#287B74", schedules = listOf(schedule))) }
        rule.onNodeWithTag("course-editor").assertIsDisplayed()
        rule.onNodeWithTag("course-editor-backdrop").assertIsDisplayed()
        val editorBounds = rule.onNodeWithTag("course-editor", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val backdropBounds = rule.onNodeWithTag("course-editor-backdrop", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(editorBounds, backdropBounds)
        rule.onAllNodesWithTag("today-screen").assertCountEquals(1)
        rule.onNodeWithTag("course-editor-toolbar").assertIsDisplayed()
        rule.onNodeWithText("NEW COURSE", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-info-section").assertIsDisplayed()
        rule.onNodeWithTag("course-info-form-section").assertIsDisplayed()
        rule.onNodeWithTag("course-current-color").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("course-color-#287B74").assertIsDisplayed()
        rule.onNodeWithText("✓", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-schedule-header-new").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("course-add-schedule").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("course-save-toolbar").assertIsDisplayed()
        rule.onAllNodesWithTag("course-save").assertCountEquals(0)
    }

    @Test fun chooserAndConfirmationsUseTerminalSectionsAndCodes() {
        val schedule = CourseScheduleFormState("new", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var editor by mutableStateOf<CourseEditorState?>(CourseEditorState(CourseEditorMode.CHOOSER))
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(), editor = editor) }
        rule.onNodeWithText("SELECT PROFILE", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-choice-create-section").assertIsDisplayed()
        rule.onNodeWithTag("course-choice-reuse-section").assertIsDisplayed()
        rule.onNodeWithTag("course-append-0").assertIsDisplayed()
        editor = CourseEditorState(CourseEditorMode.EDIT, schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Conflicts(Course("candidate", "候选", "", "#287B74", emptyList()), emptyList()))
        rule.waitForIdle()
        rule.onNodeWithTag("course-conflict-confirm").assertIsDisplayed()
        rule.onNodeWithText("WARNING / CONFLICT", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-conflict-confirm-status").assertTextContains("SCHEDULE COLLISION")
        rule.onNodeWithTag("course-conflict-confirm-backdrop").assertIsDisplayed()
        rule.onNodeWithText("检测到课程冲突", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("仍可保存", substring = true, useUnmergedTree = true).assertIsDisplayed()
        // The conflict box keeps its two actions: "返回修改" plus the explicit "仍然保存".
        rule.onAllNodesWithTag("terminal-dialog-dismiss").assertCountEquals(1)
        rule.onAllNodesWithTag("course-conflict-confirm-confirm").assertCountEquals(1)
        // P3-04-R8: a blocked save is one red dialog with a single close action and never a "save anyway".
        editor = CourseEditorState(CourseEditorMode.EDIT, schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Invalid("该上课安排已存在，请勿重复添加。"))
        rule.waitForIdle()
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        rule.onNodeWithTag("course-save-error-status").assertTextContains("CANNOT SAVE")
        rule.onNodeWithText("无法保存课程", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("该上课安排已存在，请勿重复添加。", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithTag("course-save-error-dismiss").assertCountEquals(1)
        rule.onAllNodesWithTag("terminal-dialog-dismiss").assertCountEquals(0)
        rule.onAllNodesWithText("返回修改", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("仍然保存", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("course-validation").assertCountEquals(0)
        editor = CourseEditorState(CourseEditorMode.EDIT, schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Discard)
        rule.waitForIdle(); rule.onNodeWithTag("course-discard-confirm-status").assertTextContains("DISCARD CHANGES"); rule.onNodeWithText("放弃未保存的修改？", useUnmergedTree = true).assertIsDisplayed(); rule.onNodeWithText("继续编辑", useUnmergedTree = true).assertIsDisplayed(); rule.onNodeWithText("放弃修改", useUnmergedTree = true).assertIsDisplayed()
        editor = CourseEditorState(CourseEditorMode.EDIT, schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Delete)
        rule.waitForIdle(); rule.onNodeWithTag("course-delete-confirm-status").assertTextContains("IRREVERSIBLE"); rule.onNodeWithText("删除这门课程？", useUnmergedTree = true).assertIsDisplayed(); rule.onNodeWithText("确认删除", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun schedulePickerOpensMenuAndSelectsNonAdjacentDay() {
        val schedule = CourseScheduleFormState("picker", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var selectedDay = 1
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(updateCourseDay = { _, value -> selectedDay = value }), editor = CourseEditorState(CourseEditorMode.CREATE, schedules = listOf(schedule))) }
        rule.onNodeWithTag("course-schedule-header-picker").performScrollTo()
        rule.onNodeWithTag("course-day-picker-value", useUnmergedTree = true).assertTextContains("星期一")
        rule.onNodeWithTag("course-start-period-picker-value", useUnmergedTree = true).assertTextContains("第 1 节 · 08:00")
        rule.onNodeWithTag("course-end-period-picker-value", useUnmergedTree = true).assertTextContains("第 1 节 · 08:45")
        rule.onNodeWithTag("course-day-picker").performClick()
        rule.onNodeWithTag("course-day-picker-menu").assertIsDisplayed()
        rule.onNodeWithTag("course-day-picker-option-6").performClick()
        assertEquals(6, selectedDay)
    }

    @Test fun r5TerminalColorModesDropdownsAndRepeatSelectorKeepOneEditorState() {
        val schedule = CourseScheduleFormState("r5", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        val baseState = readyToday(); val tenPeriodState = baseState.copy(data = baseState.data.copy(semester = baseState.data.semester!!.copy(periods = (1..10).map { Period(it, "%02d:00".format(7 + it), "%02d:45".format(7 + it)) })))
        var editor by mutableStateOf<CourseEditorState?>(CourseEditorState(CourseEditorMode.CREATE, color = "#287B74", colorInput = "#287B74", schedules = listOf(schedule), isColorDialogOpen = true))
        var fontScale by mutableStateOf(1f)
        var day = 1; var start = 1; var end = 1; var repeat = RepeatRule.EVERY
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) { QingKeAppContent(tenPeriodState, null, MainTab.TODAY, QingKeAppActions(
            updateCourseColor = { value -> editor = editor!!.copy(color = value, colorInput = value) },
            updateCourseDay = { _, value -> day = value }, updateCourseStartPeriod = { _, value -> start = value }, updateCourseEndPeriod = { _, value -> end = value }, updateCourseRepeat = { _, value -> repeat = value },
        ), editor = editor) } }
        rule.onNodeWithTag("course-color-mode-GRID").assertIsDisplayed()
        rule.onNodeWithTag("course-color-grid").assertIsDisplayed()
        fun pixels(tag: String, predicate: (Int) -> Boolean) = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap -> (0 until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x -> predicate(bitmap.getPixel(x, y)) } } }
        assertTrue("red grid swatch must paint pixels", pixels("course-color-grid-E11D48") { pixel -> (pixel shr 16 and 0xff) > 160 && (pixel shr 8 and 0xff) < 100 } > 80)
        assertTrue("green grid swatch must paint pixels", pixels("course-color-grid-22C55E") { pixel -> (pixel shr 8 and 0xff) > 130 && (pixel shr 16 and 0xff) < 80 } > 80)
        rule.onNodeWithTag("course-color-grid-E11D48").performClick(); assertEquals("#E11D48", editor!!.color); rule.onNodeWithText("✓", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-color-mode-SPECTRUM").performClick(); rule.onNodeWithTag("course-color-spectrum-area").assertIsDisplayed()
        val gridColor = editor!!.color; rule.onNodeWithTag("course-color-spectrum-area").performTouchInput { touchClick(center) }; val spectrumColor = editor!!.color; assertTrue("spectrum tap must change color", spectrumColor != gridColor); val spectrumHsv = hsvParts(spectrumColor); assertTrue("spectrum center saturation=${spectrumHsv[1]}", spectrumHsv[1] in 35..65); assertTrue("spectrum center value=${spectrumHsv[2]}", spectrumHsv[2] in 35..65)
        rule.onNodeWithTag("course-color-mode-SLIDERS").performClick(); val beforeRed = rgbParts(editor!!.color)[0]; rule.onNodeWithTag("course-r-slider").performTouchInput { touchClick(Offset(center.x * 1.8f, center.y)) }; val afterRed = rgbParts(editor!!.color)[0]; assertTrue("RGB slider must write a changed red channel before=$beforeRed after=$afterRed", beforeRed != afterRed && afterRed in 220..255)
        fun assertRgbRowsStayInline() {
            listOf("r", "g", "b").forEach { channel ->
                val label = rule.onNodeWithTag("course-$channel-slider-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val value = rule.onNodeWithTag("course-$channel-slider-value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val slider = rule.onNodeWithTag("course-$channel-slider", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                rule.onNodeWithTag("course-$channel-slider-label", useUnmergedTree = true).assertIsDisplayed()
                rule.onNodeWithTag("course-$channel-slider-value", useUnmergedTree = true).assertIsDisplayed()
                assertTrue("$channel label/value order: $label $value", label.right <= value.left)
                assertTrue("$channel value/track order: $value $slider", value.right <= slider.left)
                assertTrue("$channel label/value baseline: $label $value", kotlin.math.abs(label.center.y - value.center.y) <= 2f)
                assertTrue("$channel value/track baseline: $value $slider", kotlin.math.abs(value.center.y - slider.center.y) <= 2f)
                listOf("course-$channel-slider-label", "course-$channel-slider-value", "course-$channel-slider").forEach(::assertFitsRootHorizontally)
            }
        }
        assertRgbRowsStayInline(); fontScale = 1.3f; rule.waitForIdle(); assertRgbRowsStayInline()
        fontScale = 1f; rule.waitForIdle()
        editor = editor!!.copy(isColorDialogOpen = false); rule.waitForIdle()
        rule.onNodeWithTag("course-schedule-header-r5").performScrollTo()
        rule.onNodeWithTag("course-day-r5").performClick(); rule.onNodeWithTag("course-day-r5-menu").assertIsDisplayed(); rule.onNodeWithTag("course-day-r5-option-7").performScrollTo().performClick(); assertEquals(7, day)
        rule.onNodeWithTag("course-start-period-r5").performClick(); rule.onNodeWithTag("course-start-period-r5-option-10").performScrollTo().performClick(); assertEquals(10, start)
        rule.onNodeWithTag("course-end-period-r5").performClick(); rule.onNodeWithTag("course-end-period-r5-option-10").performScrollTo().performClick(); assertEquals(10, end)
        rule.onNodeWithTag("course-repeat-r5-ODD").performClick(); assertEquals(RepeatRule.ODD, repeat)
        listOf("course-repeat-r5-EVERY", "course-repeat-r5-ODD", "course-repeat-r5-EVEN").forEach(::assertAtLeast48Dp)
    }

    @Test fun chooserProfileAndClosedPickersUseCompactIosAlignedStructureAcrossFontScales() {
        val schedule = CourseScheduleFormState("compact", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var editor by mutableStateOf<CourseEditorState?>(CourseEditorState(CourseEditorMode.CHOOSER))
        var fontScale by mutableStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) {
                QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(openNewCourse = { editor = CourseEditorState(CourseEditorMode.CREATE, color = "#287B74", schedules = listOf(schedule)) }, showColorDialog = { editor = editor?.copy(isColorDialogOpen = true) }), editor = editor)
            }
        }
        rule.onNodeWithTag("course-choice-create-panel", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-create-new").assertIsDisplayed()
        assertAtLeast48Dp("course-create-new")
        rule.onNodeWithText("新建一门课程", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("已有课程会复用名称、教师和识别色，只新增一条上课安排。", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("新建一门课程").performClick()
        rule.waitForIdle()
        assertEquals(CourseEditorMode.CREATE, editor?.mode)
        editor = CourseEditorState(CourseEditorMode.CHOOSER)
        rule.waitForIdle()
        assertAtLeast48Dp("course-append-0", scroll = true)
        rule.onNodeWithTag("course-append-rail-0", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        editor = CourseEditorState(CourseEditorMode.CREATE, color = "#287B74", schedules = listOf(schedule))
        rule.waitForIdle()
        rule.onNodeWithTag("course-info-section").performScrollTo()
        rule.onNodeWithText("课程信息", useUnmergedTree = true).assertIsDisplayed()
        fontScale = 1.3f
        rule.waitForIdle()
        listOf("course-name-placeholder", "course-teacher-placeholder").forEach(::assertSingleLineFitsRoot)
        rule.onNodeWithText("教师（选填）", useUnmergedTree = true).assertIsDisplayed()
        assertAtLeast48Dp("course-custom-color", scroll = true)
        rule.onNodeWithTag("course-custom-color").performScrollTo().performClick()
        rule.onNodeWithTag("course-color-dialog").assertIsDisplayed()
        editor = editor?.copy(isColorDialogOpen = false)
        listOf(1f, 1.3f).forEach { scale ->
            fontScale = scale
            rule.waitForIdle()
            rule.onNodeWithTag("course-schedule-header-compact").performScrollTo()
            listOf("course-day-compact", "course-start-period-compact", "course-end-period-compact").forEach(::assertFitsRootHorizontally)
            listOf("course-day-compact", "course-start-period-compact", "course-end-period-compact", "course-start-week-compact-minus", "course-start-week-compact-plus").forEach(::assertAtLeast48Dp)
        }
    }

    @Test fun chooserPlusSquareUsesThemeForegroundInsteadOfCyan() {
        var mode by mutableStateOf(AppearanceMode.LIGHT)
        rule.setContent { QingKeAppContent(readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = mode)), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.CHOOSER)) }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { appearance ->
            mode = appearance; rule.waitForIdle()
            val bitmap = rule.onNodeWithTag("course-create-new-icon", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            val cyanPixels = (0 until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x ->
                val pixel = bitmap.getPixel(x, y); val red = pixel shr 16 and 0xff; val green = pixel shr 8 and 0xff; val blue = pixel and 0xff
                red < 80 && green > 125 && blue > 150
            } }
            val foregroundPixels = (0 until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x ->
                val pixel = bitmap.getPixel(x, y); val red = pixel shr 16 and 0xff; val green = pixel shr 8 and 0xff; val blue = pixel and 0xff
                if (appearance == AppearanceMode.LIGHT) red < 45 && green < 55 && blue < 60 else red > 210 && green > 215 && blue > 215
            } }
            assertTrue("$appearance cyan=$cyanPixels", cyanPixels == 0)
            assertTrue("$appearance foreground=$foregroundPixels", foregroundPixels >= 12)
            assertCenteredPlusGeometry(bitmap, appearance)
            assertRoundedCreateCourseIcon(bitmap, appearance)
        }
        val shiftedPlus = Bitmap.createBitmap(22, 22, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 2f; isAntiAlias = false }
        val shiftedCenter = shiftedPlus.width / 2f + 3f
        android.graphics.Canvas(shiftedPlus).apply {
            drawLine(5f, shiftedCenter, 17f, shiftedCenter, paint)
            drawLine(shiftedCenter, 5f, shiftedCenter, 17f, paint)
        }
        assertTrue("three-pixel translated plus must fail the geometry probe", runCatching { assertCenteredPlusGeometry(shiftedPlus, AppearanceMode.LIGHT) }.isFailure)
    }

    @Test fun appendUsesOriginalScheduleOrdinalAndAllowsRemovingNewSchedule() {
        val existing = CourseScheduleFormState("existing", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        val newSchedule = CourseScheduleFormState("new", 2, 2, 2, 1, 18, RepeatRule.EVERY, "")
        var editor by mutableStateOf<CourseEditorState?>(CourseEditorState(CourseEditorMode.APPEND, name = "算法", teacher = "老师", schedules = listOf(existing, newSchedule), originalScheduleCount = 1))
        var removedId: String? = null
        rule.setContent { QingKeAppContent(readyToday(), null, MainTab.TODAY, QingKeAppActions(removeCourseSchedule = { id -> removedId = id; editor = editor?.copy(schedules = editor!!.schedules.filterNot { it.id == id }) }), editor = editor) }
        rule.onNodeWithText("上课安排 2", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("course-schedule-header-new-number", useUnmergedTree = true).assertTextContains("03")
        rule.onNodeWithTag("course-remove-schedule-new").performScrollTo().assertIsDisplayed().assertTextContains("删除这个安排").performClick()
        assertEquals("new", removedId)
        assertEquals(listOf("existing"), editor!!.schedules.map { it.id })
        rule.onAllNodesWithTag("course-remove-schedule-new").assertCountEquals(0)
    }

    @Test fun todayAddEmptyCourseEntryAndEndMarkersUseTerminalAffordances() {
        var now by mutableStateOf(LocalDateTime.parse("2026-09-01T09:00:00"))
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var fontScale by mutableStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) { QingKeAppContent(readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)), null, MainTab.TODAY, QingKeAppActions(), now) } }
        rule.onNodeWithTag("today-add-course").assertIsDisplayed()
        val addBounds = rule.onNodeWithTag("today-add-course", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val expected = 64.dp.value * rule.density.density
        assertTrue("ADD width=${addBounds.width}", kotlin.math.abs(addBounds.width - expected) <= 1f)
        assertTrue("ADD height=${addBounds.height}", kotlin.math.abs(addBounds.height - expected) <= 1f)
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            listOf(1f, 1.3f).forEach { scale ->
                appearance = mode; fontScale = scale; rule.waitForIdle()
                assertAddChromeIsActuallyDrawn(mode)
                assertTodayAddPlusIsThinAndCentered()
                rule.onNodeWithTag("today-add-label", useUnmergedTree = true).assertTextContains("ADD")
            }
        }
        appearance = AppearanceMode.LIGHT; fontScale = 1f; rule.waitForIdle()
        rule.onNodeWithText("QUEUE EMPTY", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("STANDBY", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("使用右下角 ADD 录入一门新课程。", useUnmergedTree = true).assertIsDisplayed()
        now = LocalDateTime.parse("2026-08-31T09:41:52")
        rule.waitForIdle()
        rule.onNodeWithTag("today-course-enter-0-0", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("today-scroll-content").performScrollToIndex(8)
        rule.onNodeWithTag("today-end-marker").assertIsDisplayed()
        rule.onNodeWithTag("today-course-color-0-0", useUnmergedTree = true).assertIsDisplayed()
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
        var state by mutableStateOf(readyToday()); var notice by mutableStateOf<String?>("SYSTEM // 课程添加成功"); var fontScale by mutableStateOf(1f); var addCalls = 0
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) { QingKeAppContent(state, null, MainTab.TODAY, QingKeAppActions(openAddCourse = { addCalls++ }), LocalDateTime.parse("2026-08-31T09:00"), courseSuccess = notice, consumeCourseSuccess = { notice = null }) } }
        rule.onNodeWithTag("course-success-notice").assertIsDisplayed()
        rule.onNodeWithTag("course-success-dot", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("course-success-check", useUnmergedTree = true).assertIsDisplayed()
        listOf("SYSTEM // 课程添加成功", "SYSTEM // 添加上课安排成功", "SYSTEM // 课程修改已保存", "SYSTEM // 课程删除成功").forEach { message ->
            notice = message; rule.waitForIdle(); rule.onNodeWithTag("course-success-message", useUnmergedTree = true).assertTextContains(message)
        }
        val noticeHeight = rule.onNodeWithTag("course-success-notice", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertTrue("notice height=$noticeHeight", noticeHeight >= 46f)
        fun assertSuccessStack() {
            val noticeBounds = rule.onNodeWithTag("course-success-notice", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val addBounds = rule.onNodeWithTag("today-add-course", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val tabBounds = rule.onNodeWithTag("terminal-tab-bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val minimumGap = 6.dp.value * rule.density.density
            val maximumGap = 22.dp.value * rule.density.density
            assertTrue("success notice must be below ADD: add=$addBounds notice=$noticeBounds", addBounds.bottom <= noticeBounds.top)
            assertTrue("success notice must be above tab bar: notice=$noticeBounds tab=$tabBounds", noticeBounds.bottom <= tabBounds.top)
            assertTrue("ADD to notice gap must remain visible: add=$addBounds notice=$noticeBounds", noticeBounds.top - addBounds.bottom >= minimumGap)
            assertTrue("ADD to notice gap must stay compact: add=$addBounds notice=$noticeBounds", noticeBounds.top - addBounds.bottom <= maximumGap)
            assertTrue("notice to tab gap must stay compact: notice=$noticeBounds tab=$tabBounds", tabBounds.top - noticeBounds.bottom <= maximumGap)
        }
        assertSuccessStack()
        rule.onNodeWithTag("today-add-course").performClick(); assertEquals(1, addCalls)
        state = state.copy(preferences = state.preferences.copy(appearanceMode = AppearanceMode.DARK)); rule.waitForIdle()
        rule.onNodeWithTag("course-success-notice").assertIsDisplayed()
        fontScale = 1.3f; rule.waitForIdle(); assertSuccessStack()
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

    @Test fun dangerFooterFollowsDeletePanelAndBlockedSaveUsesTheCenteredRedDialog() {
        val schedule = CourseScheduleFormState("danger", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var scale by mutableStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) { QingKeAppContent(readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.EDIT, schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Invalid("请填写课程名称"))) } }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            appearance = mode; scale = 1.3f; rule.waitForIdle()
            rule.onAllNodesWithTag("course-validation").assertCountEquals(0)
            rule.onNodeWithTag("course-save-error").assertIsDisplayed()
            rule.onNodeWithText("无法保存课程", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithText("请填写课程名称", useUnmergedTree = true).assertIsDisplayed()
            rule.onAllNodesWithTag("course-save-error-dismiss").assertCountEquals(1)
            rule.onAllNodesWithText("仍然保存", useUnmergedTree = true).assertCountEquals(0)
            assertDialogHasDangerActionButton()
            rule.onNodeWithTag("course-danger-zone").performScrollTo()
            rule.onNodeWithTag("course-danger-footer").performScrollTo()
            val delete = rule.onNodeWithTag("course-delete", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val footer = rule.onNodeWithTag("course-danger-footer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("footer=$footer delete=$delete", footer.top >= delete.bottom)
        }
    }

    @Test fun dialogsUseDedicatedOpaqueElevatedSurfaceAcrossThemesAndLargeFont() {
        val schedule = CourseScheduleFormState("modal", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var scale by mutableStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) { QingKeAppContent(readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.EDIT, schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Delete)) } }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            appearance = mode; scale = 1.3f; rule.waitForIdle()
            rule.onNodeWithTag("course-delete-confirm-backdrop").assertIsDisplayed()
            val surface = rule.onNodeWithTag("course-delete-confirm", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val backdrop = rule.onNodeWithTag("course-delete-confirm-backdrop", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("surface must be inside backdrop", surface.left >= backdrop.left && surface.right <= backdrop.right)
            assertModalSurfaceIsOpaque(appearance)
        }
    }

    @Test fun terminalModalSurfaceDoesNotLeakAHighContrastBackdrop() {
        var dark by mutableStateOf(false)
        rule.setContent {
            Box(Modifier.fillMaxSize().drawBehind {
                val stripe = 3.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(if ((x / stripe).toInt() % 2 == 0) Color.White else Color.Black, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripe, size.height))
                    x += stripe
                }
            }) {
                Box(Modifier.size(240.dp, 144.dp).terminalModalSurface(dark, Color(0xFFE65A4F)).testTag("modal-opacity-probe"))
            }
        }
        listOf(false, true).forEach { appearance ->
            dark = appearance; rule.waitForIdle()
            assertModalProbeRejectsBackdropStripes(appearance)
        }
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

    @Test fun blockedSemesterSaveAndWriteFailureBothUseCenteredDialogs() {
        var state by mutableStateOf(onboarding(error = "保存失败"))
        var form by mutableStateOf(defaultForm(expanded = true))
        var blockedDismissals = 0
        var dismisses = 0
        var saveState by mutableStateOf<SemesterSaveState>(SemesterSaveState.Blocked("节次时间无效"))
        rule.setContent {
            QingKeAppContent(
                state, form, MainTab.TODAY,
                formActions(onDismiss = { dismisses++; state = state.copy(error = null) }, onDismissSemesterSave = { blockedDismissals++; saveState = SemesterSaveState.Idle }),
                semesterSave = saveState,
            )
        }
        // The blocking error is a centered dialog, not a notice the user has to scroll to.
        rule.onAllNodesWithTag("semester-validation-error").assertCountEquals(0)
        rule.onNodeWithTag("semester-save-error").assertIsDisplayed()
        rule.onNodeWithText("节次时间无效").assertIsDisplayed()
        rule.onAllNodesWithTag("semester-save-error-confirm").assertCountEquals(1)
        rule.onNodeWithTag("app-error-dialog").assertIsDisplayed()
        rule.onNodeWithTag("app-error-dismiss").performClick()
        rule.onAllNodesWithTag("app-error-dialog").assertCountEquals(0)
        rule.onNodeWithTag("period-p1-row").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("semester-save-error-confirm").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("semester-save-error").assertCountEquals(0)
        assertEquals(1, dismisses); assertEquals(1, blockedDismissals)
    }

    @Test fun mainShellTabsInvokeOneStateOwnerAndHaveAccessibleTargets() {
        var selected by mutableStateOf(MainTab.TODAY)
        rule.setContent { QingKeAppContent(readyWithSemester(), null, selected, QingKeAppActions(selectTab = { selected = it })) }
        rule.onNodeWithTag("today-tab").assertIsEnabled()
        rule.onNodeWithTag("schedule-tab").performClick(); rule.onNodeWithTag("week-schedule").assertIsDisplayed()
        rule.onNodeWithTag("settings-tab").performClick(); rule.onNodeWithTag("settings-screen").assertIsDisplayed()
    }

    @Test fun settingsScreenEditsExistingSemesterAndBothSaveEntriesShareOnePath() {
        var form by mutableStateOf(existingForm())
        var saves = 0
        rule.setContent {
            QingKeAppContent(settingsState(), form, MainTab.SETTINGS, QingKeAppActions(
                updateName = { form = form.copy(name = it) },
                updateTotalWeeks = { form = form.copy(totalWeeks = it) },
                togglePeriods = { form = form.copy(periodsExpanded = !form.periodsExpanded) },
                saveSemester = { saves++ },
            ))
        }
        rule.onNodeWithTag("settings-screen").assertIsDisplayed()
        rule.onNodeWithTag("settings-title").assertTextContains("系统设置")
        rule.onNodeWithTag("settings-brand-header").assertIsDisplayed()
        rule.onNodeWithTag("settings-toolbar").assertIsDisplayed()
        rule.onNodeWithTag("semester-name").assertIsDisplayed().assertTextContains("测试学期")
        rule.onNodeWithText("总周数：18").assertIsDisplayed()
        rule.onNodeWithTag("settings-periods-footer").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("semester-name").performTextReplacement("自定义学期")
        assertEquals("自定义学期", form.name)
        rule.onNodeWithTag("semester-save-toolbar").performClick()
        rule.onNodeWithTag("semester-save").performScrollTo().performClick()
        assertEquals(2, saves)
    }

    @Test fun settingsPeriodsToggleAddRemoveAndTimePickerUseRealCallbacks() {
        var form by mutableStateOf(existingForm().copy(periodsExpanded = false))
        var added = 0
        var removed: String? = null
        rule.setContent {
            QingKeAppContent(settingsState(), form, MainTab.SETTINGS, QingKeAppActions(
                togglePeriods = { form = form.copy(periodsExpanded = !form.periodsExpanded) },
                addPeriod = { added++; form = form.copy(periods = form.periods + PeriodFormState("q3", 3, LocalTime.of(14, 0), LocalTime.of(14, 45))) },
                removePeriod = { id -> removed = id; form = form.copy(periods = form.periods.filterNot { it.id == id }) },
                updatePeriodStart = { id, value -> form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(start = value) else it }) },
            ))
        }
        rule.onAllNodesWithTag("period-q1-row").assertCountEquals(0)
        rule.onNodeWithTag("daily-periods-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("period-q1-row").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("period-q1-start").performScrollTo().assertTextContains("08:55")
        rule.onNodeWithTag("add-period").performScrollTo().performClick()
        assertEquals(1, added)
        rule.onNodeWithTag("period-q3-row").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("period-q2-delete").performScrollTo().performClick()
        assertEquals("q2", removed)
        assertEquals(listOf("q1", "q3"), form.periods.map { it.id })
        rule.onNodeWithTag("period-q1-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
        rule.onNodeWithTag("terminal-time-picker-hour-07").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-minute-20").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:20")
        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(LocalTime.of(7, 20), form.periods.first().start)
        rule.onNodeWithTag("period-q1-start").performScrollTo().assertTextContains("07:20")
    }

    @Test fun cascadeSummaryScrollsInternallyWithManyAffectedCourses() {
        val cascades = (0 until 20).map { index ->
            CourseCascade(
                index, "c$index", "课程$index", 1,
                listOf(RemovedSchedule(index, "c$index", "课程$index", "s$index", 1, index + 1, index + 1, 1, 18, CascadeRemovalReason.DIRECT_REFERENCE)),
            )
        }
        val plan = SemesterCascadePlan(
            courses = emptyList(), coursesWithPartialRemoval = cascades, deletedCourses = emptyList(),
            remappedSchedules = emptyList(), removedSchedules = cascades.flatMap { it.removedSchedules },
        )
        rule.setContent {
            QingKeAppContent(
                settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions(),
                semesterSave = SemesterSaveState.AwaitingCascade(plan),
            )
        }
        rule.onNodeWithTag("semester-cascade-summary").assertHeightIsEqualTo(250.dp)
        rule.onNodeWithTag("semester-cascade-confirm").assertIsDisplayed()
        rule.onNodeWithTag("semester-cascade-dismiss").assertIsDisplayed()
        assertFalse("课程19 must start outside the summary viewport", isTextDisplayed("课程19"))
        var drags = 0
        while (!isTextDisplayed("课程19") && drags < 10) {
            rule.onNodeWithTag("semester-cascade-summary").performTouchInput { swipeUp(durationMillis = 400) }
            rule.waitForIdle()
            drags++
        }
        assertTrue("课程19 must be reachable with real drags, drags=$drags", isTextDisplayed("课程19"))
        rule.onNodeWithTag("semester-cascade-confirm").assertIsDisplayed()
        rule.onNodeWithTag("semester-cascade-dismiss").assertIsDisplayed()
    }

    @Test fun settingsSavingDisablesBothEntriesAndKeepsTheCascadeConfirmationVisible() {
        var form by mutableStateOf(existingForm())
        val plan = cascadePlan()
        rule.setContent {
            QingKeAppContent(
                settingsState().copy(isSaving = true), form, MainTab.SETTINGS, QingKeAppActions(),
                semesterSave = SemesterSaveState.AwaitingCascade(plan),
            )
        }
        rule.onNodeWithTag("semester-save-toolbar").assertIsNotEnabled()
        rule.onNodeWithTag("semester-save").assertIsNotEnabled()
        rule.onNodeWithTag("semester-cascade").assertIsDisplayed()
        rule.onNodeWithTag("semester-cascade-summary").assertIsDisplayed()
        rule.onNodeWithText("整门删除：高数（1 个安排全部失效）", substring = true).assertIsDisplayed()
        form = form.copy(name = "改名")
        rule.waitForIdle()
        rule.onNodeWithTag("semester-cascade-summary").assertIsDisplayed()
    }

    @Test fun semesterLabelsUseThemeForegroundsInBothThemes() {
        var appearance by mutableStateOf(AppearanceMode.DARK)
        rule.setContent {
            QingKeAppContent(
                settingsState().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)),
                existingForm(), MainTab.SETTINGS, QingKeAppActions(),
            )
        }
        assertNodeHasLightInk("semester-total-weeks")
        assertNodeHasLightInk("period-q1-label")
        appearance = AppearanceMode.LIGHT
        rule.waitForIdle()
        assertNodeHasDarkInk("semester-total-weeks")
        assertNodeHasDarkInk("period-q1-label")
    }

    @Test fun settingsSuccessNoticeIsConsumedAfterDisplaying() {
        var notice by mutableStateOf<String?>("SYSTEM // 学期与节次设置已保存")
        rule.setContent {
            QingKeAppContent(
                settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions(),
                semesterSuccess = notice, consumeSemesterSuccess = { notice = null },
            )
        }
        rule.onNodeWithTag("semester-save-success").assertIsDisplayed()
        rule.onNodeWithTag("semester-save-success-message").assertTextContains("SYSTEM // 学期与节次设置已保存")
        rule.waitUntil(4_000) { notice == null }
        rule.onAllNodesWithTag("semester-save-success").assertCountEquals(0)
    }

    @Test fun settingsPullToRefreshKeepsEditedDraftAndOnlyRefreshesTheClock() {
        var form by mutableStateOf(existingForm())
        var refreshes = 0
        rule.setContent {
            QingKeAppContent(settingsState(), form, MainTab.SETTINGS, QingKeAppActions(
                updateName = { form = form.copy(name = it) },
                refreshTime = { refreshes++ },
            ))
        }
        rule.onNodeWithTag("semester-name").performTextReplacement("未保存的名字")
        assertEquals("未保存的名字", form.name)
        rule.onNodeWithTag("settings-screen").performTouchInput { swipeDown() }
        rule.waitUntil(2_000) { refreshes == 1 }
        rule.onNodeWithTag("semester-name").assertTextContains("未保存的名字")
        assertEquals("未保存的名字", form.name)
        assertEquals(1, refreshes)
    }

    @Test fun savedSemesterPeriodTimesFlowIntoTodayAndWeekImmediately() {
        var tab by mutableStateOf(MainTab.TODAY)
        rule.setContent {
            QingKeAppContent(
                updatedPeriodState(), existingForm(), tab,
                QingKeAppActions(selectTab = { tab = it }), LocalDateTime.parse("2026-08-31T07:45"),
            )
        }
        rule.onNodeWithTag("today-course-start-time-0-0", useUnmergedTree = true).assertTextContains("07:30")
        rule.onNodeWithTag("today-course-end-time-0-0", useUnmergedTree = true).assertTextContains("08:15")
        rule.onNodeWithTag("schedule-tab").performClick()
        rule.onNodeWithTag("week-period-1-start").assertTextContains("07:30")
        rule.onNodeWithTag("week-list-0-0").performScrollTo().assertIsDisplayed()
    }

    @Test fun savedSemesterPeriodTimesFlowIntoCourseEditorImmediately() {
        val schedule = CourseScheduleFormState("s", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        rule.setContent {
            QingKeAppContent(
                updatedPeriodState(), existingForm(), MainTab.TODAY, QingKeAppActions(),
                LocalDateTime.parse("2026-08-31T07:45"),
                editor = CourseEditorState(CourseEditorMode.EDIT, name = "早课", schedules = listOf(schedule)),
            )
        }
        rule.onNodeWithTag("course-schedule-header-s").performScrollTo()
        rule.onNodeWithTag("course-start-period-s-value", useUnmergedTree = true).assertTextContains("第 1 节 · 07:30")
        rule.onNodeWithTag("course-end-period-s-value", useUnmergedTree = true).assertTextContains("第 1 节 · 08:15")
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

    @Test fun inlineSemesterCalendarExpandsChangesMonthsAndReturnsControlledLeapDate() {
        var form by mutableStateOf(defaultForm().copy(startDate = LocalDate.of(2026, 12, 31)))
        var dateUpdates = 0
        rule.setContent { QingKeAppContent(onboarding(), form, MainTab.TODAY, QingKeAppActions(updateStartDate = { value -> dateUpdates++; form = form.copy(startDate = value) })) }
        rule.onNodeWithTag("semester-start-date").performClick()
        rule.onNodeWithTag("semester-start-date-calendar").assertIsDisplayed()
        rule.onNodeWithText("2026年12月", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("semester-calendar-next").performClick()
        rule.onNodeWithText("2027年1月", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("semester-calendar-day-2027-01-01").performClick()
        assertEquals(LocalDate.of(2027, 1, 1), form.startDate)
        assertEquals(1, dateUpdates)
        rule.onNodeWithText("2027年1月1日", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("semester-start-date").performClick()
        rule.onAllNodesWithTag("semester-start-date-calendar").assertCountEquals(0)
    }

    @Test fun inlineSemesterCalendarRetainsLeapDayAcrossRecomposition() {
        var form by mutableStateOf(defaultForm().copy(startDate = LocalDate.of(2024, 2, 1)))
        rule.setContent { QingKeAppContent(onboarding(), form, MainTab.TODAY, QingKeAppActions(updateStartDate = { form = form.copy(startDate = it) })) }
        rule.onNodeWithTag("semester-start-date").performClick()
        rule.onNodeWithTag("semester-calendar-day-2024-02-29").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("semester-start-date-calendar").assertIsDisplayed()
        rule.onNodeWithTag("semester-calendar-day-2024-02-29").assertIsDisplayed()
        assertEquals(LocalDate.of(2024, 2, 29), form.startDate)
    }

    @Test fun inlineCalendarChevronIsVisibleAndAccessibleAcrossThemesAndLargeFont() {
        var state by mutableStateOf(onboarding().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = AppearanceMode.LIGHT)))
        var scale by mutableStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) { QingKeAppContent(state, defaultForm(), MainTab.TODAY, QingKeAppActions()) } }
        rule.onNodeWithTag("semester-start-date").performClick()
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            state = state.copy(preferences = state.preferences.copy(appearanceMode = mode)); scale = 1.3f; rule.waitForIdle()
            listOf("semester-calendar-previous", "semester-calendar-next").forEach { tag ->
                rule.onNodeWithTag(tag).assertIsDisplayed()
                assertAtLeast48Dp(tag)
                assertCalendarChevronHasSignalPixels(tag)
            }
        }
    }

    @Test fun terminalTimePickerWritesOnlyOnConfirmAndIgnoresCancelAndBack() {
        var form by mutableStateOf(defaultForm(expanded = true))
        var startWrites = 0
        var endWrites = 0
        rule.setContent {
            QingKeAppContent(onboarding(), form, MainTab.TODAY, QingKeAppActions(
                updatePeriodStart = { id, value -> startWrites++; form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(start = value) else it }) },
                updatePeriodEnd = { id, value -> endWrites++; form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(end = value) else it }) },
            ))
        }
        rule.onNodeWithTag("period-p1-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
        rule.onNodeWithTag("terminal-time-picker-title").assertTextContains("第 1 节 开始时间")
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("08:00")
        rule.onNodeWithTag("terminal-time-picker-hour-07").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-minute-20").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:20")
        assertEquals(0, startWrites)
        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(1, startWrites)
        assertEquals(LocalTime.of(7, 20), form.periods.first().start)
        rule.onAllNodesWithTag("terminal-time-picker").assertCountEquals(0)

        rule.onNodeWithTag("period-p1-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-hour-09").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-cancel").performClick(); rule.waitForIdle()
        assertEquals(1, startWrites)
        assertEquals(LocalTime.of(7, 20), form.periods.first().start)

        rule.onNodeWithTag("period-p1-end").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-minute-05").performScrollTo().performClick()
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithTag("terminal-time-picker").assertCountEquals(0)
        assertEquals(0, endWrites)
        assertEquals(LocalTime.of(8, 45), form.periods.first().end)
    }

    @Test fun terminalTimePickerKeepsItsTargetAfterPeriodsAreAddedOrRemoved() {
        var form by mutableStateOf(defaultForm(count = 3, expanded = true))
        var lastStart: Pair<String, LocalTime>? = null
        rule.setContent {
            QingKeAppContent(onboarding(), form, MainTab.TODAY, QingKeAppActions(
                updatePeriodStart = { id, value -> lastStart = id to value; form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(start = value) else it }) },
                addPeriod = { form = form.copy(periods = form.periods + PeriodFormState("p4", form.periods.size + 1, LocalTime.of(21, 0), LocalTime.of(21, 45))) },
                removePeriod = { id -> form = form.copy(periods = form.periods.filterNot { it.id == id }.mapIndexed { index, period -> period.copy(number = index + 1) }) },
            ))
        }
        rule.onNodeWithTag("period-p2-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-hour-06").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-minute-00").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals("p2" to LocalTime.of(6, 0), lastStart)

        rule.onNodeWithTag("period-p1-delete").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf("p2", "p3"), form.periods.map { it.id })
        assertEquals(listOf(1, 2), form.periods.map { it.number })
        rule.onNodeWithTag("period-p3-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-title").assertTextContains("第 2 节 开始时间")
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("09:50")
        rule.onNodeWithTag("terminal-time-picker-cancel").performClick(); rule.waitForIdle()

        rule.onNodeWithTag("add-period").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("period-p4-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("21:00")
        rule.onNodeWithTag("terminal-time-picker-cancel").performClick()
    }

    @Test fun terminalTimePickerStaysUsableAcrossThemesFontScalesAndSmallScreens() {
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var scale by mutableStateOf(1f)
        var narrow by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) {
                val page: @Composable () -> Unit = {
                    QingKeAppContent(
                        settingsState().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)),
                        existingForm(), MainTab.SETTINGS, QingKeAppActions(),
                    )
                }
                if (narrow) Box(Modifier.size(320.dp, 640.dp).testTag("narrow-root")) { page() } else page()
            }
        }
        listOf(1f, 1.3f).forEach { fontScale ->
            listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
                scale = fontScale; appearance = mode; rule.waitForIdle()
                rule.onNodeWithTag("period-q1-start").performScrollTo().performClick()
                rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
                listOf("terminal-time-picker-confirm", "terminal-time-picker-cancel").forEach {
                    rule.onNodeWithTag(it).assertIsDisplayed()
                    assertAtLeast48Dp(it)
                }
                rule.onNodeWithTag("terminal-time-picker-hour-23").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("terminal-time-picker-minute-59").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("terminal-time-picker-cancel").performClick(); rule.waitForIdle()
            }
        }
        narrow = true; scale = 1.3f; rule.waitForIdle()
        rule.onNodeWithTag("period-q2-end").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
        assertFitsInside("terminal-time-picker", "narrow-root")
        rule.onNodeWithTag("terminal-time-picker-cancel").performClick()
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

    @Test fun todayTypographyUsesCondensedIosEquivalentScaleAndFitsAtLargeFont() {
        assertEquals(74.sp, TodayVisualSpec.dayNumberSize)
        assertEquals(106.dp, TodayVisualSpec.dayColumnWidth)
        assertEquals(32.sp, TodayVisualSpec.featuredStartTimeSize)
        assertEquals(13.sp, TodayVisualSpec.featuredEndTimeSize)
        assertEquals(82.dp, TodayVisualSpec.featuredTimeColumnWidth)
        assertEquals(20.sp, TodayVisualSpec.featuredCourseNameSize)
        assertEquals(24.sp, TodayVisualSpec.sequenceStartTimeSize)
        assertEquals(11.sp, TodayVisualSpec.sequenceEndTimeSize)
        assertEquals(66.dp, TodayVisualSpec.sequenceTimeColumnWidth)
        assertEquals(17.sp, TodayVisualSpec.sequenceCourseNameSize)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertEquals("date face must request ultra-light weight", 100, TodayVisualSpec.dayNumberTypeface.weight)
        } else {
            assertTrue(
                "pre-P fallback must not be bold or italic",
                TodayVisualSpec.dayNumberTypeface.style != Typeface.BOLD &&
                    TodayVisualSpec.dayNumberTypeface.style != Typeface.BOLD_ITALIC,
            )
        }
        var appearance by mutableStateOf(AppearanceMode.LIGHT); var scale by mutableStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) { QingKeAppContent(readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)), null, MainTab.TODAY, QingKeAppActions(), LocalDateTime.parse("2026-08-31T09:41:52")) } }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            appearance = mode; scale = 1.3f; rule.waitForIdle()
            listOf("today-day-number", "today-featured-start-time", "today-featured-end-time", "today-featured-name", "today-course-start-time-0-0", "today-course-end-time-0-0", "today-course-name-0-0").forEach { tag -> rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed(); assertFitsRootHorizontally(tag) }
        }
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

    @Test fun settingsFormSectionsUseOneRailPanelWithInternalDividers() {
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        rule.setContent {
            QingKeAppContent(
                settingsState().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)),
                existingForm(), MainTab.SETTINGS, QingKeAppActions(),
            )
        }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            appearance = mode; rule.waitForIdle()
            assertCyanRail("settings-semester-panel")
            assertCyanRail("settings-periods-panel")
            assertDividersInsidePanel("settings-semester-divider", "settings-semester-panel", 2)
            assertDividersInsidePanel("settings-periods-divider", "settings-periods-panel", 3)
            listOf("semester-name", "semester-start-date", "semester-total-weeks")
                .forEach { assertInsidePanel(it, "settings-semester-panel") }
            listOf("daily-periods-toggle", "daily-periods-count", "daily-periods-chevron", "period-q1-start", "period-q1-end", "add-period")
                .forEach { assertInsidePanel(it, "settings-periods-panel") }
            rule.onNodeWithTag("settings-periods-footer").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun settingsPeriodsToggleKeepsEverythingInsideOnePanelWithStatefulChevron() {
        var form by mutableStateOf(existingForm().copy(periodsExpanded = false))
        rule.setContent {
            QingKeAppContent(settingsState(), form, MainTab.SETTINGS, QingKeAppActions(
                togglePeriods = { form = form.copy(periodsExpanded = !form.periodsExpanded) },
            ))
        }
        rule.onNodeWithTag("daily-periods-chevron", useUnmergedTree = true).assertContentDescriptionEquals("展开节次箭头")
        rule.onNodeWithText("展开节次设置").assertIsDisplayed()
        rule.onNodeWithText("2 节").assertIsDisplayed()
        rule.onAllNodesWithTag("period-q1-row").assertCountEquals(0)
        rule.onAllNodesWithTag("settings-periods-divider").assertCountEquals(0)
        assertInsidePanel("daily-periods-toggle", "settings-periods-panel")
        assertCyanRail("settings-periods-panel")

        rule.onNodeWithTag("daily-periods-toggle").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("daily-periods-chevron", useUnmergedTree = true).assertContentDescriptionEquals("收起节次箭头")
        rule.onNodeWithText("收起节次设置").assertIsDisplayed()
        assertDividersInsidePanel("settings-periods-divider", "settings-periods-panel", 3)
        listOf("period-q1-row", "period-q1-start", "period-q1-end", "period-q1-delete", "add-period")
            .forEach { assertInsidePanel(it, "settings-periods-panel") }
        assertCyanRail("settings-periods-panel")
    }

    @Test fun topSaveEntriesUseYellowTextOnTheInverseBarWithoutFilledButtons() {
        var page by mutableStateOf("settings")
        rule.setContent {
            when (page) {
                "settings" -> QingKeAppContent(settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions())
                "onboarding" -> QingKeAppContent(onboarding(), defaultForm(), MainTab.TODAY, QingKeAppActions())
                else -> QingKeAppContent(
                    settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions(),
                    editor = CourseEditorState(CourseEditorMode.CREATE),
                )
            }
        }
        assertYellowTextOnInverseBar("settings-toolbar", "semester-save-toolbar")
        page = "onboarding"; rule.waitForIdle()
        assertYellowTextOnInverseBar("semester-save-toolbar")
        page = "editor"; rule.waitForIdle()
        assertYellowTextOnInverseBar("course-editor-toolbar", "course-save-toolbar")
    }

    @Test fun settingsSaveCardMatchesCompactIosHeightAndArrowContract() {
        var scale by mutableStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) {
                QingKeAppContent(settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions())
            }
        }
        rule.onNodeWithTag("semester-save").performScrollTo(); rule.waitForIdle()
        assertCompactSaveCard()
        scale = 1.3f; rule.waitForIdle()
        assertCompactSaveCard()
    }

    @Test fun settingsSectionsSurviveLargeFontAndNarrowScreenWithoutClipping() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, 1.3f)) {
                Box(Modifier.size(320.dp, 720.dp).testTag("narrow-root")) {
                    QingKeAppContent(settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions())
                }
            }
        }
        rule.onNodeWithTag("narrow-root").assertIsDisplayed()
        listOf("semester-name", "semester-start-date", "semester-total-weeks", "daily-periods-toggle", "daily-periods-count", "daily-periods-chevron", "period-q1-start", "period-q1-end", "period-q1-delete", "add-period", "semester-save")
            .forEach { tag ->
                rule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                assertFitsInside(tag, "narrow-root")
            }
        assertCyanRail("settings-semester-panel")
        assertCyanRail("settings-periods-panel")
    }

    @Test fun settingsSemesterPanelKeepsTheInlineCalendarInsideTheSameRailPanel() {
        var form by mutableStateOf(existingForm())
        rule.setContent {
            QingKeAppContent(settingsState(), form, MainTab.SETTINGS, QingKeAppActions(
                updateStartDate = { form = form.copy(startDate = it) },
            ))
        }
        assertDividersInsidePanel("settings-semester-divider", "settings-semester-panel", 2)
        rule.onAllNodesWithTag("semester-start-date-calendar").assertCountEquals(0)
        rule.onNodeWithTag("semester-start-date").performScrollTo().performClick(); rule.waitForIdle()
        assertDividersInsidePanel("settings-semester-divider", "settings-semester-panel", 3)
        assertInsidePanel("semester-start-date-calendar", "settings-semester-panel")
        val dateRow = rule.onNodeWithTag("semester-start-date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val calendar = rule.onNodeWithTag("semester-start-date-calendar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dividers = rule.onAllNodesWithTag("settings-semester-divider", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("divider must sit between the date row and the calendar, tops=" + dividers.map { it.top }, dividers.any { it.top >= dateRow.bottom - 1f && it.bottom <= calendar.top + 1f })
        assertCyanRail("settings-semester-panel")
        rule.onNodeWithTag("semester-calendar-day-2026-09-15").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(LocalDate.parse("2026-09-15"), form.startDate)
        rule.onNodeWithTag("semester-weeks-minus", useUnmergedTree = true).performScrollTo()
        val settingsStepperWidth = dpWidth("semester-weeks-minus")
        assertTrue("settings stepper must stay a 48dp borderless glyph, width=$settingsStepperWidth", settingsStepperWidth in 44f..50f)
    }

    @Test fun onboardingUsesTheSharedTerminalPanelsAndBrandHeader() {
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        rule.setContent {
            QingKeAppContent(
                onboarding().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)),
                defaultForm(count = 2, expanded = true), MainTab.TODAY, formActions(),
            )
        }
        rule.onNodeWithTag("onboarding-brand-header").assertIsDisplayed()
        rule.onNodeWithText("SETUP / 00").assertIsDisplayed()
        rule.onNodeWithTag("onboarding-terminal-header").assertIsDisplayed()
        rule.onNodeWithTag("onboarding-title").assertTextContains("首次设置")
        rule.onNodeWithText("FIRST BOOT").assertIsDisplayed()
        rule.onNodeWithText("INIT").assertIsDisplayed()
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            appearance = mode; rule.waitForIdle()
            assertCyanRail("onboarding-semester-panel")
            assertCyanRail("onboarding-periods-panel")
            assertDividersInsidePanel("onboarding-semester-divider", "onboarding-semester-panel", 2)
            assertDividersInsidePanel("onboarding-periods-divider", "onboarding-periods-panel", 3)
            listOf("semester-name", "semester-start-date", "semester-total-weeks").forEach { assertInsidePanel(it, "onboarding-semester-panel") }
            listOf("daily-periods-toggle", "period-p1-start", "period-p1-end", "period-p1-delete", "add-period").forEach { assertInsidePanel(it, "onboarding-periods-panel") }
            rule.onNodeWithTag("semester-save-title", useUnmergedTree = true).performScrollTo().assertTextContains("创建课表")
            rule.onNodeWithTag("semester-save-subtitle", useUnmergedTree = true).assertTextContains("INITIALIZE TERMINAL")
            assertCompactSaveCard("onboarding-bottom-spacer", "INITIALIZE TERMINAL")
        }
    }

    @Test fun courseEditorSectionIndexKeepsThePreviousMetrics() {
        var page by mutableStateOf("settings")
        rule.setContent {
            if (page == "settings") {
                QingKeAppContent(settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions())
            } else {
                QingKeAppContent(settingsState(), existingForm(), MainTab.SETTINGS, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.CREATE))
            }
        }
        rule.onNodeWithTag("settings-semester-section-number", useUnmergedTree = true).performScrollTo()
        val settingsIndex = dpHeight("settings-semester-section-number")
        page = "editor"; rule.waitForIdle()
        val editorIndex = dpHeight("course-info-section-number")
        assertTrue("settings index=$settingsIndex editor index=$editorIndex", settingsIndex < editorIndex - 1f)
    }

    @Test fun academicCalendarSectionIsSharedByBothFormsWithIndependentTagsAndRail() {
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var page by mutableStateOf("settings")
        rule.setContent {
            val preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)
            if (page == "settings") {
                QingKeAppContent(settingsState().copy(preferences = preferences), existingForm(), MainTab.SETTINGS, QingKeAppActions())
            } else {
                QingKeAppContent(onboarding().copy(preferences = preferences), defaultForm(count = 2), MainTab.TODAY, QingKeAppActions())
            }
        }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            appearance = mode; rule.waitForIdle()
            assertCyanRail("settings-calendar-panel")
            rule.onNodeWithTag("settings-calendar-section-number", useUnmergedTree = true).performScrollTo().assertTextContains("03")
            rule.onNodeWithText("教学日历").assertIsDisplayed()
            rule.onNodeWithText("CALENDAR").assertIsDisplayed()
            rule.onNodeWithTag("settings-calendar-footer").performScrollTo().assertIsDisplayed()
            listOf(
                "settings-calendar-weekends-toggle", "settings-calendar-lunch-toggle", "settings-calendar-lunch-start",
                "settings-calendar-lunch-end", "settings-calendar-mode-non-teaching", "settings-calendar-mode-makeup",
                "settings-calendar-exception-date", "settings-calendar-add-exception",
            ).forEach { assertInsidePanel(it, "settings-calendar-panel") }
            assertCyanRail("settings-semester-panel")
        }
        page = "onboarding"; rule.waitForIdle()
        assertCyanRail("onboarding-calendar-panel")
        assertInsidePanel("onboarding-calendar-exception-date", "onboarding-calendar-panel")
        rule.onAllNodesWithTag("settings-calendar-panel").assertCountEquals(0)
        rule.onAllNodesWithTag("settings-calendar-exception-calendar").assertCountEquals(0)
        rule.onAllNodesWithTag("semester-start-date-calendar").assertCountEquals(0)
        rule.onNodeWithTag("onboarding-calendar-footer").performScrollTo().assertIsDisplayed()
    }

    @Test fun calendarTogglesAndModesUseRealCallbacksAndHideLunchTimes() {
        var calendar by mutableStateOf(AcademicCalendarPreferences())
        val weekendToggles = mutableListOf<Boolean>()
        val lunchToggles = mutableListOf<Boolean>()
        rule.setContent {
            QingKeAppContent(calendarState(calendar), existingForm(), MainTab.SETTINGS, QingKeAppActions(
                setWeekendsAreNonTeachingDays = { weekendToggles += it; calendar = calendar.withWeekendsAreNonTeachingDays(it) },
                setLunchBreakEnabled = { lunchToggles += it; calendar = calendar.withLunchBreakEnabled(it) },
            ))
        }
        rule.onNodeWithTag("settings-calendar-weekends-toggle").performScrollTo().assertIsOff()
        rule.onNodeWithTag("settings-calendar-weekends-toggle").performClick(); rule.waitForIdle()
        assertEquals(listOf(true), weekendToggles)
        rule.onNodeWithTag("settings-calendar-weekends-toggle").assertIsOn()
        rule.onNodeWithContentDescription("周末默认不上课").assertIsDisplayed()

        rule.onNodeWithTag("settings-calendar-lunch-start").assertTextContains("11:40")
        rule.onNodeWithTag("settings-calendar-lunch-end").assertTextContains("14:00")
        rule.onNodeWithContentDescription("开始时间，11:40").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-lunch-toggle").performScrollTo().assertIsOn()
        rule.onNodeWithTag("settings-calendar-lunch-toggle").performClick(); rule.waitForIdle()
        assertEquals(listOf(false), lunchToggles)
        rule.onNodeWithTag("settings-calendar-lunch-toggle").assertIsOff()
        rule.onAllNodesWithTag("settings-calendar-lunch-start").assertCountEquals(0)
        rule.onAllNodesWithTag("settings-calendar-lunch-end").assertCountEquals(0)
        rule.onAllNodesWithTag("settings-calendar-lunch-error").assertCountEquals(0)

        rule.onNodeWithTag("settings-calendar-mode-non-teaching").assertIsSelected()
        rule.onNodeWithTag("settings-calendar-mode-makeup").assertIsNotSelected()
        rule.onAllNodesWithTag("settings-calendar-makeup-weekday").assertCountEquals(0)
        rule.onNodeWithTag("settings-calendar-add-exception").assertTextContains("添加停课日")
        rule.onNodeWithTag("settings-calendar-mode-makeup").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-mode-makeup").assertIsSelected()
        rule.onNodeWithTag("settings-calendar-mode-non-teaching").assertIsNotSelected()
        rule.onNodeWithTag("settings-calendar-makeup-weekday").assertTextContains("周一")
        rule.onNodeWithTag("settings-calendar-add-exception").assertTextContains("添加调课日")
        rule.onNodeWithTag("settings-calendar-mode-non-teaching").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("settings-calendar-makeup-weekday").assertCountEquals(0)
    }

    @Test fun calendarDatePickerUsesItsOwnCalendarAndKeepsTheSemesterDateControlClosed() {
        rule.setContent { QingKeAppContent(calendarState(AcademicCalendarPreferences()), existingForm(), MainTab.SETTINGS, QingKeAppActions()) }
        rule.onAllNodesWithTag("settings-calendar-exception-calendar").assertCountEquals(0)
        rule.onNodeWithTag("settings-calendar-exception-date").performScrollTo()
        rule.onNodeWithTag("settings-calendar-exception-date", useUnmergedTree = true).assertContentDescriptionEquals("日期，2026年9月1日，展开日历")
        rule.onNodeWithTag("settings-calendar-date-value", useUnmergedTree = true).assertTextContains("2026年9月1日")
        rule.onNodeWithTag("settings-calendar-exception-date").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-exception-calendar").performScrollTo().assertIsDisplayed()
        assertInsidePanel("settings-calendar-exception-calendar", "settings-calendar-panel")
        rule.onAllNodesWithTag("semester-start-date-calendar").assertCountEquals(0)
        rule.onNodeWithTag("settings-calendar-exception-calendar-next").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-exception-calendar-day-2026-10-01").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-exception-calendar-previous").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-exception-calendar-day-2026-09-05").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-date-value", useUnmergedTree = true).assertTextContains("2026年9月5日")
        rule.onNodeWithTag("settings-calendar-exception-date").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("settings-calendar-exception-calendar").assertCountEquals(0)

        rule.onNodeWithTag("semester-start-date").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-start-date-calendar").assertIsDisplayed()
        rule.onAllNodesWithTag("settings-calendar-exception-calendar").assertCountEquals(0)
        rule.onNodeWithTag("semester-start-date", useUnmergedTree = true).assertContentDescriptionEquals("开始日期，2026年9月1日，收起日历")
    }

    @Test fun calendarAddDeleteRouteDateAndSourceWeekday() {
        var calendar by mutableStateOf(AcademicCalendarPreferences())
        val stopped = mutableListOf<LocalDate>()
        val makeup = mutableListOf<Pair<LocalDate, Int>>()
        val removedStopped = mutableListOf<String>()
        val removedMakeup = mutableListOf<String>()
        rule.setContent {
            QingKeAppContent(calendarState(calendar), existingForm(), MainTab.SETTINGS, QingKeAppActions(
                addNonTeachingDate = { stopped += it; calendar = calendar.withNonTeachingDate(it) },
                addMakeupTeachingDay = { date, day -> makeup += date to day; calendar = calendar.withMakeupTeachingDay(date, day) },
                removeNonTeachingDate = { removedStopped += it; calendar = calendar.withoutNonTeachingDate(it) },
                removeMakeupTeachingDay = { removedMakeup += it; calendar = calendar.withoutMakeupTeachingDay(it) },
            ))
        }
        rule.onNodeWithTag("settings-calendar-exception-date").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-exception-calendar-day-2026-09-05").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-mode-makeup").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-makeup-weekday").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-makeup-weekday-option-3").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-makeup-weekday").assertTextContains("周三")
        rule.onNodeWithTag("settings-calendar-add-exception").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(LocalDate.parse("2026-09-05") to 3), makeup)
        rule.onAllNodesWithTag("settings-calendar-exception-calendar").assertCountEquals(0)
        rule.onNodeWithTag("settings-calendar-makeup-2026-09-05").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-makeup-2026-09-05-detail", useUnmergedTree = true).assertTextContains("按周三课表")
        rule.onNodeWithTag("settings-calendar-makeup-2026-09-05-delete").assertContentDescriptionEquals("删除 2026年9月5日 周六")
        rule.onNodeWithTag("settings-calendar-makeup-2026-09-05-delete").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf("2026-09-05"), removedMakeup)
        rule.onAllNodesWithTag("settings-calendar-makeup-2026-09-05").assertCountEquals(0)

        rule.onNodeWithTag("settings-calendar-mode-non-teaching").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-add-exception").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(LocalDate.parse("2026-09-05")), stopped)
        rule.onNodeWithTag("settings-calendar-non-teaching-2026-09-05").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-non-teaching-2026-09-05-detail", useUnmergedTree = true).assertTextContains("不显示课程")

        rule.onNodeWithTag("settings-calendar-mode-makeup").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-add-exception").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(LocalDate.parse("2026-09-05") to 3, LocalDate.parse("2026-09-05") to 3), makeup)
        rule.onAllNodesWithTag("settings-calendar-non-teaching-2026-09-05").assertCountEquals(0)
        rule.onNodeWithTag("settings-calendar-makeup-2026-09-05").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-makeup-2026-09-05-delete").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf("2026-09-05", "2026-09-05"), removedMakeup)
        rule.onAllNodesWithTag("settings-calendar-makeup-2026-09-05").assertCountEquals(0)
        assertEquals(emptyList<String>(), removedStopped)
    }

    @Test fun lunchTimePickerWritesOnlyOnConfirmAndIgnoresCancelAndBack() {
        var calendar by mutableStateOf(AcademicCalendarPreferences())
        val writes = mutableListOf<Pair<LocalTime, LocalTime>>()
        rule.setContent {
            QingKeAppContent(calendarState(calendar), existingForm(), MainTab.SETTINGS, QingKeAppActions(
                setLunchBreakEnabled = { calendar = calendar.withLunchBreakEnabled(it) },
                requestLunchBreakTimes = { start, end -> writes += start to end; calendar = calendar.withLunchBreakTimes(start.toString(), end.toString()) ?: calendar },
            ))
        }
        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker").assertIsDisplayed()
        rule.onNodeWithTag("terminal-lunch-time-picker-title").assertTextContains("午休开始时间")
        rule.onNodeWithTag("terminal-lunch-time-picker-value").assertTextEquals("11:40")
        rule.onAllNodesWithTag("terminal-time-picker").assertCountEquals(0)
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-12").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-minute-30").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-cancel").performClick(); rule.waitForIdle()
        assertEquals(emptyList<Pair<LocalTime, LocalTime>>(), writes)
        rule.onNodeWithTag("settings-calendar-lunch-start").assertTextContains("11:40")
        rule.onNodeWithTag("period-q1-start").assertTextContains("08:55")

        rule.onNodeWithTag("settings-calendar-lunch-end").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-title").assertTextContains("午休结束时间")
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithTag("terminal-lunch-time-picker").assertCountEquals(0)
        assertEquals(0, writes.size)

        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-12").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-minute-30").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(listOf(LocalTime.of(12, 30) to LocalTime.of(14, 0)), writes)
        rule.onNodeWithTag("settings-calendar-lunch-start").assertTextContains("12:30")
        rule.onAllNodesWithTag("settings-calendar-lunch-error").assertCountEquals(0)

        rule.onNodeWithTag("settings-calendar-lunch-end").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-13").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(listOf(LocalTime.of(12, 30) to LocalTime.of(14, 0), LocalTime.of(12, 30) to LocalTime.of(13, 0)), writes)
        rule.onAllNodesWithTag("settings-calendar-lunch-error").assertCountEquals(0)

        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-15").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(2, writes.size)
        rule.onNodeWithTag("settings-calendar-lunch-start").assertTextContains("15:30")
        rule.onNodeWithTag("settings-calendar-lunch-error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-lunch-error").assertTextContains("午休开始时间必须早于结束时间。")
        rule.onAllNodesWithTag("terminal-lunch-time-picker").assertCountEquals(0)
        rule.onNodeWithTag("settings-calendar-lunch-toggle").performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("settings-calendar-lunch-start").assertCountEquals(0)
        rule.onAllNodesWithTag("settings-calendar-lunch-error").assertCountEquals(0)
    }

    @Test fun calendarControlsMeetTouchTargetsSemanticsAndThemesAtLargeFontAndNarrowWidth() {
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var scale by mutableStateOf(1f)
        var narrow by mutableStateOf(false)
        val calendar = AcademicCalendarPreferences(
            weekendsAreNonTeachingDays = true,
            nonTeachingDates = listOf("2026-10-01"),
            makeupTeachingDays = listOf(MakeupTeachingDay("2026-10-10", 4)),
        )
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) {
                val page: @Composable () -> Unit = {
                    QingKeAppContent(
                        calendarState(calendar).copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance, academicCalendar = calendar)),
                        existingForm(), MainTab.SETTINGS, QingKeAppActions(),
                    )
                }
                if (narrow) Box(Modifier.size(320.dp, 720.dp).testTag("narrow-root")) { page() } else page()
            }
        }
        listOf(1f, 1.3f).forEach { fontScale ->
            listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
                scale = fontScale; appearance = mode; rule.waitForIdle()
                listOf(
                    "settings-calendar-weekends-toggle", "settings-calendar-lunch-toggle", "settings-calendar-lunch-start",
                    "settings-calendar-lunch-end", "settings-calendar-mode-non-teaching", "settings-calendar-mode-makeup",
                    "settings-calendar-exception-date", "settings-calendar-add-exception",
                    "settings-calendar-non-teaching-2026-10-01-delete", "settings-calendar-makeup-2026-10-10-delete",
                ).forEach { tag ->
                    rule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    assertAtLeast48Dp(tag, scroll = true)
                    assertInsidePanel(tag, "settings-calendar-panel")
                }
                rule.onNodeWithTag("settings-calendar-weekends-toggle").assertIsOn()
                val signal = pixelCounts("settings-calendar-add-exception")[2]
                assertTrue("fontScale=$fontScale mode=$mode add-exception must keep the signal fill, yellow=$signal", signal >= 200)
                val switch = rule.onNodeWithTag("settings-calendar-weekends-toggle-switch", useUnmergedTree = true).apply { performScrollTo() }.fetchSemanticsNode().boundsInRoot
                assertTrue("switch glyph bounds=$switch", switch.width >= with(rule.density) { 44.dp.toPx() })
                rule.onNodeWithTag("settings-calendar-makeup-2026-10-10-detail", useUnmergedTree = true).performScrollTo().assertTextContains("按周四课表")
            }
        }
        narrow = true; scale = 1.3f; rule.waitForIdle()
        listOf("settings-calendar-weekends-toggle", "settings-calendar-exception-date", "settings-calendar-add-exception", "settings-calendar-makeup-2026-10-10").forEach { assertFitsInside(it, "narrow-root") }
        assertCyanRail("settings-calendar-panel")
    }

    @Test fun lunchBreakConflictDialogUsesTheRedTerminalVisualAndRoutesBothDecisions() {
        var conflict by mutableStateOf<LunchBreakConflict?>(LunchBreakConflict("09:30", "10:05", persistedPeriodNumbers = listOf(1, 2), draftPeriodNumbers = emptyList()))
        var dismissals = 0
        var confirms = 0
        rule.setContent {
            QingKeAppContent(
                calendarState(AcademicCalendarPreferences()),
                existingForm(), MainTab.SETTINGS,
                QingKeAppActions(
                    dismissLunchBreakConflict = { dismissals++; conflict = null },
                    confirmLunchBreakConflict = { confirms++; conflict = null },
                ),
                lunchBreakConflict = conflict,
            )
        }
        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()
        rule.onNodeWithTag("calendar-lunch-conflict-code", useUnmergedTree = true).assertTextContains("WARNING / CONFLICT")
        rule.onNodeWithTag("calendar-lunch-conflict-status", useUnmergedTree = true).assertTextContains("PERIOD OVERLAP")
        rule.onNodeWithText("午休与节次重叠").assertIsDisplayed()
        rule.onNodeWithText("与第 1、2 节时间重叠", substring = true).assertIsDisplayed()
        rule.onNodeWithText("周课表不会显示该午休条", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("terminal-dialog-dismiss").assertTextContains("返回修改")
        rule.onNodeWithTag("calendar-lunch-conflict-confirm").assertTextContains("仍然保存")
        assertDangerFilled("calendar-lunch-conflict-status")
        listOf("terminal-dialog-dismiss", "calendar-lunch-conflict-confirm").forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val minimum = with(rule.density) { 46.dp.toPx() }
            assertTrue(tag + " keeps the shared terminal dialog height, bounds=" + bounds, bounds.height >= minimum)
        }

        rule.onNodeWithTag("terminal-dialog-dismiss").performClick(); rule.waitForIdle()
        assertEquals(1, dismissals); assertEquals(0, confirms)
        rule.onAllNodesWithTag("calendar-lunch-conflict").assertCountEquals(0)

        conflict = LunchBreakConflict("09:30", "10:05", persistedPeriodNumbers = listOf(1, 2), draftPeriodNumbers = emptyList()); rule.waitForIdle()
        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()
        rule.onNodeWithTag("calendar-lunch-conflict-confirm").performClick(); rule.waitForIdle()
        assertEquals(1, dismissals); assertEquals(1, confirms)
        rule.onAllNodesWithTag("calendar-lunch-conflict").assertCountEquals(0)
    }

    @Test fun firstBootLunchConflictWarnsFromTheVisibleDraftBeforeWriting() {
        val repository = HostScheduleRepository(ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"))
        val preferences = HostPreferencesRepository()
        val model = ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDateTime.parse("2026-07-01T09:00") }, { "boot" })
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }
        rule.onNodeWithTag("onboarding-screen").assertIsDisplayed()

        rule.onNodeWithTag("onboarding-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-08").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()

        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()
        rule.onNodeWithText("与当前节次设置的第 1、2、3、4 节时间重叠", substring = true).assertIsDisplayed()
        rule.onNodeWithText("这些节次时间尚未保存到学期设置", substring = true).assertIsDisplayed()
        rule.onNodeWithText("保存学期设置后才会隐藏该午休条", substring = true).assertIsDisplayed()
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)

        rule.onNodeWithTag("terminal-dialog-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("calendar-lunch-conflict").assertCountEquals(0)
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)

        rule.onNodeWithTag("onboarding-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-08").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("calendar-lunch-conflict-confirm").performClick()
        rule.waitUntil(5_000) { model.state.value.preferences.academicCalendar.lunchBreak.startTime == "08:40" }
        rule.onAllNodesWithTag("calendar-lunch-conflict").assertCountEquals(0)
        assertNull(model.state.value.error)
    }

    @Test fun settingsDraftPeriodEditRaisesTheConflictWhileTheWeekRowFollowsSavedPeriods() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"),
        ))
        val repository = HostScheduleRepository(ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"))
        val preferences = HostPreferencesRepository()
        var ids = 0
        val model = ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDateTime.parse("2026-09-05T09:00") }, { "id${ids++}" })
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        if (!model.form.value!!.periodsExpanded) {
            rule.onNodeWithTag("daily-periods-toggle").performScrollTo().performClick(); rule.waitForIdle()
        }
        val firstPeriod = model.form.value!!.periods.first().id
        rule.onNodeWithTag("period-${firstPeriod}-end").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-hour-12").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-minute-00").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(LocalTime.of(12, 0), model.form.value!!.periods.first().end)

        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()

        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()
        rule.onNodeWithText("与当前节次设置的第 1 节时间重叠", substring = true).assertIsDisplayed()
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        rule.onNodeWithTag("terminal-dialog-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("calendar-lunch-conflict").assertCountEquals(0)
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)

        rule.onNodeWithTag("schedule-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("week-lunch-break").performScrollTo().assertIsDisplayed()
    }

    @Test fun failedLunchBreakConfirmationKeepsTheErrorAndARetryableWarning() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"),
        ))
        val repository = HostScheduleRepository(ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"))
        val preferences = HostPreferencesRepository()
        var ids = 0
        val model = ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDateTime.parse("2026-09-05T09:00") }, { "id${ids++}" })
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-08").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()

        preferences.failUpdate = true
        rule.onNodeWithTag("calendar-lunch-conflict-confirm").performClick(); rule.waitForIdle()
        rule.waitUntil(5_000) { model.state.value.error != null }
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        assertFalse(model.state.value.isSaving)
        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()
        rule.onNodeWithTag("app-error-dialog").assertIsDisplayed()

        rule.onNodeWithTag("app-error-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("app-error-dialog").assertCountEquals(0)
        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        assertFalse(model.state.value.isSaving)

        preferences.failUpdate = false
        rule.onNodeWithTag("calendar-lunch-conflict-confirm").performClick()
        rule.waitUntil(5_000) { model.state.value.preferences.academicCalendar.lunchBreak.startTime == "08:40" }
        rule.onAllNodesWithTag("calendar-lunch-conflict").assertCountEquals(0)
        assertNull(model.state.value.error)
    }

    @Test fun conflictingLunchBreakNeedsConfirmationAndKeepsTheWeekWarningAfterSaving() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"),
        ))
        val repository = HostScheduleRepository(ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"))
        val preferences = HostPreferencesRepository()
        val model = ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDateTime.parse("2026-09-05T09:00") }, { "id" })
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        // P3-07-R1: with only periods before the break the row must stay visible (bottom of the matrix).
        rule.onNodeWithTag("schedule-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("week-lunch-break").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("week-lunch-break-title").assertTextContains("午休")

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-08").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-minute-30").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()

        rule.onNodeWithTag("calendar-lunch-conflict").assertIsDisplayed()
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        rule.onNodeWithTag("terminal-dialog-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("calendar-lunch-conflict").assertCountEquals(0)
        assertEquals("11:40", model.state.value.preferences.academicCalendar.lunchBreak.startTime)
        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().assertTextContains("11:40")

        rule.onNodeWithTag("settings-calendar-lunch-start").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-08").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-minute-30").performScrollTo().performClick()
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("calendar-lunch-conflict-confirm").performClick()
        rule.waitUntil(5_000) { model.state.value.preferences.academicCalendar.lunchBreak.startTime == "08:30" }
        rule.onNodeWithTag("settings-calendar-lunch-conflict-note").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-lunch-conflict-note").assertTextContains("周课表不会显示午休条", substring = true)
        rule.onNodeWithTag("settings-calendar-lunch-conflict-note").assertTextContains("第 1、2 节", substring = true)
        rule.onNodeWithTag("settings-calendar-lunch-conflict-note").assertTextContains("节次优先", substring = true)

        rule.onNodeWithTag("schedule-tab").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("week-lunch-break").assertCountEquals(0)
        rule.onNodeWithTag("week-period-1").assertIsDisplayed()
    }

    @Test fun weekLunchBreakIsPlacedAboveAndBelowTheOnlyPeriodAndHiddenOnOverlap() {
        var lunch by mutableStateOf(LunchBreakSettings(true, "午休", "07:00", "07:50"))
        rule.setContent {
            QingKeAppContent(
                singlePeriodWeekState(lunch), null, MainTab.SCHEDULE, QingKeAppActions(),
                LocalDateTime.parse("2026-08-31T09:00"),
            )
        }
        rule.onNodeWithTag("week-lunch-break").performScrollTo().assertIsDisplayed()
        assertTrue("break above the only period", rule.onNodeWithTag("week-lunch-break").getUnclippedBoundsInRoot().top < rule.onNodeWithTag("week-period-1").getUnclippedBoundsInRoot().top)

        lunch = LunchBreakSettings(true, "午休", "09:00", "10:00"); rule.waitForIdle()
        rule.onNodeWithTag("week-lunch-break").performScrollTo().assertIsDisplayed()
        assertTrue("break below the only period", rule.onNodeWithTag("week-lunch-break").getUnclippedBoundsInRoot().top > rule.onNodeWithTag("week-period-1").getUnclippedBoundsInRoot().bottom)

        lunch = LunchBreakSettings(true, "午休", "08:30", "09:00"); rule.waitForIdle()
        rule.onAllNodesWithTag("week-lunch-break").assertCountEquals(0)
        rule.onNodeWithTag("week-period-1").assertIsDisplayed()

        lunch = LunchBreakSettings(true, "午休", "10:00", "09:00"); rule.waitForIdle()
        rule.onAllNodesWithTag("week-lunch-break").assertCountEquals(0)
        lunch = LunchBreakSettings(false, "午休", "07:00", "07:50"); rule.waitForIdle()
        rule.onAllNodesWithTag("week-lunch-break").assertCountEquals(0)
    }

    @Test fun calendarEditsFlowIntoTodayAndWeekImmediately() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"),
            Period(3, "10:00", "10:45"), Period(4, "14:00", "14:45"),
        ))
        val courses = listOf(Course("monday", "周一课", "老师", "#287B74", listOf(CourseSchedule("s", 1, 1, 1, 1, 18, RepeatRule.EVERY, ""))))
        val repository = HostScheduleRepository(ScheduleData(1, semester, courses, "1970-01-01T00:00:00Z"))
        val preferences = HostPreferencesRepository()
        val model = ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDateTime.parse("2026-09-05T09:00") }, { "id" })
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }
        rule.onNodeWithText("今天没有课程，享受空闲时间吧。").assertIsDisplayed()

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-exception-date").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-exception-calendar-next").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-exception-calendar-day-2026-09-05").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-calendar-exception-calendar-day-2026-09-05").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-add-exception").performScrollTo().performClick()
        rule.waitUntil(5_000) { model.state.value.preferences.academicCalendar.nonTeachingDates.contains("2026-09-05") }
        rule.onNodeWithTag("settings-calendar-non-teaching-2026-09-05").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("today-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("today-empty").assertIsDisplayed()
        rule.onNodeWithText("已设为停课日，今日不显示课程。").assertIsDisplayed()

        rule.onNodeWithTag("schedule-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("week-manifest-detail").assertTextContains("OFF DAY")
        rule.onNodeWithTag("week-lunch-break").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("week-lunch-break-title").assertTextContains("午休")

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("settings-calendar-lunch-toggle").performScrollTo().performClick()
        rule.waitUntil(5_000) { !model.state.value.preferences.academicCalendar.lunchBreak.isEnabled }
        rule.onNodeWithTag("schedule-tab").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("week-lunch-break").assertCountEquals(0)
        rule.onNodeWithTag("week-period-1").assertIsDisplayed()
        rule.onNodeWithTag("week-manifest-detail").assertTextContains("OFF DAY")
    }

    private class HostScheduleRepository(var data: ScheduleData) : ScheduleRepository {
        var writes = 0
        var courseWrites = 0
        var failWrite = false
        override suspend fun load(): ScheduleData = data
        override suspend fun replace(data: ScheduleData): ScheduleData = data.also { this.data = it }
        override suspend fun saveSemester(semester: Semester): ScheduleData = data.copy(semester = semester).also { data = it }
        override suspend fun saveSemesterWithCourses(semester: Semester, courses: List<Course>): ScheduleData {
            writes++
            if (failWrite) error("injected semester write failure")
            return data.copy(semester = semester, courses = courses).also { data = it }
        }
        override suspend fun saveCourse(course: Course): ScheduleData {
            courseWrites++
            val index = data.courses.indexOfFirst { it.id == course.id }
            return data.copy(courses = if (index < 0) data.courses + course else data.courses.toMutableList().also { it[index] = course }).also { data = it }
        }

        override suspend fun saveCourseAt(index: Int, expected: Course, course: Course): ScheduleData {
            courseWrites++
            return data.copy(courses = data.courses.toMutableList().also { it[index] = course }).also { data = it }
        }
        override suspend fun deleteCourseAt(index: Int, expected: Course): ScheduleData = data
        override suspend fun deleteCourse(id: String): ScheduleData = data
    }

    private class HostPreferencesRepository : SchedulePreferencesRepository {
        private var stored = SchedulePreferences.defaults
        var failUpdate = false
        var updates = 0
        override suspend fun load(): SchedulePreferences = stored
        override suspend fun save(preferences: SchedulePreferences): SchedulePreferences {
            if (failUpdate) error("injected preferences failure")
            updates++
            return preferences.also { stored = it }
        }
        override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences): SchedulePreferences = save(transform(stored))
    }

    private fun singlePeriodWeekState(lunch: LunchBreakSettings): ScheduleState {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45")))
        return ScheduleState(
            data = ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"),
            preferences = SchedulePreferences.defaults.copy(academicCalendar = AcademicCalendarPreferences(lunchBreak = lunch)),
            loadStatus = LoadStatus.READY,
        )
    }

    private fun assertDangerFilled(tag: String) {
        val bitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        var red = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if ((pixel shr 16 and 0xff) >= 180 && (pixel shr 8 and 0xff) <= 130 && (pixel and 0xff) <= 130) red++
            }
        }
        assertTrue(tag + " must paint the danger tone, redPixels=" + red, red >= 100)
    }

    private fun calendarState(calendar: AcademicCalendarPreferences) =
        settingsState().copy(preferences = SchedulePreferences.defaults.copy(academicCalendar = calendar))

    private fun formActions(
        onName: (String) -> Unit = {}, onToggle: () -> Unit = {}, onAdd: () -> Unit = {}, onRemove: (String) -> Unit = {}, onSave: () -> Unit = {}, onDismiss: () -> Unit = {},
        onDismissSemesterSave: () -> Unit = {}, onConfirmSemesterCascade: () -> Unit = {},
    ) = QingKeAppActions(updateName = onName, togglePeriods = onToggle, addPeriod = onAdd, removePeriod = onRemove, saveSemester = onSave, dismissError = onDismiss, dismissSemesterSave = onDismissSemesterSave, confirmSemesterCascade = onConfirmSemesterCascade)

    private fun assertCyanRail(panelTag: String) {
        rule.onNodeWithTag(panelTag, useUnmergedTree = true).performScrollTo()
        val bitmap = rule.onNodeWithTag(panelTag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val railColumns = (with(rule.density) { 4.dp.toPx() }.toInt()).coerceAtLeast(2)
        var cyan = 0
        for (x in 0 until minOf(railColumns, bitmap.width)) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                if (isCyanInk(pixel shr 16 and 0xff, pixel shr 8 and 0xff, pixel and 0xff)) cyan++
            }
        }
        assertTrue("%s cyan rail pixels=%d height=%d".format(panelTag, cyan, bitmap.height), cyan >= bitmap.height * 2)
    }

    private fun assertInsidePanel(tag: String, panelTag: String) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        val panel = rule.onNodeWithTag(panelTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag $node must stay inside $panelTag $panel", node.left >= panel.left - 1f && node.right <= panel.right + 1f && node.top >= panel.top - 1f && node.bottom <= panel.bottom + 1f)
    }

    private fun assertDividersInsidePanel(tag: String, panelTag: String, expected: Int) {
        val count = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(expected, count)
        for (index in 0 until count) {
            rule.onAllNodesWithTag(tag, useUnmergedTree = true)[index].performScrollTo()
            val panel = rule.onNodeWithTag(panelTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bounds = rule.onAllNodesWithTag(tag, useUnmergedTree = true)[index].fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $bounds must stay inside $panelTag $panel", bounds.left >= panel.left - 1f && bounds.right <= panel.right + 1f && bounds.top >= panel.top - 1f && bounds.bottom <= panel.bottom + 1f)
        }
    }

    private fun assertFitsInside(tag: String, rootTag: String) {
        val root = rule.onNodeWithTag(rootTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag $node must stay inside $rootTag $root", node.left >= root.left - 1f && node.right <= root.right + 1f)
    }

    private fun assertYellowTextOnInverseBar(vararg tags: String) {
        if (tags.size > 1) {
            val bar = pixelCounts(tags[0])
            assertTrue("%s must keep the inverse bar, dark=%d/%d".format(tags[0], bar[4], bar[0]), bar[4] * 100 >= bar[0] * 45)
        }
        val save = pixelCounts(tags.last())
        assertTrue("%s must paint signal text, yellow=%d/%d".format(tags.last(), save[2], save[0]), save[2] >= 12)
        assertTrue("%s must not be a filled yellow button, yellow=%d/%d".format(tags.last(), save[2], save[0]), save[2] * 100 <= save[0] * 45)
        assertTrue("%s must sit on the inverse bar, dark=%d/%d".format(tags.last(), save[4], save[0]), save[4] * 100 >= save[0] * 45)
    }

    private fun assertCompactSaveCard(spacerTag: String = "settings-bottom-spacer", subtitle: String = "COMMIT CHANGES") {
        rule.onNodeWithTag(spacerTag).performScrollTo(); rule.waitForIdle()
        val height = dpHeight("semester-save-body")
        assertTrue("semester-save-body height=$height", height in 56f..62f)
        val arrow = rule.onNodeWithTag("semester-save-arrow", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val minimum = with(rule.density) { 22.dp.toPx() }
        assertTrue("semester-save-arrow bounds=$arrow", arrow.width >= minimum && arrow.height >= minimum)
        val arrowInk = pixelCounts("semester-save-arrow")[3]
        assertTrue("semester-save-arrow ink=$arrowInk", arrowInk >= 60)
        val underlineHeight = dpHeight("semester-save-underline")
        assertTrue("semester-save-underline height=$underlineHeight", underlineHeight in 3f..6f)
        val cardWidth = rule.onNodeWithTag("semester-save", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        val underlineWidth = rule.onNodeWithTag("semester-save-underline", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        assertTrue("semester-save-underline width=$underlineWidth of $cardWidth", underlineWidth >= cardWidth * 0.9f)
        val underlineInk = pixelCounts("semester-save-underline")[2]
        assertTrue("semester-save-underline signal pixels=$underlineInk", underlineInk >= 150)
        rule.onNodeWithText(subtitle).assertIsDisplayed()
    }

    private fun dpHeight(tag: String): Float = with(rule.density) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height.toDp().value
    }

    private fun dpWidth(tag: String): Float = with(rule.density) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width.toDp().value
    }

    private fun pixelCounts(tag: String): IntArray {
        val bitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val counts = IntArray(5)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                counts[0]++
                if (isCyanInk(red, green, blue)) counts[1]++
                if (isSignalInk(red, green, blue)) counts[2]++
                if (isLightInk(red, green, blue)) counts[3]++
                if (isDarkInk(red, green, blue)) counts[4]++
            }
        }
        return counts
    }

    private fun isCyanInk(red: Int, green: Int, blue: Int) = red < 110 && green > 130 && blue > 150

    private fun isSignalInk(red: Int, green: Int, blue: Int) = red > 200 && green > 140 && blue < 100

    private fun isLightInk(red: Int, green: Int, blue: Int) = (red * 299 + green * 587 + blue * 114) / 1000 >= 140

    private fun isDarkInk(red: Int, green: Int, blue: Int) = red < 60 && green < 60 && blue < 60

    private fun assertAtLeast48Dp(tag: String, scroll: Boolean = false) {
        val node = rule.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val minimum = with(rule.density) { 48.dp.toPx() }
        assertTrue("$tag width=${bounds.width}", bounds.width >= minimum)
        assertTrue("$tag height=${bounds.height}", bounds.height >= minimum)
    }

    private fun assertFitsRootHorizontally(tag: String) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag left=${bounds.left} root=$root", bounds.left >= root.left)
        assertTrue("$tag right=${bounds.right} root=$root", bounds.right <= root.right)
    }

    private fun assertSingleLineFitsRoot(tag: String) {
        assertFitsRootHorizontally(tag)
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag height=${bounds.height}", bounds.height <= 48.dp.value * rule.density.density)
    }

    private fun assertCalendarChevronHasSignalPixels(tag: String) {
        val bitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val count = (0 until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x ->
            val pixel = bitmap.getPixel(x, y); val red = pixel shr 16 and 0xff; val green = pixel shr 8 and 0xff; val blue = pixel and 0xff
            red > 215 && green > 155 && blue < 70
        } }
        assertTrue("$tag signal pixels=$count", count >= 12)
    }

    private fun assertCenteredPlusGeometry(bitmap: Bitmap, appearance: AppearanceMode) {
        val margin = maxOf(1, (minOf(bitmap.width, bitmap.height) * .15f).toInt())
        val innerXs = margin until bitmap.width - margin; val innerYs = margin until bitmap.height - margin
        fun isForeground(x: Int, y: Int): Boolean {
            val pixel = bitmap.getPixel(x, y); val rgb = pixel and 0xffffff
            return (pixel ushr 24) > 0 && if (appearance == AppearanceMode.LIGHT) rgb < 0x404040 else rgb > 0xd0d0d0
        }
        // Cropping out the square border keeps its symmetric bounds from masking a translated plus.
        val minimumCoverage = (minOf(innerXs.count(), innerYs.count()) * .7f).toInt()
        val horizontalRows = innerYs.filter { y -> innerXs.count { x -> isForeground(x, y) } >= minimumCoverage }
        val verticalColumns = innerXs.filter { x -> innerYs.count { y -> isForeground(x, y) } >= minimumCoverage }
        assertTrue("$appearance plus horizontal stroke=$horizontalRows", horizontalRows.isNotEmpty())
        assertTrue("$appearance plus vertical stroke=$verticalColumns", verticalColumns.isNotEmpty())
        val canvasCenterX = (bitmap.width - 1) / 2f; val canvasCenterY = (bitmap.height - 1) / 2f
        val horizontalCenterY = (horizontalRows.first() + horizontalRows.last()) / 2f
        val verticalCenterX = (verticalColumns.first() + verticalColumns.last()) / 2f
        assertTrue("$appearance plus horizontal center=$horizontalCenterY canvas=$canvasCenterY", kotlin.math.abs(horizontalCenterY - canvasCenterY) <= 1.5f)
        assertTrue("$appearance plus vertical center=$verticalCenterX canvas=$canvasCenterX", kotlin.math.abs(verticalCenterX - canvasCenterX) <= 1.5f)
        val horizontalXs = innerXs.filter { x -> horizontalRows.any { y -> isForeground(x, y) } }
        val verticalYs = innerYs.filter { y -> verticalColumns.any { x -> isForeground(x, y) } }
        val leftArm = canvasCenterX - horizontalXs.min(); val rightArm = horizontalXs.max() - canvasCenterX
        val topArm = canvasCenterY - verticalYs.min(); val bottomArm = verticalYs.max() - canvasCenterY
        assertTrue("$appearance plus left=$leftArm right=$rightArm", kotlin.math.abs(leftArm - rightArm) <= 1.5f)
        assertTrue("$appearance plus top=$topArm bottom=$bottomArm", kotlin.math.abs(topArm - bottomArm) <= 1.5f)
    }

    private fun assertRoundedCreateCourseIcon(bitmap: Bitmap, appearance: AppearanceMode) {
        fun isInk(x: Int, y: Int): Boolean {
            val pixel = bitmap.getPixel(x, y); val red = pixel shr 16 and 0xff; val green = pixel shr 8 and 0xff; val blue = pixel and 0xff
            return if (appearance == AppearanceMode.LIGHT) red < 70 && green < 75 && blue < 80 else red > 185 && green > 190 && blue > 190
        }
        val corner = (0 until (bitmap.width * .06f).toInt()).sumOf { x -> (0 until (bitmap.height * .06f).toInt()).count { y -> isInk(x, y) } }
        val topEdge = ((bitmap.width * .35f).toInt() until (bitmap.width * .65f).toInt()).sumOf { x -> (0 until (bitmap.height * .18f).toInt()).count { y -> isInk(x, y) } }
        val leftEdge = (0 until (bitmap.width * .18f).toInt()).sumOf { x -> ((bitmap.height * .35f).toInt() until (bitmap.height * .65f).toInt()).count { y -> isInk(x, y) } }
        assertTrue("$appearance rounded icon corner ink=$corner", corner == 0)
        assertTrue("$appearance rounded icon top edge ink=$topEdge", topEdge >= 4)
        assertTrue("$appearance rounded icon left edge ink=$leftEdge", leftEdge >= 4)
    }

    private fun assertAddChromeIsActuallyDrawn(appearance: AppearanceMode) {
        val bitmap = rule.onNodeWithTag("today-add-visual", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        fun channels(pixel: Int) = intArrayOf(pixel shr 16 and 0xff, pixel shr 8 and 0xff, pixel and 0xff)
        fun isWhiteFrame(pixel: Int) = channels(pixel).let { it[0] > 245 && it[1] > 225 && it[2] > 160 }
        fun isBlack(pixel: Int) = channels(pixel).let { it[0] < 70 && it[1] < 70 && it[2] < 70 }
        fun framePixels(xs: IntRange, ys: IntRange, predicate: (Int) -> Boolean) = xs.sumOf { x -> ys.count { y -> predicate(bitmap.getPixel(x, y)) } }
        val whiteFoldPixels = ((bitmap.width * .80f).toInt() until bitmap.width).sumOf { x -> (0 until (bitmap.height * .24f).toInt()).count { y -> channels(bitmap.getPixel(x, y)).let { it[0] > 245 && it[1] > 225 && it[2] > 160 } } }
        val shadowPixels = ((bitmap.width * .76f).toInt() until bitmap.width).sumOf { x -> ((bitmap.height * .02f).toInt() until (bitmap.height * .28f).toInt()).count { y -> channels(bitmap.getPixel(x, y)).let { it[0] in 170..245 && it[1] in 120..205 && it[2] < 40 } } }
        val inset = (3.dp.value * rule.density.density).toInt(); val tolerance = (1.dp.value * rule.density.density).toInt()
        val topXs = (bitmap.width / 6)..(bitmap.width * 2 / 3); val topYs = (inset - tolerance)..(inset + tolerance)
        val bottomXs = (bitmap.width / 6)..(bitmap.width * 2 / 3); val bottomYs = (bitmap.height - inset - tolerance)..(bitmap.height - inset + tolerance)
        val leftXs = (inset - tolerance)..(inset + tolerance); val leftYs = (bitmap.height / 5)..(bitmap.height * 3 / 4)
        val rightXs = (bitmap.width - inset - tolerance)..(bitmap.width - inset + tolerance); val rightYs = (bitmap.height / 3)..(bitmap.height * 3 / 4)
        val topFrame = framePixels(topXs, topYs, ::isWhiteFrame); val bottomFrame = framePixels(bottomXs, bottomYs, ::isWhiteFrame)
        val leftFrame = framePixels(leftXs, leftYs, ::isWhiteFrame); val rightFrame = framePixels(rightXs, rightYs, ::isWhiteFrame)
        val blackFramePixels = framePixels(topXs, topYs, ::isBlack) + framePixels(bottomXs, bottomYs, ::isBlack) + framePixels(leftXs, leftYs, ::isBlack) + framePixels(rightXs, rightYs, ::isBlack)
        assertTrue("$appearance ADD black inner frame pixels=$blackFramePixels", blackFramePixels == 0)
        assertTrue("$appearance ADD top white inner frame pixels=$topFrame", topFrame >= 20)
        assertTrue("$appearance ADD bottom white inner frame pixels=$bottomFrame", bottomFrame >= 20)
        assertTrue("$appearance ADD left white inner frame pixels=$leftFrame", leftFrame >= 20)
        assertTrue("$appearance ADD right white inner frame pixels=$rightFrame", rightFrame >= 20)
        assertTrue("$appearance ADD translucent white fold pixels=$whiteFoldPixels", whiteFoldPixels >= 12)
        assertTrue("$appearance ADD fold shadow pixels=$shadowPixels", shadowPixels >= 2)
    }

    private fun assertTodayAddPlusIsThinAndCentered() {
        val bounds = rule.onNodeWithTag("today-add-plus", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val expected = 25.dp.value * rule.density.density
        assertTrue("ADD plus width=${bounds.width}", kotlin.math.abs(bounds.width - expected) <= 1f)
        assertTrue("ADD plus height=${bounds.height}", kotlin.math.abs(bounds.height - expected) <= 1f)
        val bitmap = rule.onNodeWithTag("today-add-plus", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        fun isDarkInk(x: Int, y: Int): Boolean {
            val pixel = bitmap.getPixel(x, y)
            return (pixel shr 16 and 0xff) < 70 && (pixel shr 8 and 0xff) < 70 && (pixel and 0xff) < 70
        }
        val horizontalRows = (0 until bitmap.height).filter { y -> (0 until bitmap.width).count { x -> isDarkInk(x, y) } >= bitmap.width * .45f }
        val verticalColumns = (0 until bitmap.width).filter { x -> (0 until bitmap.height).count { y -> isDarkInk(x, y) } >= bitmap.height * .45f }
        assertTrue("ADD plus horizontal stroke=$horizontalRows", horizontalRows.isNotEmpty())
        assertTrue("ADD plus vertical stroke=$verticalColumns", verticalColumns.isNotEmpty())
        val centerX = (bitmap.width - 1) / 2f; val centerY = (bitmap.height - 1) / 2f
        val horizontalCenter = (horizontalRows.first() + horizontalRows.last()) / 2f
        val verticalCenter = (verticalColumns.first() + verticalColumns.last()) / 2f
        assertTrue("ADD plus horizontal center=$horizontalCenter expected=$centerY", kotlin.math.abs(horizontalCenter - centerY) <= 1.5f)
        assertTrue("ADD plus vertical center=$verticalCenter expected=$centerX", kotlin.math.abs(verticalCenter - centerX) <= 1.5f)
        assertTrue("ADD plus horizontal width=${horizontalRows.size}", horizontalRows.size <= 2.2f * rule.density.density + 1f)
        assertTrue("ADD plus vertical width=${verticalColumns.size}", verticalColumns.size <= 2.2f * rule.density.density + 1f)
        val horizontalXs = (0 until bitmap.width).filter { x -> horizontalRows.any { y -> isDarkInk(x, y) } }
        val verticalYs = (0 until bitmap.height).filter { y -> verticalColumns.any { x -> isDarkInk(x, y) } }
        val leftArm = centerX - horizontalXs.min(); val rightArm = horizontalXs.max() - centerX
        val topArm = centerY - verticalYs.min(); val bottomArm = verticalYs.max() - centerY
        assertTrue("ADD plus left=$leftArm right=$rightArm", kotlin.math.abs(leftArm - rightArm) <= 1.5f)
        assertTrue("ADD plus top=$topArm bottom=$bottomArm", kotlin.math.abs(topArm - bottomArm) <= 1.5f)
    }

    /** P3-04-R8: the blocked-save dialog must carry a real danger-filled action button. */
    private fun assertDialogHasDangerActionButton(tag: String = "course-save-error-dismiss") {
        val bitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        var danger = 0
        var total = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = pixel shr 16 and 0xff; val green = pixel shr 8 and 0xff; val blue = pixel and 0xff
                total++
                if (red >= 150 && green <= 130 && blue <= 130) danger++
            }
        }
        assertTrue("$tag danger pixels=$danger/$total", danger * 100 >= total * 80)
    }

    private fun assertModalSurfaceIsOpaque(appearance: AppearanceMode) {
        val bitmap = rule.onNodeWithTag("course-delete-confirm", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val center = (bitmap.width / 5 until bitmap.width * 4 / 5).sumOf { x -> (bitmap.height / 4 until bitmap.height * 3 / 4).count { y ->
            val pixel = bitmap.getPixel(x, y); val red = pixel shr 16 and 0xff; val green = pixel shr 8 and 0xff; val blue = pixel and 0xff
            if (appearance == AppearanceMode.LIGHT) red >= 220 && green >= 220 && blue >= 220 else red in 8..55 && green in 15..75 && blue in 15..80
        } }
        assertTrue("$appearance opaque modal pixels=$center", center >= 80)
    }

    private fun assertModalProbeRejectsBackdropStripes(dark: Boolean) {
        val bitmap = rule.onNodeWithTag("modal-opacity-probe", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val y = (bitmap.height * .10f).toInt()
        val samples = ((bitmap.width * .35f).toInt() until (bitmap.width * .65f).toInt()).map { x -> bitmap.getPixel(x, y) }
        val largestAdjacentChange = samples.zipWithNext().maxOf { (first, second) ->
            maxOf(kotlin.math.abs((first shr 16 and 0xff) - (second shr 16 and 0xff)), kotlin.math.abs((first shr 8 and 0xff) - (second shr 8 and 0xff)), kotlin.math.abs((first and 0xff) - (second and 0xff)))
        }
        assertTrue("dark=$dark modal must block the 3dp black/white backdrop stripes; adjacent change=$largestAdjacentChange", largestAdjacentChange <= 8)
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

    /**
     * P3-06-R7 evidence: the real settings flow is replayed once so the same APK that ships the rules also
     * produces the screenshots. Write failures cannot be injected in the production app, so that path stays
     * asserted by tests instead of a fabricated screenshot.
     */
    /** P3-04-R8 evidence: the real blocked-save flow plus the unchanged conflict box. */
    /**
     * P3-04-R8-R1: the shared TerminalDialog scrim must really block the covered screen. The two controls
     * behind the modal are touched at their raw screen coordinates through the root node, never through their
     * semantics click action, so a pass-through scrim would fire them.
     */
    /**
     * P3-04-R8-R2 evidence: one real first-boot → save → settings flow in which every time value is reached
     * with real Android system swipes (Instrumentation#sendPointerSync), never with a semantic scroll action.
     */
    @Test fun p3r04R8R2ModalScrollEvidence() {
        val repository = HostScheduleRepository(ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"))
        val preferences = HostPreferencesRepository()
        val model = ScheduleViewModel(ScheduleAppState(repository, preferences), { LocalDateTime.parse("2026-09-20T09:00") }, uniqueIds())
        val evidence = mutableListOf("P3-04-R8-R2 real-gesture evidence (API 37 ARM64 1080x2400@420dpi, font_scale 1.0)")
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        // 1) first boot period 1 (08:00-08:45): 08:00 → 07:20, both targets outside the initial viewport
        rule.onNodeWithTag("daily-periods-toggle").performScrollTo().performClick(); rule.waitForIdle()
        val periodId = model.form.value!!.periods.first().id
        rule.onNodeWithTag("period-" + periodId + "-start").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("08:00")
        assertFalse("hour 07 must start outside the first-boot viewport", isDisplayed("terminal-time-picker-hour-07"))
        val bootHourSwipes = systemSwipeUntilDisplayed("terminal-time-picker-hour-list", "terminal-time-picker-hour-07", down = true)
        systemTapOn("terminal-time-picker-hour-07"); rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:00")
        assertFalse("minute 20 must start outside the first-boot viewport", isDisplayed("terminal-time-picker-minute-20"))
        val bootMinuteSwipes = systemSwipeUntilDisplayed("terminal-time-picker-minute-list", "terminal-time-picker-minute-20", down = false)
        systemTapOn("terminal-time-picker-minute-20"); rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:20")
        evidence.add("first boot picker: hour swipes=" + bootHourSwipes + " minute swipes=" + bootMinuteSwipes + " " + nodeLine("terminal-time-picker"))
        saveR8R2Screenshot("p3-04-r8-r2-01-first-boot-picker-after-real-swipes.png")

        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(LocalTime.of(7, 20), model.form.value!!.periods.first().start)
        rule.onNodeWithTag("period-" + periodId + "-start").performScrollTo()
        evidence.add("first boot draft after confirm: start=" + model.form.value!!.periods.first().start + " writes=" + repository.writes)
        saveR8R2Screenshot("p3-04-r8-r2-02-first-boot-draft-after-confirm.png")

        rule.onNodeWithTag("semester-save").performScrollTo().performClick()
        rule.waitUntil(5_000) { repository.writes == 1 }

        // 2) the real settings page repeats the same gesture: 07:20 → 06:35
        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        if (!model.form.value!!.periodsExpanded) {
            rule.onNodeWithTag("daily-periods-toggle").performScrollTo().performClick(); rule.waitForIdle()
        }
        rule.onNodeWithTag("period-" + periodId + "-start").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:20")
        assertFalse("hour 06 must start outside the settings viewport", isDisplayed("terminal-time-picker-hour-06"))
        val settingsHourSwipes = systemSwipeUntilDisplayed("terminal-time-picker-hour-list", "terminal-time-picker-hour-06", down = true)
        systemTapOn("terminal-time-picker-hour-06"); rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("06:20")
        assertFalse("minute 35 must start outside the settings viewport", isDisplayed("terminal-time-picker-minute-35"))
        val settingsMinuteSwipes = systemSwipeUntilDisplayed("terminal-time-picker-minute-list", "terminal-time-picker-minute-35", down = false)
        systemTapOn("terminal-time-picker-minute-35"); rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("06:35")
        evidence.add("settings picker: hour swipes=" + settingsHourSwipes + " minute swipes=" + settingsMinuteSwipes + " " + nodeLine("terminal-time-picker"))
        saveR8R2Screenshot("p3-04-r8-r2-03-settings-picker-after-real-swipes.png")
        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(LocalTime.of(6, 35), model.form.value!!.periods.first().start)
        rule.onNodeWithTag("semester-save").performScrollTo().performClick()
        rule.waitUntil(5_000) { repository.writes == 2 }
        evidence.add("settings save: period start=" + repository.data.semester!!.periods.first().startTime + " writes=" + repository.writes)

        // 3) the lunch break picker shares the same overlay: 14:00 → 13:30 (still conflict-free with periods)
        rule.onNodeWithTag("settings-calendar-lunch-end").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-lunch-time-picker-value").assertTextEquals("14:00")
        assertFalse("hour 13 must start outside the lunch viewport", isDisplayed("terminal-lunch-time-picker-hour-13"))
        val lunchHourSwipes = systemSwipeUntilDisplayed("terminal-lunch-time-picker-hour-list", "terminal-lunch-time-picker-hour-13", down = true)
        systemTapOn("terminal-lunch-time-picker-hour-13"); rule.onNodeWithTag("terminal-lunch-time-picker-value").assertTextEquals("13:00")
        assertFalse("minute 30 must start outside the lunch viewport", isDisplayed("terminal-lunch-time-picker-minute-30"))
        val lunchMinuteSwipes = systemSwipeUntilDisplayed("terminal-lunch-time-picker-minute-list", "terminal-lunch-time-picker-minute-30", down = false)
        systemTapOn("terminal-lunch-time-picker-minute-30"); rule.onNodeWithTag("terminal-lunch-time-picker-value").assertTextEquals("13:30")
        evidence.add("lunch picker: hour swipes=" + lunchHourSwipes + " minute swipes=" + lunchMinuteSwipes + " " + nodeLine("terminal-lunch-time-picker"))
        saveR8R2Screenshot("p3-04-r8-r2-04-lunch-picker-after-real-swipes.png")
        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals("13:30", model.state.value.preferences.academicCalendar.lunchBreak.endTime)
        assertEquals(null, model.lunchBreakConflict.value)
        evidence.add("lunch preferences: end=" + model.state.value.preferences.academicCalendar.lunchBreak.endTime + " updates=" + preferences.updates)

        // 4) measure whether the colour dialog really needs internal scrolling at this size
        rule.onNodeWithTag("today-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("today-add-course").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-custom-color").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-color-dialog").assertIsDisplayed()
        val windowHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val dialogBounds = rule.onNodeWithTag("course-color-dialog", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tabsBefore = rule.onNodeWithTag("course-color-mode-SLIDERS", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        systemSwipeOn("course-color-dialog", down = false, distancePx = 300f)
        val tabsAfter = rule.onNodeWithTag("course-color-mode-SLIDERS", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        evidence.add(
            "color dialog: panel height=%.0f window=%.0f clipped=%s tabs top %.0f -> %.0f".format(
                dialogBounds.height, windowHeight, dialogBounds.height >= windowHeight - 2f, tabsBefore.top, tabsAfter.top,
            ),
        )
        saveR8R2Screenshot("p3-04-r8-r2-05-color-dialog-measured.png")

        saveR8R2Text("node-and-state-verification-20260919.txt", evidence.joinToString("\n") + "\n")
    }

    private fun systemSwipeUntilDisplayed(listTag: String, itemTag: String, down: Boolean, limit: Int = 18): Int {
        var swipes = 0
        while (!isDisplayed(itemTag) && swipes < limit) {
            systemSwipeOn(listTag, down = down)
            swipes++
        }
        assertTrue("$itemTag must appear after real system swipes, swipes=$swipes", isDisplayed(itemTag))
        return swipes
    }

    private fun r8r2EvidenceDirectory(): File = File(
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: rule.activity.cacheDir.absolutePath,
        "p3-04-r8-r2-modal-scroll",
    ).also { check(it.exists() || it.mkdirs()) }

    private fun saveR8R2Screenshot(name: String) {
        File(r8r2EvidenceDirectory(), name).outputStream().use { output ->
            check(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun saveR8R2Text(name: String, content: String) {
        File(r8r2EvidenceDirectory(), name).writeText(content)
    }

    /**
     * P3-04-R8-R2: the Compose test injection does not reproduce the field defect, so this case drives the
     * real Android input pipeline via Instrumentation#sendPointerSync. With the R1 parent-scrim structure the
     * column does not move at all under real input; with the scrim behind the panel it scrolls.
     */
    @Test fun timePickerHourColumnScrollsWithRealSystemInput() {
        var form by mutableStateOf(
            existingForm().copy(periods = existingForm().periods.map { if (it.id == "q1") it.copy(start = LocalTime.of(11, 40), end = LocalTime.of(12, 40)) else it }),
        )
        var start: Pair<String, LocalTime>? = null
        rule.setContent {
            QingKeAppContent(settingsState(), form, MainTab.SETTINGS, QingKeAppActions(updatePeriodStart = { id, value -> start = id to value }))
        }
        rule.onNodeWithTag("period-q1-start").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
        assertFalse("hour 07 must start outside the viewport", isDisplayed("terminal-time-picker-hour-07"))

        var swipes = 0
        while (!isDisplayed("terminal-time-picker-hour-07") && swipes < 6) {
            systemSwipeOn("terminal-time-picker-hour-list", down = true)
            swipes++
        }
        assertTrue("hour 07 must appear after real system swipes, swipes=$swipes", isDisplayed("terminal-time-picker-hour-07"))
        systemTapOn("terminal-time-picker-hour-07")
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:40")

        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals("q1", start?.first)
        assertEquals(LocalTime.of(7, 40), start?.second)
    }

    /** Injects a real touch swipe through the Android input pipeline (not Compose test input). */
    private fun systemSwipeOn(tag: String, down: Boolean, distancePx: Float = 400f, steps: Int = 80) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInWindow
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val x = root.left + bounds.center.x
        val startY = root.top + if (down) bounds.center.y - distancePx / 2f else bounds.center.y + distancePx / 2f
        val endY = startY + if (down) distancePx else -distancePx
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        instrumentation.sendPointerSync(touchEvent(downTime, eventTime, MotionEvent.ACTION_DOWN, x, startY))
        for (step in 1..steps) {
            eventTime += 5
            val fraction = step.toFloat() / steps
            instrumentation.sendPointerSync(touchEvent(downTime, eventTime, MotionEvent.ACTION_MOVE, x, startY + (endY - startY) * fraction))
        }
        eventTime += 16
        instrumentation.sendPointerSync(touchEvent(downTime, eventTime, MotionEvent.ACTION_UP, x, endY))
        rule.waitForIdle()
    }

    private fun systemTapOn(tag: String) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInWindow
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val x = root.left + bounds.center.x
        val y = root.top + bounds.center.y
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(touchEvent(downTime, downTime, MotionEvent.ACTION_DOWN, x, y))
        instrumentation.sendPointerSync(touchEvent(downTime, downTime + 60, MotionEvent.ACTION_UP, x, y))
        rule.waitForIdle()
    }

    private fun touchEvent(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }

    /** Real finger swipes on a picker column until [itemTag] is visible; never a semantic scrollTo. */
    /**
     * P3-04-R8-R2: a real finger drag on a picker column. The gesture is a coordinate drag through
     * [performTouchInput] with zero end velocity so the test controls the scroll step and can prove that the
     * target was outside the viewport before the drag; no semantic scroll action is used anywhere.
     */
    private fun dragColumn(listTag: String, up: Boolean, distancePx: Float = 300f) {
        rule.onNodeWithTag(listTag).performTouchInput {
            val dy = if (up) -distancePx else distancePx
            swipeWithVelocity(Offset(centerX, centerY), Offset(centerX, centerY + dy), 0f, durationMillis = 400)
        }
        rule.waitForIdle()
    }

    private fun swipeUntilDisplayed(listTag: String, itemTag: String, up: Boolean, limit: Int = 10): Int {
        var swipes = 0
        while (!isDisplayed(itemTag) && swipes < limit) {
            dragColumn(listTag, up)
            swipes++
        }
        assertTrue("$itemTag must become visible after real drags, drags=$swipes", isDisplayed(itemTag))
        return swipes
    }

    private fun isTextDisplayed(text: String): Boolean =
        runCatching { rule.onNodeWithText(text, substring = true).assertIsDisplayed() }.isSuccess

    private fun isDisplayed(tag: String): Boolean =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() &&
            runCatching { rule.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    @Test fun onboardingTimePickerColumnsScrollWithRealSwipes() {
        var form by mutableStateOf(defaultForm(count = 3, expanded = true))
        var start: Pair<String, LocalTime>? = null
        rule.setContent {
            QingKeAppContent(onboarding(), form, MainTab.TODAY, QingKeAppActions(updatePeriodStart = { id, value -> start = id to value }))
        }
        rule.onNodeWithTag("period-p1-start").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()

        // the columns start on 08:00, so both targets are outside the initial viewport
        assertFalse("hour 18 must start outside the viewport", isDisplayed("terminal-time-picker-hour-18"))
        // one plain full-height swipeUp first (the literal gesture the user failed to make), then measured drags
        rule.onNodeWithTag("terminal-time-picker-hour-list").performTouchInput { swipeUp() }
        rule.waitForIdle()
        swipeUntilDisplayed("terminal-time-picker-hour-list", "terminal-time-picker-hour-18", up = true)
        rule.onNodeWithTag("terminal-time-picker-hour-18").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("18:00")

        assertFalse("minute 20 must start outside the viewport", isDisplayed("terminal-time-picker-minute-20"))
        swipeUntilDisplayed("terminal-time-picker-minute-list", "terminal-time-picker-minute-20", up = true)
        rule.onNodeWithTag("terminal-time-picker-minute-20").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("18:20")

        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals("p1", start?.first)
        assertEquals(LocalTime.of(18, 20), start?.second)
        rule.onAllNodesWithTag("terminal-time-picker").assertCountEquals(0)
    }

    @Test fun settingsTimePickerColumnsScrollWithRealSwipes() {
        var form by mutableStateOf(
            existingForm().copy(periods = existingForm().periods.map { if (it.id == "q1") it.copy(start = LocalTime.of(11, 40), end = LocalTime.of(12, 40)) else it }),
        )
        var start: Pair<String, LocalTime>? = null
        rule.setContent {
            QingKeAppContent(settingsState(), form, MainTab.SETTINGS, QingKeAppActions(updatePeriodStart = { id, value -> start = id to value }))
        }
        rule.onNodeWithTag("period-q1-start").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("11:40")

        // hour 07 is before the visible window, so it needs a real downward swipe
        assertFalse("hour 07 must start outside the viewport", isDisplayed("terminal-time-picker-hour-07"))
        swipeUntilDisplayed("terminal-time-picker-hour-list", "terminal-time-picker-hour-07", up = false)
        rule.onNodeWithTag("terminal-time-picker-hour-07").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:40")

        // minute 20 is before the visible window as well (the picker opened on 40)
        assertFalse("minute 20 must start outside the viewport", isDisplayed("terminal-time-picker-minute-20"))
        swipeUntilDisplayed("terminal-time-picker-minute-list", "terminal-time-picker-minute-20", up = false)
        rule.onNodeWithTag("terminal-time-picker-minute-20").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker-value").assertTextEquals("07:20")

        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals("q1", start?.first)
        assertEquals(LocalTime.of(7, 20), start?.second)
    }

    @Test fun lunchBreakTimePickerColumnsScrollWithRealSwipes() {
        var requested: Pair<LocalTime, LocalTime>? = null
        rule.setContent {
            QingKeAppContent(
                settingsState(), existingForm(), MainTab.SETTINGS,
                QingKeAppActions(requestLunchBreakTimes = { from, to -> requested = from to to }),
            )
        }
        rule.onNodeWithTag("settings-calendar-lunch-end").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-lunch-time-picker").assertIsDisplayed()
        rule.onNodeWithTag("terminal-lunch-time-picker-value").assertTextEquals("14:00")

        // 23:00 is far below the 14:00 the end picker opens on, and 23:20 keeps the range valid
        assertFalse("hour 23 must start outside the lunch picker viewport", isDisplayed("terminal-lunch-time-picker-hour-23"))
        swipeUntilDisplayed("terminal-lunch-time-picker-hour-list", "terminal-lunch-time-picker-hour-23", up = true)
        rule.onNodeWithTag("terminal-lunch-time-picker-hour-23").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-lunch-time-picker-value").assertTextEquals("23:00")

        assertFalse("minute 20 must start outside the lunch picker viewport", isDisplayed("terminal-lunch-time-picker-minute-20"))
        swipeUntilDisplayed("terminal-lunch-time-picker-minute-list", "terminal-lunch-time-picker-minute-20", up = true)
        rule.onNodeWithTag("terminal-lunch-time-picker-minute-20").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-lunch-time-picker-value").assertTextEquals("23:20")

        rule.onNodeWithTag("terminal-lunch-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(LocalTime.of(11, 40) to LocalTime.of(23, 20), requested)
    }

    /** P3-04-R8-R1: the shared time picker is the other full-screen scrim and must block the page behind it. */
    @Test fun timePickerScrimBlocksTouchesToTheSettingsPageBehindIt() {
        var form by mutableStateOf(existingForm().copy(periodsExpanded = true))
        var saves = 0
        var starts = 0
        rule.setContent {
            QingKeAppContent(
                settingsState(), form, MainTab.SETTINGS,
                QingKeAppActions(saveSemester = { saves++ }, updatePeriodStart = { _, _ -> starts++ }),
            )
        }
        rule.onNodeWithTag("period-q1-start").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()
        rule.onNodeWithTag("terminal-time-picker-backdrop", useUnmergedTree = true).assertHasNoClickAction()

        val saveBounds = rule.onNodeWithTag("semester-save-toolbar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        touchAt(saveBounds.center)
        rule.waitForIdle()
        assertEquals(0, saves)
        rule.onNodeWithTag("terminal-time-picker").assertIsDisplayed()

        rule.onNodeWithTag("terminal-time-picker-confirm").performClick(); rule.waitForIdle()
        assertEquals(1, starts)
        rule.onAllNodesWithTag("terminal-time-picker").assertCountEquals(0)
    }

    @Test fun invalidDialogScrimBlocksTouchesToTheEditorBehindIt() {
        val schedule = CourseScheduleFormState("blocked", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var editor by mutableStateOf<CourseEditorState?>(CourseEditorState(CourseEditorMode.EDIT, name = "被阻断", schedules = listOf(schedule)))
        var saves = 0
        var closes = 0
        var dismissals = 0
        rule.setContent {
            QingKeAppContent(
                readyToday(), null, MainTab.TODAY,
                QingKeAppActions(
                    saveCourse = { saves++ }, closeCourseEditor = { closes++ },
                    dismissCourseConfirmation = { dismissals++; editor = editor?.copy(confirmation = null) },
                ),
                editor = editor,
            )
        }
        val saveBounds = rule.onNodeWithTag("course-save-toolbar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val closeBounds = rule.onNodeWithTag("course-editor-close", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        editor = CourseEditorState(
            CourseEditorMode.EDIT, name = "被阻断", schedules = listOf(schedule),
            confirmation = CourseEditorConfirmation.Invalid("该上课安排已存在，请勿重复添加。"),
        )
        rule.waitForIdle()
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        rule.onNodeWithTag("course-save-error-backdrop", useUnmergedTree = true).assertHasNoClickAction()

        touchAt(saveBounds.center)
        touchAt(closeBounds.center)
        rule.waitForIdle()
        assertEquals(0, saves)
        assertEquals(0, closes)
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        assertEquals("被阻断", editor!!.name)
        assertEquals(1, editor!!.schedules.size)

        // the dialog's own action is still reachable and fires exactly once
        rule.onNodeWithTag("course-save-error-dismiss").performClick(); rule.waitForIdle()
        assertEquals(1, dismissals)
        rule.onAllNodesWithTag("course-save-error").assertCountEquals(0)
    }

    @Test fun conflictDialogButtonsStayClickableUnderTheScrim() {
        val schedule = CourseScheduleFormState("conflict", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var confirms = 0
        var dismissals = 0
        var editor by mutableStateOf<CourseEditorState?>(
            CourseEditorState(
                CourseEditorMode.EDIT, name = "冲突", schedules = listOf(schedule),
                confirmation = CourseEditorConfirmation.Conflicts(Course("candidate", "候选", "", "#287B74", emptyList()), emptyList()),
            ),
        )
        rule.setContent {
            QingKeAppContent(
                readyToday(), null, MainTab.TODAY,
                QingKeAppActions(confirmSaveDespiteConflicts = { confirms++ }, dismissCourseConfirmation = { dismissals++ }),
                editor = editor,
            )
        }
        rule.onNodeWithTag("course-conflict-confirm").assertIsDisplayed()
        rule.onNodeWithTag("terminal-dialog-dismiss").performClick(); rule.waitForIdle()
        assertEquals(0, confirms)
        assertEquals(1, dismissals)
        rule.onNodeWithTag("course-conflict-confirm-confirm").performClick(); rule.waitForIdle()
        assertEquals(1, confirms)
    }

    @Test fun p3r04R8CourseSaveErrorDialogEvidence() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        val evidence = mutableListOf("P3-04-R8 API 37 ARM64 1080x2400@420dpi node verification")
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("today-add-course").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-name").performTextReplacement("重复安排课程")
        rule.onNodeWithTag("course-add-schedule").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        rule.onNodeWithText("无法保存课程", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("该上课安排已存在，请勿重复添加。", useUnmergedTree = true).assertIsDisplayed()
        evidence.add(nodeLine("course-save-error"))
        evidence.add(nodeLine("course-save-error-dismiss"))
        saveR8Screenshot("p3-04-r8-01-duplicate-blocked-dialog.png")

        rule.onNodeWithTag("course-save-error-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("course-save-error").assertCountEquals(0)
        rule.onNodeWithTag("course-schedule-" + model.editor.value!!.schedules.last().id).performScrollTo()
        evidence.add("after 返回修改: name=" + model.editor.value!!.name + " schedules=" + model.editor.value!!.schedules.size + " writes=" + repository.courseWrites)
        saveR8Screenshot("p3-04-r8-02-return-keeps-draft.png")

        rule.onNodeWithTag("course-editor-close").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-discard-confirm-confirm").performClick(); rule.waitForIdle()
        repository.data = repository.data.copy(courses = listOf(Course("existing", "已有课程", "", "#287B74", listOf(CourseSchedule("existing-slot", 6, 1, 1, 1, 18, RepeatRule.EVERY, "A101")))))
        model.retryLoad(); rule.waitForIdle()
        model.openNewCourse(); rule.waitForIdle()
        rule.onNodeWithTag("course-name").performTextReplacement("冲突课程")
        rule.onNodeWithTag("course-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-conflict-confirm").assertIsDisplayed()
        rule.onNodeWithTag("terminal-dialog-dismiss").assertIsDisplayed()
        evidence.add(nodeLine("course-conflict-confirm"))
        saveR8Screenshot("p3-04-r8-03-conflict-box-unchanged.png")

        saveR8Text("node-verification-20260919.txt", evidence.joinToString("\n") + "\n")
    }

    /** P3-04-R8 evidence: dark theme, 130% font scale and a 320dp narrow host for the same dialog. */
    @Test fun p3r04R8DialogVariantEvidence() {
        val schedule = CourseScheduleFormState("r8", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var appearance by mutableStateOf(AppearanceMode.DARK)
        var scale by mutableStateOf(1f)
        var narrow by mutableStateOf(false)
        val evidence = mutableListOf("P3-04-R8 dialog variants")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) {
                val page = @Composable {
                    QingKeAppContent(
                        readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)), null, MainTab.TODAY, QingKeAppActions(),
                        editor = CourseEditorState(CourseEditorMode.EDIT, name = "昼夜课程", schedules = listOf(schedule), confirmation = CourseEditorConfirmation.Invalid("该上课安排已存在，请勿重复添加。")),
                    )
                }
                if (narrow) Box(Modifier.size(320.dp, 720.dp).testTag("narrow-root")) { page() } else page()
            }
        }
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        evidence.add("dark: " + nodeLine("course-save-error") + " | " + nodeLine("course-save-error-dismiss"))
        saveR8Screenshot("p3-04-r8-04-dark-dialog.png")

        scale = 1.3f; rule.waitForIdle()
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        rule.onNodeWithText("无法保存课程", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("该上课安排已存在，请勿重复添加。", useUnmergedTree = true).assertIsDisplayed()
        evidence.add("font130: " + nodeLine("course-save-error") + " | " + nodeLine("course-save-error-dismiss"))
        saveR8Screenshot("p3-04-r8-05-font130-dialog.png")

        appearance = AppearanceMode.LIGHT; narrow = true; rule.waitForIdle()
        rule.onNodeWithTag("narrow-root").assertIsDisplayed()
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        rule.onNodeWithTag("course-save-error-dismiss").assertIsDisplayed()
        assertFitsInside("course-save-error", "narrow-root")
        rule.onNodeWithText("该上课安排已存在，请勿重复添加。", useUnmergedTree = true).assertIsDisplayed()
        evidence.add("narrow light: " + nodeLine("course-save-error") + " | " + nodeLine("course-save-error-dismiss"))
        saveR8Screenshot("p3-04-r8-06-narrow-320dp-dialog.png")

        saveR8Text("node-verification-variants-20260919.txt", evidence.joinToString("\n") + "\n")
    }

    @Test fun p3r06R7SettingsSaveDialogEvidence() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"),
        ))
        val course = Course("course", "高数", "", "#287B74", listOf(CourseSchedule("s", 6, 2, 2, 1, 18, RepeatRule.EVERY, "A101")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        val evidence = mutableListOf("P3-06-R7 API 37 ARM64 1080x2400@420dpi node verification")
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-name").performTextReplacement(" ")
        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-error").assertIsDisplayed()
        rule.onNodeWithText("无法保存学期设置").assertIsDisplayed()
        rule.onNodeWithText("请填写学期名称").assertIsDisplayed()
        evidence.add(nodeLine("semester-save-error"))
        evidence.add(nodeLine("semester-save-error-confirm"))
        saveCascadeScreenshot("p3-06-r7-01-blocked-top-save-error.png")
        rule.onNodeWithTag("semester-save-error-confirm").performClick(); rule.waitForIdle()

        rule.onNodeWithTag("semester-save").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-error").assertIsDisplayed()
        rule.onNodeWithText("请填写学期名称").assertIsDisplayed()
        evidence.add("bottom entry: " + nodeLine("semester-save-error"))
        saveCascadeScreenshot("p3-06-r7-02-blocked-bottom-save-error.png")
        rule.onNodeWithTag("semester-save-error-confirm").performClick(); rule.waitForIdle()

        rule.onNodeWithTag("semester-name").performTextReplacement("测试学期")
        val firstPeriod = model.form.value!!.periods.first().id
        rule.onNodeWithTag("period-" + firstPeriod + "-delete").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-cascade").assertIsDisplayed()
        rule.onNodeWithText("节次变更将影响已有课程").assertIsDisplayed()
        rule.onNodeWithText("确认保存并级联").assertIsDisplayed()
        rule.onNodeWithText("返回修改").assertIsDisplayed()
        rule.onNodeWithText("仅重新编号", substring = true).assertIsDisplayed()
        evidence.add(nodeLine("semester-cascade"))
        evidence.add(nodeLine("semester-cascade-summary"))
        evidence.add(nodeLine("semester-cascade-dismiss"))
        evidence.add(nodeLine("semester-cascade-confirm"))
        saveCascadeScreenshot("p3-06-r7-03-cascade-confirm.png")

        rule.onNodeWithTag("semester-cascade-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("semester-cascade").assertCountEquals(0)
        assertEquals(listOf(1, 2), model.form.value!!.periods.map { it.number })
        rule.onNodeWithTag("period-" + model.form.value!!.periods.first().id + "-row").performScrollTo(); rule.waitForIdle()
        saveCascadeScreenshot("p3-06-r7-04-return-keeps-draft.png")

        rule.onNodeWithTag("semester-save").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-cascade-confirm").performClick()
        rule.waitUntil(5_000) { model.state.value.data.semester?.periods?.size == 2 }
        evidence.add("after confirm: periods=" + model.state.value.data.semester!!.periods.map { it.number } +
            " courseSchedules=" + model.state.value.data.courses.flatMap { it.schedules }.map { it.startPeriod to it.endPeriod })
        rule.onNodeWithTag("schedule-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("week-item-5:0:0").performScrollTo().assertIsDisplayed()
        evidence.add("week matrix: " + nodeLine("week-period-1-start"))
        saveCascadeScreenshot("p3-06-r7-05-cascade-saved-week.png")

        rule.onNodeWithTag("today-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("today-featured-course-0-0").assertIsDisplayed()
        saveCascadeScreenshot("p3-06-r7-06-cascade-saved-today.png")

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        if (!model.form.value!!.periodsExpanded) {
            rule.onNodeWithTag("daily-periods-toggle").performScrollTo().performClick(); rule.waitForIdle()
        }
        rule.onNodeWithTag("period-" + model.form.value!!.periods.first().id + "-delete").performScrollTo(); rule.waitForIdle()
        evidence.add("saved settings draft: periods=" + model.form.value!!.periods.map { it.number })
        saveCascadeScreenshot("p3-06-r7-07-saved-settings.png")
        saveCascadeText("node-verification-20260919.txt", evidence.joinToString("\n") + "\n")
    }

    @Test fun bothSaveEntriesShareOneCascadeDialogAndReturnKeepsTheDraft() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"),
        ))
        val course = Course("course", "高数", "", "#287B74", listOf(CourseSchedule("s", 6, 2, 2, 1, 18, RepeatRule.EVERY, "A101")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        val firstPeriod = model.form.value!!.periods.first().id
        rule.onNodeWithTag("period-" + firstPeriod + "-delete").performScrollTo().performClick(); rule.waitForIdle()

        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-cascade").assertIsDisplayed()
        rule.onNodeWithText("上课安排仅重新编号", substring = true).assertIsDisplayed()
        assertEquals(0, repository.writes)

        rule.onNodeWithTag("semester-cascade-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("semester-cascade").assertCountEquals(0)
        assertEquals(0, repository.writes)
        assertEquals(listOf(1, 2), model.form.value!!.periods.map { it.number })
        assertEquals(listOf(course), model.state.value.data.courses)

        rule.onNodeWithTag("semester-save").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-cascade").assertIsDisplayed()
        rule.onNodeWithTag("semester-cascade-confirm").performClick()
        rule.waitUntil(5_000) { repository.writes == 1 }
        rule.onAllNodesWithTag("semester-cascade").assertCountEquals(0)
        assertEquals(listOf(1, 2), model.state.value.data.semester!!.periods.map { it.number })
        assertEquals(1, model.state.value.data.courses.single().schedules.single().let { it.startPeriod })
        assertEquals("SYSTEM // 学期与节次设置已保存", model.semesterSuccess.value)
    }

    @Test fun confirmingTheCascadeFeedsTodayAndWeekScreensWithoutRestart() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"), Period(3, "10:00", "10:45"),
        ))
        val course = Course("course", "高数", "", "#287B74", listOf(CourseSchedule("s", 6, 2, 2, 1, 18, RepeatRule.EVERY, "A101")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }
        rule.onNodeWithTag("today-featured-course-0-0").assertIsDisplayed()

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        val firstPeriod = model.form.value!!.periods.first().id
        rule.onNodeWithTag("period-" + firstPeriod + "-delete").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-cascade-confirm").performClick()
        rule.waitUntil(5_000) { model.state.value.data.semester?.periods?.size == 2 }

        rule.onNodeWithTag("today-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("today-featured-course-0-0").assertIsDisplayed()
        rule.onNodeWithTag("schedule-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("week-item-5:0:0").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("week-period-1-start").assertTextContains("08:55")
        rule.onAllNodesWithTag("week-period-3").assertCountEquals(0)
    }

    @Test fun failedCascadeWriteKeepsTheErrorDialogAndARetryableConfirmation() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40"),
        ))
        val course = Course("course", "高数", "", "#287B74", listOf(CourseSchedule("s", 6, 2, 2, 1, 18, RepeatRule.EVERY, "A101")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        val secondPeriod = model.form.value!!.periods[1].id
        rule.onNodeWithTag("period-" + secondPeriod + "-delete").performScrollTo().performClick(); rule.waitForIdle()
        repository.failWrite = true
        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-cascade-confirm").performClick()
        rule.waitUntil(5_000) { model.state.value.error != null }
        rule.onNodeWithTag("app-error-dialog").assertIsDisplayed()
        rule.onNodeWithTag("semester-cascade").assertIsDisplayed()
        assertEquals(listOf(1, 2), model.state.value.data.semester!!.periods.map { it.number })
        assertEquals(listOf(course), model.state.value.data.courses)

        rule.onNodeWithTag("app-error-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("app-error-dialog").assertCountEquals(0)
        rule.onNodeWithTag("semester-cascade").assertIsDisplayed()
        assertEquals(listOf(1), model.form.value!!.periods.map { it.number })

        repository.failWrite = false
        rule.onNodeWithTag("semester-cascade-confirm").performClick()
        rule.waitUntil(5_000) { repository.writes == 2 }
        rule.onAllNodesWithTag("semester-cascade").assertCountEquals(0)
        assertEquals(listOf(1), model.state.value.data.semester!!.periods.map { it.number })
        assertEquals(emptyList<Course>(), model.state.value.data.courses)
    }

    /**
     * P3-06-R7-R1 evidence and regression: a legal reversed persisted order (9, 4, 20) cannot express a
     * 4-9 arrangement after the first deletion, so both entries show the red error dialog and write nothing.
     */
    @Test fun duplicateScheduleBlocksTheSaveWithOneCenteredDialogAndReturnKeepsTheDraft() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("today-add-course").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-editor-toolbar").assertIsDisplayed()
        rule.onNodeWithTag("course-name").performTextReplacement("重复安排课程")
        rule.onNodeWithTag("course-add-schedule").performScrollTo().performClick(); rule.waitForIdle()
        val duplicate = model.editor.value!!.schedules.last().id
        assertEquals(2, model.editor.value!!.schedules.size)

        rule.onNodeWithTag("course-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("course-save-error").assertIsDisplayed()
        rule.onNodeWithText("无法保存课程", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("该上课安排已存在，请勿重复添加。", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithTag("course-save-error-dismiss").assertCountEquals(1)
        rule.onAllNodesWithTag("course-validation").assertCountEquals(0)
        rule.onAllNodesWithTag("course-conflict-confirm").assertCountEquals(0)
        assertEquals(0, repository.courseWrites)
        assertEquals(emptyList<Course>(), model.state.value.data.courses)

        rule.onNodeWithTag("course-save-error-dismiss").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("course-save-error").assertCountEquals(0)
        assertNotNull(model.editor.value)
        assertEquals("重复安排课程", model.editor.value!!.name)
        assertEquals(2, model.editor.value!!.schedules.size)

        rule.onNodeWithTag("course-remove-schedule-" + duplicate).performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(1, model.editor.value!!.schedules.size)
        rule.onNodeWithTag("course-save-toolbar").performClick()
        rule.waitUntil(5_000) { repository.courseWrites == 1 }
        assertNull(model.editor.value)
        assertEquals(listOf("重复安排课程"), model.state.value.data.courses.map { it.name })
        assertEquals("SYSTEM // 课程添加成功", model.courseSuccess.value)
    }

    @Test fun p3r06R7ReversedPeriodsBlockedEvidence() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(9, "08:00", "08:45"), Period(4, "08:55", "09:40"), Period(20, "10:00", "10:45"),
        ))
        val course = Course("course", "数学", "", "#287B74", listOf(CourseSchedule("span", 6, 4, 9, 1, 18, RepeatRule.EVERY, "A101")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        val evidence = mutableListOf("P3-06-R7-R1 reversed persisted numbers (9, 4, 20) with a 4-9 arrangement")
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        val lastPeriod = model.form.value!!.periods.last().id
        rule.onNodeWithTag("period-" + lastPeriod + "-delete").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(1, 2), model.form.value!!.periods.map { it.number })

        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-error").assertIsDisplayed()
        rule.onNodeWithText("无法按新节次顺序安全重映射", substring = true).assertIsDisplayed()
        rule.onNodeWithText("第4-9节", substring = true).assertIsDisplayed()
        rule.onAllNodesWithTag("semester-cascade").assertCountEquals(0)
        assertEquals(0, repository.writes)
        evidence.add("top entry: " + nodeLine("semester-save-error"))
        saveCascadeScreenshot("p3-06-r7-08-reversed-periods-blocked.png")

        rule.onNodeWithTag("semester-save-error-confirm").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("semester-save-error").assertCountEquals(0)
        assertEquals(listOf(1, 2), model.form.value!!.periods.map { it.number })
        assertEquals(listOf(9, 4, 20), model.state.value.data.semester!!.periods.map { it.number })
        assertEquals(listOf(course), model.state.value.data.courses)

        rule.onNodeWithTag("semester-save").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-error").assertIsDisplayed()
        assertEquals(0, repository.writes)
        rule.onNodeWithTag("semester-save-error-confirm").performClick(); rule.waitForIdle()
        assertEquals(listOf(1, 2), model.form.value!!.periods.map { it.number })
        saveCascadeText("node-verification-reversed-20260919.txt", evidence.joinToString("\n") + "\n")
    }

    @Test fun blockedSemesterInputOpensTheSameErrorDialogFromBothEntries() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-name").performTextReplacement(" ")
        rule.onNodeWithTag("semester-save-toolbar").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-error").assertIsDisplayed()
        rule.onNodeWithText("请填写学期名称").assertIsDisplayed()
        assertEquals(0, repository.writes)
        rule.onAllNodesWithTag("semester-save-error-confirm").assertCountEquals(1)

        rule.onNodeWithTag("semester-save-error-confirm").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("semester-save-error").assertCountEquals(0)
        assertEquals(" ", model.form.value!!.name)
        assertEquals("测试学期", model.state.value.data.semester!!.name)

        rule.onNodeWithTag("semester-save").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-save-error").assertIsDisplayed()
        assertEquals(0, repository.writes)
        rule.onNodeWithTag("semester-save-error-confirm").performClick(); rule.waitForIdle()

        rule.onNodeWithTag("semester-name").performTextReplacement("改名学期")
        rule.onNodeWithTag("semester-save-toolbar").performClick()
        rule.waitUntil(5_000) { repository.writes == 1 }
        rule.onAllNodesWithTag("semester-save-error").assertCountEquals(0)
        assertEquals("改名学期", model.state.value.data.semester!!.name)
    }

    @Test fun aCleanSettingsSaveNeverShowsADialog() {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45")))
        val repository = HostScheduleRepository(ScheduleData(1, semester, emptyList(), "1970-01-01T00:00:00Z"))
        val model = ScheduleViewModel(ScheduleAppState(repository, HostPreferencesRepository()), { LocalDateTime.parse("2026-09-05T09:00") }, uniqueIds())
        rule.setContent { QingKeApp(model) }
        rule.waitUntil(5_000) { model.state.value.loadStatus == LoadStatus.READY }

        rule.onNodeWithTag("settings-tab").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("semester-name").performTextReplacement("改名学期")
        rule.onNodeWithTag("semester-save-toolbar").performClick()
        rule.waitUntil(5_000) { repository.writes == 1 }
        rule.onAllNodesWithTag("semester-save-error").assertCountEquals(0)
        rule.onAllNodesWithTag("semester-cascade").assertCountEquals(0)
        rule.onAllNodesWithTag("app-error-dialog").assertCountEquals(0)
        rule.onNodeWithTag("semester-save-success").assertIsDisplayed()
    }

    /** Raw coordinate touch inside the root node; never a semantic performClick. */
    private fun touchAt(offset: Offset) {
        rule.onRoot().performTouchInput { touchClick(offset) }
    }

    private fun r8EvidenceDirectory(): File = File(
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: rule.activity.cacheDir.absolutePath,
        "p3-04-r8-course-save-error-dialog",
    ).also { check(it.exists() || it.mkdirs()) }

    private fun saveR8Screenshot(name: String) {
        File(r8EvidenceDirectory(), name).outputStream().use { output ->
            check(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun saveR8Text(name: String, content: String) {
        File(r8EvidenceDirectory(), name).writeText(content)
    }

    private fun evidenceDirectory(): File = File(
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: rule.activity.cacheDir.absolutePath,
        "p3-06-r7-semester-cascade",
    ).also { check(it.exists() || it.mkdirs()) }

    private fun nodeLine(tag: String): String {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val texts = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }.orEmpty()
        return "$tag bounds=(l=%.1f,t=%.1f,r=%.1f,b=%.1f) size=%.1fx%.1f texts=[%s]".format(
            bounds.left, bounds.top, bounds.right, bounds.bottom, bounds.width, bounds.height, texts,
        )
    }

    private fun saveCascadeText(name: String, content: String) {
        File(evidenceDirectory(), name).writeText(content)
    }

    /** P3-06-R7 evidence host: same capture path as the earlier rounds, one directory per task. */
    private fun saveCascadeScreenshot(name: String) {
        val directory = evidenceDirectory()
        File(directory, name).outputStream().use { output ->
            check(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
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

    private fun defaultForm(count: Int = 10, expanded: Boolean = false) = SemesterFormState(
        id = "semester", name = "2026 秋季学期", startDate = LocalDate.parse("2026-07-01"), totalWeeks = 18,
        periods = (1..count).map { PeriodFormState("p$it", it, LocalTime.of(8, 0).plusMinutes((it - 1) * 55L), LocalTime.of(8, 45).plusMinutes((it - 1) * 55L)) },
        periodsExpanded = expanded,
    )
    private fun loading() = ScheduleState(loadStatus = LoadStatus.LOADING)
    private fun failed(message: String) = ScheduleState(loadStatus = LoadStatus.FAILED, error = message)
    private fun onboarding(saving: Boolean = false, error: String? = null) = ScheduleState(loadStatus = LoadStatus.READY, isSaving = saving, error = error)
    private fun assertNodeHasLightInk(tag: String) = assertNodeInk(tag, minimumLuminance = 140, expectedLight = true)

    private fun assertNodeHasDarkInk(tag: String) = assertNodeInk(tag, maximumLuminance = 90, expectedLight = false)

    private fun assertNodeInk(
        tag: String,
        minimumLuminance: Int = 0,
        maximumLuminance: Int = 255,
        expectedLight: Boolean,
    ) {
        rule.onNodeWithTag(tag).performScrollTo()
        val bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        var ink = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val luminance = ((pixel shr 16 and 0xff) * 299 + (pixel shr 8 and 0xff) * 587 + (pixel and 0xff) * 114) / 1000
                if (luminance >= minimumLuminance && luminance <= maximumLuminance) ink++
            }
        }
        val expectation = if (expectedLight) "light" else "dark"
        assertTrue(tag + " must render " + expectation + " ink against its surface, inkPixels=" + ink, ink >= 20)
    }

    private fun uniqueIds(): () -> String {
        var number = 0
        return { "p3r7-" + number++ }
    }

    private fun cascadePlan(): SemesterCascadePlan {
        val removed = RemovedSchedule(0, "course", "高数", "s1", 1, 2, 2, 1, 18, CascadeRemovalReason.DIRECT_REFERENCE)
        return SemesterCascadePlan(
            courses = emptyList(),
            coursesWithPartialRemoval = emptyList(),
            deletedCourses = listOf(CourseCascade(0, "course", "高数", 0, listOf(removed))),
            remappedSchedules = emptyList(),
            removedSchedules = listOf(removed),
        )
    }

    private fun existingForm() = SemesterFormState(
        id = "term", name = "测试学期", startDate = LocalDate.parse("2026-09-01"), totalWeeks = 18,
        periods = listOf(
            PeriodFormState("q1", 2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
            PeriodFormState("q2", 1, LocalTime.of(10, 0), LocalTime.of(10, 45)),
        ),
        periodsExpanded = true,
    )

    private fun settingsState() = ScheduleState(
        data = ScheduleData(1, Semester("term", "测试学期", "2026-09-01", 18, listOf(Period(2, "08:55", "09:40"), Period(1, "10:00", "10:45"))), emptyList(), "1970-01-01T00:00:00Z"),
        preferences = SchedulePreferences.defaults,
        loadStatus = LoadStatus.READY,
    )

    private fun updatedPeriodState(): ScheduleState {
        val semester = Semester("term", "测试学期", "2026-08-31", 18, listOf(Period(1, "07:30", "08:15")))
        val course = Course("course", "早课", "老师", "#287B74", listOf(CourseSchedule("s", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")))
        return ScheduleState(
            data = ScheduleData(1, semester, listOf(course), "1970-01-01T00:00:00Z"),
            preferences = SchedulePreferences.defaults,
            loadStatus = LoadStatus.READY,
        )
    }

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


    private fun assertWeekControlTextInsidePanel(scale: Float) {
        val panel = rule.onNodeWithTag("week-controls").getUnclippedBoundsInRoot()
        listOf(
            Triple("week-semester-name", 14.dp, 11.dp),
            Triple("week-teaching-week", 21.dp, 17.dp),
            Triple("week-parity", 12.dp, 9.dp),
        ).forEach { (tag, lineHeight, minimumHeight) ->
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val textHeight = bounds.bottom - bounds.top
            assertTrue(
                "fontScale=" + scale + " " + tag + " must keep its full line box: height=" + textHeight + " lineHeight=" + (lineHeight * scale),
                textHeight >= minimumHeight * scale,
            )
            assertTrue(
                "fontScale=" + scale + " " + tag + " must stay inside the week controls panel: text=" + bounds + " panel=" + panel,
                bounds.top >= panel.top - 0.5.dp && bounds.bottom <= panel.bottom + 0.5.dp &&
                    bounds.left >= panel.left - 0.5.dp && bounds.right <= panel.right + 0.5.dp,
            )
        }
        val title = rule.onNodeWithTag("week-title").getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=" + scale + " week controls panel must keep the iOS group gap below the page title: gap=" + (panel.top - title.bottom),
            panel.top - title.bottom >= 12.dp,
        )
        val strip = rule.onNodeWithTag("week-date-strip").getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=" + scale + " week controls panel must keep the iOS group gap above the date strip: gap=" + (strip.top - panel.bottom),
            strip.top - panel.bottom >= 12.dp,
        )
        if (scale > 1f) {
            val panelHeight = panel.bottom - panel.top
            assertTrue("fontScale=" + scale + " panel must grow past the 64dp minimum, height=" + panelHeight, panelHeight > 66.dp)
        }
    }

    private fun assertParityTextPainted(scale: Float) {
        val bitmap = rule.onNodeWithTag("week-controls").captureToImage().asAndroidBitmap()
        val edge = (44f * rule.density.density).toInt()
        var cyan = 0
        for (y in 0 until bitmap.height) {
            for (x in edge until (bitmap.width - edge)) {
                val pixel = bitmap.getPixel(x, y)
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                if (red <= 120 && green >= 140 && blue >= 170) cyan++
            }
        }
        assertTrue("fontScale=" + scale + " ODD/EVEN WEEK text must be painted inside the panel, cyanPixels=" + cyan, cyan >= 20)
    }

    private fun weekState(withLunchBreak: Boolean): ScheduleState {
        val semester = Semester("semester", "测试学期", "2026-08-31", 18, listOf(
            Period(1, "08:00", "08:45"), Period(2, "09:00", "09:45"),
            Period(3, "10:00", "10:45"), Period(4, "14:00", "14:45"),
        ))
        fun schedule(period: Int) = CourseSchedule("s" + period, 1, period, period, 1, 18, RepeatRule.EVERY, "")
        val courses = listOf(
            Course("a", "第一门", "老师", "#287B74", listOf(schedule(1))),
            Course("b", "第二门", "", "#287B74", listOf(schedule(2))),
            Course("c", "第三门", "", "#287B74", listOf(schedule(3))),
            Course("d", "第四门", "", "#287B74", listOf(schedule(4))),
        )
        return ScheduleState(
            data = ScheduleData(1, semester, courses, "1970-01-01T00:00:00Z"),
            preferences = SchedulePreferences.defaults.copy(
                academicCalendar = AcademicCalendarPreferences(lunchBreak = LunchBreakSettings(isEnabled = withLunchBreak)),
            ),
            loadStatus = LoadStatus.READY,
        )
    }

    private fun signalUnderlineCenterX(tag: String): Float {
        val bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        var total = 0L
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                if (red >= 220 && green >= 180 && blue <= 120) {
                    total += x
                    count++
                }
            }
        }
        val stripMiddle = medianLuminance(bitmap, bitmap.height / 2, horizontal = true)
        val topRule = medianLuminance(bitmap, 1, horizontal = true)
        val bottomRule = medianLuminance(bitmap, bitmap.height - 2, horizontal = true)
        assertTrue("day strip must draw a top rule distinguishable from the surface, top=" + topRule + " middle=" + stripMiddle, kotlin.math.abs(topRule - stripMiddle) >= 12)
        assertTrue("day strip must draw a bottom rule distinguishable from the surface, bottom=" + bottomRule + " middle=" + stripMiddle, kotlin.math.abs(bottomRule - stripMiddle) >= 12)
        assertTrue("week date strip must render a signal-yellow selection underline in $tag, found $count px", count >= 20)
        return total.toFloat() / count
    }

    private fun assertWeekMatrixGridLines() {
        val bitmap = rule.onNodeWithTag("week-matrix-canvas").captureToImage().asAndroidBitmap()
        val density = rule.density.density
        val timeColumn = 44f * density
        val headerHeight = 38f * density
        val rowHeight = 68f * density
        val columnWidth = (bitmap.width - timeColumn) / 7f
        assertTrue("week matrix canvas must be fully captured, height=" + bitmap.height, bitmap.height >= (headerHeight + rowHeight * 4).toInt() - 4)
        val sampleRowY = (headerHeight + rowHeight * 1.5f).toInt().coerceIn(0, bitmap.height - 1)
        val rowBaseline = medianLuminance(bitmap, sampleRowY, horizontal = true)
        val lineColumns = (0..7).count { column ->
            val x = (timeColumn + columnWidth * column).toInt().coerceIn(0, bitmap.width - 1)
            deviationNear(bitmap, x, sampleRowY, rowBaseline, horizontal = true) >= 12
        }
        assertTrue("week matrix must draw vertical column lines, lineColumns=$lineColumns", lineColumns >= 6)
        val sampleColumnX = (timeColumn + columnWidth * 3.5f).toInt().coerceIn(0, bitmap.width - 1)
        val columnBaseline = medianLuminance(bitmap, sampleColumnX, horizontal = false)
        val lineRows = (0..4).count { row ->
            val y = (headerHeight + rowHeight * row).toInt().coerceIn(0, bitmap.height - 1)
            deviationNear(bitmap, sampleColumnX, y, columnBaseline, horizontal = false) >= 12
        }
        assertTrue("week matrix must draw a horizontal line before each period, lineRows=$lineRows", lineRows >= 4)
    }

    private fun assertLunchBreakCyanStrip() {
        val bitmap = rule.onNodeWithTag("week-lunch-break").captureToImage().asAndroidBitmap()
        var cyan = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                if (red <= 120 && green >= 120 && blue >= 150) cyan++
            }
        }
        assertTrue("lunch break must be a cyan strip, cyanPixels=$cyan", cyan >= bitmap.width * bitmap.height / 4)
    }

    private fun medianLuminance(bitmap: Bitmap, fixed: Int, horizontal: Boolean): Int {
        val values = if (horizontal) {
            (0 until bitmap.width step 2).map { luminance(bitmap, it, fixed) }
        } else {
            (0 until bitmap.height step 2).map { luminance(bitmap, fixed, it) }
        }
        return values.sorted()[values.size / 2]
    }

    /** Theme agnostic: a divider may be darker (light theme) or lighter (dark theme) than its surface. */
    private fun deviationNear(bitmap: Bitmap, x: Int, y: Int, baseline: Int, horizontal: Boolean): Int = (-3..3).maxOf { delta ->
        val sample = if (horizontal) luminance(bitmap, (x + delta).coerceIn(0, bitmap.width - 1), y)
        else luminance(bitmap, x, (y + delta).coerceIn(0, bitmap.height - 1))
        kotlin.math.abs(sample - baseline)
    }

    private fun luminance(bitmap: Bitmap, x: Int, y: Int): Int {
        val pixel = bitmap.getPixel(x, y)
        return ((pixel shr 16 and 0xff) * 299 + (pixel shr 8 and 0xff) * 587 + (pixel and 0xff) * 114) / 1000
    }
}
