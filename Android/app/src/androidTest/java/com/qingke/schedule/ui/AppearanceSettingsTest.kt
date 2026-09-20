package com.qingke.schedule.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleState
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.PeriodFormState
import com.qingke.schedule.viewmodel.SemesterFormState
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P3-09／A11: the "06 外观 / DISPLAY" section on the terminal settings page. These tests own the copy, the
 * selection semantics, the live theme换 and the responsive layout; the production APK run covers the fixed-data
 * cross-page check.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSettingsPageOrdersRemindersTransferAppearanceAndTheSaveCard() {
        setContent(mutableStateOf(settingsState(AppearanceMode.LIGHT)))
        rule.onNodeWithTag("semester-save").performScrollTo()

        val reminders = rule.onNodeWithTag("settings-reminders-section").getUnclippedBoundsInRoot()
        val transfer = rule.onNodeWithTag("settings-transfer-section").getUnclippedBoundsInRoot()
        val appearance = rule.onNodeWithTag("settings-appearance-section").getUnclippedBoundsInRoot()
        val saveCard = rule.onNodeWithTag("semester-save").getUnclippedBoundsInRoot()

        assertTrue("04 上课提醒 must stay above 05 数据备份", reminders.top < transfer.top)
        assertTrue("05 数据备份 must stay above 06 外观", transfer.top < appearance.top)
        assertTrue("06 外观 must sit just before the save card", appearance.top < saveCard.top)
        rule.onNodeWithTag("settings-appearance-panel").assertIsDisplayed()
        assertTrue("06 must keep a 48dp touch target for every option", appearance.height.value > 0f)
    }

    @Test
    fun theOnboardingPageHasNoAppearanceEntryButStillAppliesTheSavedDarkMode() {
        setContent(mutableStateOf(onboardingState(AppearanceMode.DARK)), form = onboardingForm())

        rule.onAllNodesWithTag("onboarding-appearance-section").assertCountEquals(0)
        rule.onAllNodesWithTag("onboarding-appearance-panel").assertCountEquals(0)
        rule.onNodeWithTag("onboarding-screen").assertIsDisplayed()
        assertTrue(
            "the first-boot page must still consume the stored dark mode",
            meanLuminance() < 0.35f,
        )
    }

    @Test
    fun theThreeOptionsExposeIosCopySelectionAndRadioSemantics() {
        val selections = mutableListOf<AppearanceMode>()
        setContent(mutableStateOf(settingsState(AppearanceMode.LIGHT)), QingKeAppActions(setAppearanceMode = { selections += it }))
        rule.onNodeWithTag("settings-appearance-light").performScrollTo()

        listOf("AUTO" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEachIndexed { index, (code, title) ->
            val tag = "settings-appearance-" + appearanceModeAt(index).storageValue
            rule.onNodeWithTag(tag).assert(hasStateDescription(if (index == 1) "已选择" else "未选择"))
            rule.onNodeWithTag(tag).assertContentDescriptionEquals(code + " " + title)
            rule.onNodeWithTag(tag).assertTextContains(code)
            rule.onNodeWithTag(tag).assertTextContains(title)
            rule.onNodeWithTag(tag + "-code", useUnmergedTree = true).assertExists()
            rule.onNodeWithTag(tag + "-title", useUnmergedTree = true).assertExists()
            if (index == 1) rule.onNodeWithTag(tag).assertIsSelected() else rule.onNodeWithTag(tag).assertIsNotSelected()
        }
        rule.onNodeWithTag("settings-appearance-light").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-appearance-light").assertTextContains("浅色")
        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：浅色")
        rule.onNodeWithTag("settings-appearance-effective-value").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("settings-appearance-dark").performScrollTo().performClick()
        assertEquals(listOf(AppearanceMode.DARK), selections)
    }

    @Test
    fun forcedLightAndDarkOverrideTheSystemNightModeAndUpdateTheEffectiveLine() {
        val night = mutableStateOf(true)
        val state = mutableStateOf(settingsState(AppearanceMode.SYSTEM))
        setContent(state, night = night)

        rule.onNodeWithTag("settings-appearance-effective").performScrollTo()
            .assertContentDescriptionEquals("当前显示：深色")

        state.value = settingsState(AppearanceMode.LIGHT)
        rule.waitForIdle()
        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：浅色")
        rule.onNodeWithTag("settings-appearance-light").assertIsSelected()
        assertTrue("forced light must ignore the dark system", meanLuminance() > 0.6f)

        state.value = settingsState(AppearanceMode.DARK)
        rule.waitForIdle()
        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：深色")
        rule.onNodeWithTag("settings-appearance-dark").assertIsSelected()
        night.value = false
        rule.waitForIdle()
        assertTrue("forced dark must ignore the light system", meanLuminance() < 0.4f)
        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：深色")
    }

    @Test
    fun systemFollowsTheRealConfigurationAndUpdatesWhenItChanges() {
        val night = mutableStateOf(false)
        setContent(mutableStateOf(settingsState(AppearanceMode.SYSTEM)), night = night)

        rule.onNodeWithTag("settings-appearance-system").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：浅色")
        val light = meanLuminance()

        night.value = true
        rule.waitForIdle()

        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：深色")
        rule.onNodeWithTag("settings-appearance-system").assertIsSelected()
        val dark = meanLuminance()
        assertTrue("the system night switch must re-resolve the theme, light=" + light + " dark=" + dark, dark < light - 0.3f)
    }

    @Test
    fun writingDisablesTheOptionsAndAFailedWriteKeepsTheCommittedThemeAndShowsTheError() {
        val selections = mutableListOf<AppearanceMode>()
        val state = mutableStateOf(settingsState(AppearanceMode.LIGHT, isSaving = true))
        setContent(state, QingKeAppActions(setAppearanceMode = { selections += it }))
        rule.onNodeWithTag("settings-appearance-dark").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("settings-appearance-light").assertIsNotEnabled()
        rule.onNodeWithTag("settings-appearance-system").assertIsNotEnabled()
        rule.onNodeWithTag("settings-appearance-dark").performClick()
        rule.onNodeWithTag("settings-appearance-dark").performClick()
        assertEquals("a write in flight must not queue another selection", emptyList<AppearanceMode>(), selections)
        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：浅色")

        state.value = settingsState(AppearanceMode.LIGHT, error = "appearance write failed")
        rule.waitForIdle()
        rule.onNodeWithTag("app-error-dialog").assertIsDisplayed()
        rule.onNodeWithText("appearance write failed").assertIsDisplayed()
        rule.onNodeWithTag("settings-appearance-light").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("settings-appearance-effective").assertContentDescriptionEquals("当前显示：浅色")
    }

    @Test
    fun theOptionsStackOnANarrowScreenAtLargeFontsWithFullTouchTargets() {
        setContent(mutableStateOf(settingsState(AppearanceMode.SYSTEM)), fontScale = 1.3f, widthDp = 320)
        assertStackedLayout()
    }

    @Test
    fun theOptionsStackOnANarrowScreenWithTheDefaultFont() {
        setContent(mutableStateOf(settingsState(AppearanceMode.SYSTEM)), widthDp = 320)
        assertStackedLayout()
    }

    @Test
    fun theOptionsStackAtTheAccessibilityFontScale() {
        setContent(mutableStateOf(settingsState(AppearanceMode.SYSTEM)), fontScale = 2f)
        assertStackedLayout()
    }

    @Test
    fun theOptionsShareOneRowOnANormalWidthWithTheDefaultFont() {
        setContent(mutableStateOf(settingsState(AppearanceMode.SYSTEM)))
        scrollToAppearancePanelBottom()
        val system = rule.onNodeWithTag("settings-appearance-system").getUnclippedBoundsInRoot()
        val light = rule.onNodeWithTag("settings-appearance-light").getUnclippedBoundsInRoot()
        val dark = rule.onNodeWithTag("settings-appearance-dark").getUnclippedBoundsInRoot()

        assertTrue("the three options must share one row", abs((system.top - light.top).value) < 1f && abs((light.top - dark.top).value) < 1f)
        assertTrue(system.left < light.left && light.left < dark.left)
        listOf(system, light, dark).forEach { assertTrue("each option needs 48dp", it.height >= 48.dp) }
    }

    @Test
    fun theSelectedOptionAndEffectiveLineStayReadableInBothForcedThemes() {
        setContent(mutableStateOf(settingsState(AppearanceMode.LIGHT)))
        rule.onNodeWithTag("settings-appearance-light").performScrollTo()
        assertTrue("the selected option uses the inverse surface with light ink", hasLightInk("settings-appearance-light"))
        assertTrue("the unselected option keeps dark ink on the light panel", hasDarkInk("settings-appearance-system"))
        rule.onNodeWithTag("settings-appearance-effective-value").performScrollTo().assertIsDisplayed()
        assertTrue("the effective line must be readable in light mode", hasDarkInk("settings-appearance-effective"))

        rule.onNodeWithTag("settings-appearance-dark").performScrollTo().performClick()
    }

    @Test
    fun theForcedLightBrandLogoStaysInkInkOnALightSurfaceWhileTheSystemIsDark() {
        setContent(mutableStateOf(settingsState(AppearanceMode.LIGHT)), night = mutableStateOf(true))
        rule.onNodeWithTag("settings-brand-header").assertIsDisplayed()
        assertTrue(
            "the light brand logo must not follow the dark system qualifier",
            hasDarkInk("today-brand-logo"),
        )
    }

    @Test
    fun theForcedDarkBrandLogoStaysBrightWhileTheSystemIsLight() {
        setContent(mutableStateOf(settingsState(AppearanceMode.DARK)), night = mutableStateOf(false))
        rule.onNodeWithTag("settings-brand-header").assertIsDisplayed()
        assertTrue("the dark brand logo must stay bright", hasLightInk("today-brand-logo"))
    }

    private fun assertStackedLayout() {
        scrollToAppearancePanelBottom()
        val system = rule.onNodeWithTag("settings-appearance-system").getUnclippedBoundsInRoot()
        val light = rule.onNodeWithTag("settings-appearance-light").getUnclippedBoundsInRoot()
        val dark = rule.onNodeWithTag("settings-appearance-dark").getUnclippedBoundsInRoot()

        assertTrue("stacked options must share the left edge", abs((system.left - light.left).value) < 1f && abs((light.left - dark.left).value) < 1f)
        assertTrue("stacked options must not overlap", system.bottom <= light.top && light.bottom <= dark.top)
        listOf(system, light, dark).forEach { assertTrue("each stacked option needs 48dp", it.height >= 48.dp) }
        rule.onNodeWithTag("settings-appearance-system").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-appearance-dark").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-appearance-effective").performScrollTo().assertIsDisplayed()
    }

    /** One fixed scroll offset, with the lowest element of the section visible. */
    private fun scrollToAppearancePanelBottom() {
        rule.onNodeWithTag("settings-appearance-effective").performScrollTo()
    }

    private fun appearanceModeAt(index: Int) =
        listOf(AppearanceMode.SYSTEM, AppearanceMode.LIGHT, AppearanceMode.DARK)[index]

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun setContent(
        state: MutableState<ScheduleState>,
        actions: QingKeAppActions = QingKeAppActions(),
        night: MutableState<Boolean> = mutableStateOf(false),
        fontScale: Float = 1f,
        widthDp: Int? = null,
        form: SemesterFormState? = settingsForm(),
    ) {
        rule.setContent {
            val isNight = night.value
            CompositionLocalProvider(
                LocalConfiguration provides configuration(isNight),
                LocalDensity provides Density(rule.density.density, fontScale),
            ) {
                val content: @Composable () -> Unit = {
                    QingKeAppContent(state.value, form, MainTab.SETTINGS, actions)
                }
                if (widthDp != null) Box(Modifier.width(widthDp.dp)) { content() } else content()
            }
        }
    }

    private fun configuration(night: Boolean): Configuration =
        Configuration(rule.activity.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }

    /** Theme probe: the whole window is always visible, so a tall panel can never be captured half-clipped. */
    private fun meanLuminance(): Float {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        var sum = 0.0
        var count = 0
        for (x in 0 until bitmap.width step 8) {
            for (y in 0 until bitmap.height step 8) {
                sum += luminanceOf(bitmap.getPixel(x, y)) / 255.0
                count++
            }
        }
        return (sum / count).toFloat()
    }

    private fun hasDarkInk(tag: String): Boolean = inkCount(tag) { it < 60 } >= 20

    private fun hasLightInk(tag: String): Boolean = inkCount(tag) { it > 190 } >= 20

    /** Transparent logo pixels must never be mistaken for ink, so only opaque pixels count. */
    private fun inkCount(tag: String, predicate: (Int) -> Boolean): Int {
        val bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        var ink = 0
        for (x in 0 until bitmap.width step 2) {
            for (y in 0 until bitmap.height step 2) {
                val pixel = bitmap.getPixel(x, y)
                if ((pixel ushr 24 and 0xff) > 200 && predicate(luminanceOf(pixel))) ink++
            }
        }
        return ink
    }

    private fun luminanceOf(pixel: Int): Int =
        ((pixel shr 16 and 0xff) * 299 + (pixel shr 8 and 0xff) * 587 + (pixel and 0xff) * 114) / 1000

    private fun settingsState(
        appearanceMode: AppearanceMode,
        isSaving: Boolean = false,
        error: String? = null,
    ) = ScheduleState(
        data = ScheduleData(1, semester(), emptyList(), "1970-01-01T00:00:00Z"),
        preferences = SchedulePreferences.defaults.copy(appearanceMode = appearanceMode),
        loadStatus = LoadStatus.READY,
        isSaving = isSaving,
        error = error,
    )

    private fun onboardingState(appearanceMode: AppearanceMode) = ScheduleState(
        data = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"),
        preferences = SchedulePreferences.defaults.copy(appearanceMode = appearanceMode),
        loadStatus = LoadStatus.READY,
    )

    private fun semester() = Semester("semester", "测试学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45")))

    private fun settingsForm() = SemesterFormState(
        id = "semester",
        name = "测试学期",
        startDate = LocalDate.parse("2026-08-31"),
        totalWeeks = 18,
        periods = listOf(PeriodFormState("p1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45))),
        periodsExpanded = false,
    )

    private fun onboardingForm() = SemesterFormState(
        id = "onboarding",
        name = "2026 秋季学期",
        startDate = LocalDate.parse("2026-08-31"),
        totalWeeks = 18,
        periods = listOf(PeriodFormState("p1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45))),
        periodsExpanded = false,
    )
}
