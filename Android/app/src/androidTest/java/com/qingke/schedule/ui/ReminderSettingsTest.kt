package com.qingke.schedule.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleState
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.PeriodFormState
import com.qingke.schedule.viewmodel.ReminderUiState
import com.qingke.schedule.viewmodel.SemesterFormState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A08 second batch: the "04 上课提醒" section on the terminal settings page. These tests own the rendering and
 * the callback routing; the real switch - persist - schedule flow lives in [ReminderSettingsFlowTest].
 */
@RunWith(AndroidJUnit4::class)
class ReminderSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun theSectionKeepsTheLeadTimeHiddenWhileRemindersAreOff() {
        val toggles = mutableListOf<Boolean>()
        val state = stateOf(ReminderUiState())
        setContent(state, QingKeAppActions(setRemindersEnabled = { toggles += it }))

        rule.onNodeWithTag("settings-reminders-section").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-reminders-toggle").performScrollTo().assertIsOff()
        rule.onNodeWithTag("settings-reminders-status").performScrollTo().assertTextEquals("提醒已关闭")
        rule.onAllNodesWithTag("settings-reminders-lead-10").assertCountEquals(0)
        rule.onNodeWithTag("settings-reminders-permission-value").performScrollTo().assertTextEquals("未开启")
        rule.onNodeWithTag("settings-reminders-channel-value").performScrollTo().assertTextEquals("未启用")
        rule.onNodeWithTag("settings-reminders-exact-value").performScrollTo().assertTextEquals("不可用")

