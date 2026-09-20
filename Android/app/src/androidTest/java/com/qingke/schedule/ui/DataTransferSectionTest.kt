package com.qingke.schedule.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleState
import com.qingke.schedule.transfer.ScheduleDataTransfer
import com.qingke.schedule.transfer.ScheduleImportPreview
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.PeriodFormState
import com.qingke.schedule.viewmodel.SemesterFormState
import com.qingke.schedule.viewmodel.TransferUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P4／A10: the "05 数据备份" section on both the first-boot and the settings forms. These tests own the copy,
 * the destructive confirmation, the failure dialogs, the export gate and the theme／density behaviour; the real
 * system file panels and the transactional replace are covered by the device transfer tests.
 */
@RunWith(AndroidJUnit4::class)
class DataTransferSectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSettingsSectionFollowsTheReminderSectionAndOffersBothEntries() {
        var imports = 0
        var exports = 0
        setContent(TransferUiState(), QingKeAppActions(requestImport = { imports++ }, requestExport = { exports++ }))

        val reminders = rule.onNodeWithTag("settings-reminders-section").performScrollTo().getUnclippedBoundsInRoot()
        val transfer = rule.onNodeWithTag("settings-transfer-section").performScrollTo().getUnclippedBoundsInRoot()
        assertTrue("the transfer section must follow 04 上课提醒", transfer.top >= reminders.top)

