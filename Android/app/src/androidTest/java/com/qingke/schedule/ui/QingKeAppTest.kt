package com.qingke.schedule.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.content.res.Configuration
import android.util.Xml
import android.view.View
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.test.click as touchClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.sp
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

    @Test fun dangerFooterFollowsDeletePanelAndValidationUsesAcrylicDangerCard() {
        val schedule = CourseScheduleFormState("danger", 1, 1, 1, 1, 18, RepeatRule.EVERY, "")
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var scale by mutableStateOf(1f)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(rule.density.density, scale)) { QingKeAppContent(readyToday().copy(preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance)), null, MainTab.TODAY, QingKeAppActions(), editor = CourseEditorState(CourseEditorMode.EDIT, schedules = listOf(schedule), validationMessage = "请填写课程名称")) } }
        listOf(AppearanceMode.LIGHT, AppearanceMode.DARK).forEach { mode ->
            appearance = mode; scale = 1.3f; rule.waitForIdle()
            rule.onNodeWithTag("course-validation").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("course-validation-icon", useUnmergedTree = true).assertIsDisplayed()
            val validation = rule.onNodeWithTag("course-validation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("validation height=${validation.height}", validation.height >= 52f)
            assertDangerCardHasCoralIconAndRail()
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
        rule.onNodeWithTag("schedule-tab").performClick(); rule.onNodeWithTag("week-schedule").assertIsDisplayed()
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

    @Test fun systemTimeDialogsConfirmNewValuesAndCancelLeavesExistingValues() {
        var form by mutableStateOf(defaultForm(expanded = true))
        var startUpdates = 0; var endUpdates = 0
        rule.setContent { QingKeAppContent(onboarding(), form, MainTab.TODAY, QingKeAppActions(
            updatePeriodStart = { id, value -> startUpdates++; form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(start = value) else it }) },
            updatePeriodEnd = { id, value -> endUpdates++; form = form.copy(periods = form.periods.map { if (it.id == id) it.copy(end = value) else it }) },
        )) }
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

    private fun assertDangerCardHasCoralIconAndRail() {
        val bitmap = rule.onNodeWithTag("course-validation", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val coralPixels = (0 until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x ->
            val pixel = bitmap.getPixel(x, y); val red = pixel shr 16 and 0xff; val green = pixel shr 8 and 0xff; val blue = pixel and 0xff
            red > 175 && green in 50..125 && blue in 45..125
        } }
        assertTrue("validation coral pixels=$coralPixels", coralPixels >= 20)
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