        rule.onNodeWithTag("settings-reminders-toggle").performScrollTo().performClick()
        assertEquals(listOf(true), toggles)
    }

    @Test fun anEnabledReminderShowsThePresetsAndTheExactAlarmFallback() {
        val state = stateOf(
            reminderState(remindersEnabled = true, notificationsPermitted = true, channelReady = true, activeCount = 2),
        )
        setContent(state)

        rule.onNodeWithTag("settings-reminders-toggle").performScrollTo().assertIsOn()
        listOf(0, 5, 10, 15, 30).forEach { minutes ->
            rule.onNodeWithTag("settings-reminders-lead-" + minutes).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithTag("settings-reminders-lead-10").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("settings-reminders-lead-30").performScrollTo().assertIsNotSelected()
        rule.onNodeWithTag("settings-reminders-inexact-note").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-reminders-open-exact-alarm-settings").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-reminders-exact-value").performScrollTo().assertTextEquals("不可用")
    }

    @Test fun presetChipsRouteTheStoredLeadTime() {
        val written = mutableListOf<Pair<Int, Boolean>>()
        val state = stateOf(
            reminderState(remindersEnabled = true, notificationsPermitted = true, channelReady = true, exactAlarmsAvailable = true),
        )
        setContent(state, QingKeAppActions(setReminderLeadMinutes = { minutes, custom -> written += minutes to custom }))

        rule.onNodeWithTag("settings-reminders-lead-30").performScrollTo().performClick()
        rule.onNodeWithTag("settings-reminders-lead-0").performScrollTo().performClick()
        rule.onNodeWithTag("settings-reminders-lead-custom").performScrollTo().performClick()

        assertEquals(listOf(30 to false, 0 to false, 10 to true), written)
    }

    @Test fun aCustomLeadTimeShowsTheStepperAndRoutesBothDirections() {
        val written = mutableListOf<Pair<Int, Boolean>>()
        val state = stateOf(
            reminderState(
                remindersEnabled = true,
                notificationsPermitted = true,
                channelReady = true,
                exactAlarmsAvailable = true,
                leadMinutes = 45,
                usesCustomLeadTime = true,
            ),
        )
        setContent(state, QingKeAppActions(setReminderLeadMinutes = { minutes, custom -> written += minutes to custom }))

        rule.onNodeWithTag("settings-reminders-lead-custom").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("settings-reminders-lead-custom-stepper").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-reminders-lead-custom-note").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-reminders-lead-custom-plus").performScrollTo().performClick()
        rule.onNodeWithTag("settings-reminders-lead-custom-minus").performScrollTo().performClick()

        assertEquals(listOf(46 to true, 44 to true), written)
    }

    @Test fun aDeniedPermissionOffersTheExplicitActionsOnlyWhileRemindersAreOn() {
        val state = stateOf(reminderState(remindersEnabled = true, notificationsPermitted = false, channelReady = true))
        setContent(state)

        rule.onNodeWithTag("settings-reminders-permission-value").performScrollTo().assertTextEquals("未开启")
        rule.onNodeWithTag("settings-reminders-status").performScrollTo().assertTextEquals("系统通知权限未开启，提醒不会投递")
        rule.onNodeWithTag("settings-reminders-request-permission").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-reminders-open-notification-settings").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag("settings-reminders-channel-settings").assertCountEquals(0)

        state.value = reminderState(remindersEnabled = false, notificationsPermitted = false, channelReady = false)
        rule.waitForIdle()
        rule.onAllNodesWithTag("settings-reminders-request-permission").assertCountEquals(0)
        rule.onAllNodesWithTag("settings-reminders-open-notification-settings").assertCountEquals(0)
    }

    @Test fun aClosedChannelOffersTheSystemNotificationSettingsWithoutThePermissionRequest() {
        val state = stateOf(reminderState(remindersEnabled = true, notificationsPermitted = true, channelReady = false))
        setContent(state)

        rule.onNodeWithTag("settings-reminders-channel-value").performScrollTo().assertTextEquals("已关闭")
        rule.onNodeWithTag("settings-reminders-status").performScrollTo().assertTextEquals("提醒渠道不可用，提醒不会投递")
        rule.onNodeWithTag("settings-reminders-channel-settings").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag("settings-reminders-request-permission").assertCountEquals(0)
    }

    @Test fun scheduledAndDegradedCountsAreReportedInTheStatusLine() {
        val state = stateOf(
            reminderState(
                remindersEnabled = true,
                notificationsPermitted = true,
                channelReady = true,
                exactAlarmsAvailable = true,
                activeCount = 5,
            ),
        )
        setContent(state)
        rule.onNodeWithTag("settings-reminders-status").performScrollTo().assertTextEquals("已安排最近 5 条课程提醒")

        state.value = reminderState(
            remindersEnabled = true,
            notificationsPermitted = true,
            channelReady = true,
            exactAlarmsAvailable = false,
            activeCount = 3,
            degraded = true,
        )
        rule.waitForIdle()
        rule.onNodeWithTag("settings-reminders-status").performScrollTo()
            .assertTextEquals("已安排最近 3 条课程提醒（部分可能延迟）")
        rule.onNodeWithTag("settings-reminders-inexact-note").performScrollTo().assertIsDisplayed()
    }

    @Test fun anEmptyWindowAndAFailedUpdateAreBothExplained() {
        val state = stateOf(
            reminderState(
                remindersEnabled = true,
                notificationsPermitted = true,
                channelReady = true,
                exactAlarmsAvailable = true,
                activeCount = 0,
            ),
        )
        setContent(state)
        rule.onNodeWithTag("settings-reminders-status").performScrollTo().assertTextEquals("当前没有待安排的课程提醒")

        state.value = reminderState(remindersEnabled = true, diagnostic = "平台不可用", lastFailureCount = 2)
        rule.waitForIdle()
        rule.onNodeWithTag("settings-reminders-status").performScrollTo()
            .assertTextEquals("课表已保存，但提醒更新失败：平台不可用")
        rule.onNodeWithTag("settings-reminders-retry").performScrollTo().assertTextEquals("2 条提醒未能安排，将在下次重建时重试。")
    }

    private fun stateOf(initial: ReminderUiState): MutableState<ReminderUiState> = mutableStateOf(initial)

    private fun setContent(state: MutableState<ReminderUiState>, actions: QingKeAppActions = QingKeAppActions()) {
        rule.setContent {
            QingKeAppContent(
                settingsState(),
                existingForm(),
                MainTab.SETTINGS,
                actions,
                reminder = state.value,
            )
        }
    }

    private fun reminderState(
        remindersEnabled: Boolean = false,
        notificationsPermitted: Boolean = false,
        channelReady: Boolean = false,
        exactAlarmsAvailable: Boolean = false,
        leadMinutes: Int = 10,
        usesCustomLeadTime: Boolean = false,
        activeCount: Int = 0,
        degraded: Boolean = false,
        lastFailureCount: Int = 0,
        diagnostic: String? = null,
    ) = ReminderUiState(
        loaded = true,
        remindersEnabled = remindersEnabled,
        leadMinutes = leadMinutes,
        usesCustomLeadTime = usesCustomLeadTime,
        notificationsPermitted = notificationsPermitted,
        channelReady = channelReady,
        exactAlarmsAvailable = exactAlarmsAvailable,
        activeCount = activeCount,
        degraded = degraded,
        lastFailureCount = lastFailureCount,
        diagnostic = diagnostic,
    )

    private fun settingsState() = ScheduleState(
        data = ScheduleData(1, semester(), emptyList(), "1970-01-01T00:00:00Z"),
        preferences = SchedulePreferences.defaults,
        loadStatus = LoadStatus.READY,
    )

    private fun semester() = Semester("semester", "测试学期", "2026-08-31", 18, listOf(Period(1, "08:00", "08:45")))

    private fun existingForm() = SemesterFormState(
        id = "semester",
        name = "测试学期",
        startDate = LocalDate.parse("2026-08-31"),
        totalWeeks = 18,
        periods = listOf(PeriodFormState("p1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45))),
        periodsExpanded = false,
    )
}