        rule.onNodeWithTag("settings-transfer-format").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-transfer-export").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-transfer-import").performScrollTo().performClick()
        rule.onNodeWithTag("settings-transfer-export").performScrollTo().performClick()

        assertEquals(1, imports)
        assertEquals(1, exports)
    }

    @Test
    fun theOnboardingSectionUsesTheSameCopyAndExplainsTheBlockedExport() {
        var imports = 0
        setContent(TransferUiState(), QingKeAppActions(requestImport = { imports++ }), onboarding = true)

        rule.onNodeWithTag("onboarding-transfer-section").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-transfer-format").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-transfer-export-disabled").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(ScheduleDataTransfer.NO_SEMESTER_EXPORT_HINT).assertIsDisplayed()
        rule.onAllNodesWithTag("onboarding-transfer-export").assertCountEquals(0)
        rule.onNodeWithText("请选择扩展名为 .json 的青课课表备份；暂不支持 Excel（.xlsx / .xls）文件。").assertIsDisplayed()

        rule.onNodeWithTag("onboarding-transfer-import").performScrollTo().performClick()
        assertEquals(1, imports)
    }

    @Test
    fun aValidPreviewAsksBeforeReplacingAndRoutesBothButtons() {
        var confirms = 0
        var dismissals = 0
        val transfer = TransferUiState(preview = preview())
        setContent(transfer, QingKeAppActions(confirmImport = { confirms++ }, dismissTransferPrompt = { dismissals++ }))

        rule.onNodeWithTag("transfer-import-preview").assertIsDisplayed()
        rule.onNodeWithText("替换当前课表？").assertIsDisplayed()
        rule.onNodeWithText("学期：2026 秋季学期", substring = true).assertIsDisplayed()
        rule.onNodeWithText("课程：2 门", substring = true).assertIsDisplayed()
        rule.onNodeWithText("更新时间：2026-09-02T12:00:00.000Z", substring = true).assertIsDisplayed()
        rule.onNodeWithText("将整体替换当前课表", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("transfer-import-preview-confirm").performClick()
        assertEquals(1, confirms)
        assertEquals(0, dismissals)

        rule.onNodeWithTag("transfer-import-preview-dismiss").performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun aWritingPreviewDisablesTheConfirmActionUntilTheTransactionReturns() {
        var confirms = 0
        setContent(TransferUiState(preview = preview(), isWriting = true), QingKeAppActions(confirmImport = { confirms++ }))

        rule.onNodeWithText("正在导入…").assertIsDisplayed()
        rule.onNodeWithTag("transfer-import-preview-confirm").performClick()
        assertEquals(0, confirms)
    }

    @Test
    fun anUnknownVersionAndAnOversizedFileReportClearChineseErrors() {
        val dismissals = mutableListOf<Unit>()
        val state = mutableStateOf(TransferUiState(importFailure = "不支持的课表数据版本：2"))
        setContent(state, QingKeAppActions(dismissTransferPrompt = { dismissals += Unit }))

        rule.onNodeWithText("无法导入课表").assertIsDisplayed()
        rule.onNodeWithText("不支持的课表数据版本：2").assertIsDisplayed()
        rule.onNodeWithTag("transfer-import-error-confirm").performClick()
        assertEquals(1, dismissals.size)

        state.value = TransferUiState(importFailure = "课表文件超过 5 MiB 输入上限")
        rule.waitForIdle()
        rule.onNodeWithText("课表文件超过 5 MiB 输入上限").assertIsDisplayed()
    }

    @Test
    fun aFailedWriteKeepsThePreviewRetryableAndCancelsBackToTheSchedule() {
        var confirms = 0
        var dismissals = 0
        setContent(
            TransferUiState(preview = preview(), writeFailure = "课表存储损坏：读取失败"),
            QingKeAppActions(confirmImport = { confirms++ }, dismissTransferPrompt = { dismissals++ }),
        )

        rule.onNodeWithText("导入失败").assertIsDisplayed()
        rule.onNodeWithText("课表存储损坏：读取失败", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("transfer-import-retry-confirm").performClick()
        assertEquals(1, confirms)

        rule.onNodeWithTag("transfer-import-retry-dismiss").performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun aSuccessfulTransferAndAFailedExportUseTheSectionStatusAndTheSameDialogs() {
        val dismissals = mutableListOf<Unit>()
        val state = mutableStateOf(TransferUiState(statusMessage = "已导入 6 门课程"))
        setContent(state, QingKeAppActions(dismissTransferPrompt = { dismissals += Unit }))

        rule.onNodeWithTag("settings-transfer-success").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-transfer-success-message").performScrollTo().assertTextEquals("已导入 6 门课程")
        rule.onAllNodesWithTag("transfer-import-preview").assertCountEquals(0)

        state.value = TransferUiState(statusMessage = ScheduleDataTransfer.EXPORT_SUCCESS_MESSAGE)
        rule.waitForIdle()
        rule.onNodeWithTag("settings-transfer-success-message").performScrollTo()
            .assertTextEquals("已导出备份文件")

        state.value = TransferUiState(exportFailure = "无法写入所选文件：写入中断")
        rule.waitForIdle()
        rule.onNodeWithText("无法导出备份").assertIsDisplayed()
        rule.onNodeWithText("无法写入所选文件：写入中断").assertIsDisplayed()
        rule.onNodeWithTag("transfer-export-error-confirm").performClick()
        assertEquals(1, dismissals.size)
    }

    @Test
    fun theSectionSurvivesANarrowScreenAndALargeFontInLightMode() =
        assertNarrowAndLargeFontSection(AppearanceMode.LIGHT)

    @Test
    fun theSectionSurvivesANarrowScreenAndALargeFontInDarkMode() =
        assertNarrowAndLargeFontSection(AppearanceMode.DARK)

    private fun assertNarrowAndLargeFontSection(appearance: AppearanceMode) {
        var imports = 0
        setContent(
            TransferUiState(),
            QingKeAppActions(requestImport = { imports++ }),
            appearance = appearance,
            fontScale = 1.3f,
            narrow = true,
        )
        rule.onNodeWithTag("settings-transfer-format").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-transfer-import").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-transfer-import").performScrollTo().performClick()
        rule.onNodeWithTag("settings-transfer-export").performScrollTo().assertIsDisplayed()
        assertEquals(1, imports)
    }

    @Test
    fun theRowsKeepTouchTargetsAndAccessibleDescriptions() {
        setContent(
            TransferUiState(statusMessage = "已导入 6 门课程"),
            QingKeAppActions(),
        )

        rule.onNodeWithTag("settings-transfer-import").performScrollTo()
        rule.onNodeWithTag("settings-transfer-import").assert(hasContentDescription("从 JSON 文件导入课表"))
        rule.onNodeWithTag("settings-transfer-export").assert(hasContentDescription("导出课表备份"))
        rule.onNodeWithTag("settings-transfer-success").assert(hasContentDescription("已导入 6 门课程"))

        listOf("settings-transfer-import", "settings-transfer-export", "settings-transfer-success").forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag must keep a 48dp touch target", bounds.height >= 48.dp)
        }
    }

    private fun setContent(
        initial: TransferUiState,
        actions: QingKeAppActions,
        onboarding: Boolean = false,
        appearance: AppearanceMode = AppearanceMode.SYSTEM,
        fontScale: Float = 1f,
        narrow: Boolean = false,
    ) {
        val state = mutableStateOf(initial)
        setContent(state, actions, onboarding, appearance, fontScale, narrow)
    }

    private fun setContent(
        state: MutableState<TransferUiState>,
        actions: QingKeAppActions,
        onboarding: Boolean = false,
        appearance: AppearanceMode = AppearanceMode.SYSTEM,
        fontScale: Float = 1f,
        narrow: Boolean = false,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(rule.density.density, fontScale)) {
                val content: @Composable () -> Unit = {
                    QingKeAppContent(
                        if (onboarding) onboardingState(appearance) else settingsState(appearance),
                        if (onboarding) onboardingForm() else settingsForm(),
                        if (onboarding) MainTab.TODAY else MainTab.SETTINGS,
                        actions,
                        transfer = state.value,
                    )
                }
                if (narrow) Box(Modifier.width(320.dp)) { content() } else content()
            }
        }
    }

    private fun preview() = ScheduleImportPreview.of(importedData())

    private fun importedData() = ScheduleData(
        1,
        Semester(
            "semester-imported",
            "2026 秋季学期",
            "2026-09-02",
            18,
            listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")),
        ),
        listOf(
            course("duplicate", "第一门", "#287B74"),
            course("duplicate", "第二门", "#E65A4F"),
        ),
        "2026-09-02T12:00:00.000Z",
    )

    private fun course(id: String, name: String, color: String) = Course(
        id,
        name,
        "教师",
        color,
        listOf(CourseSchedule("shared", 1, 1, 1, 1, 18, RepeatRule.EVERY, "A101")),
    )

    private fun settingsState(appearance: AppearanceMode) = ScheduleState(
        data = ScheduleData(1, semester(), emptyList(), "1970-01-01T00:00:00Z"),
        preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance),
        loadStatus = LoadStatus.READY,
    )

    private fun onboardingState(appearance: AppearanceMode) = ScheduleState(
        data = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00Z"),
        preferences = SchedulePreferences.defaults.copy(appearanceMode = appearance),
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
