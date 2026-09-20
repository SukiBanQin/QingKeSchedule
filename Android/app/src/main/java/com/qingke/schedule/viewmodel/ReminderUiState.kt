package com.qingke.schedule.viewmodel

import com.qingke.schedule.preferences.ReminderPreferences

/**
 * A08 second batch: everything the reminder settings section renders. The status wording mirrors the iOS
 * `reminderStatusMessage` states so both platforms explain the same situation.
 */
data class ReminderUiState(
    val loaded: Boolean = false,
    val remindersEnabled: Boolean = false,
    val leadMinutes: Int = ReminderPreferences.DEFAULT_LEAD_MINUTES,
    val usesCustomLeadTime: Boolean = false,
    val notificationsPermitted: Boolean = false,
    val channelReady: Boolean = false,
    val exactAlarmsAvailable: Boolean = false,
    val activeCount: Int = 0,
    val degraded: Boolean = false,
    val lastFailureCount: Int = 0,
    val diagnostic: String? = null,
) {
    val statusMessage: String
        get() = when {
            diagnostic != null -> "课表已保存，但提醒更新失败：" + diagnostic
            !remindersEnabled -> "提醒已关闭"
            !loaded -> "正在读取提醒状态…"
            !notificationsPermitted -> "系统通知权限未开启，提醒不会投递"
            !channelReady -> "提醒渠道不可用，提醒不会投递"
            activeCount == 0 -> "当前没有待安排的课程提醒"
            degraded -> "已安排最近 " + activeCount + " 条课程提醒（部分可能延迟）"
            else -> "已安排最近 " + activeCount + " 条课程提醒"
        }

    /** Only an enabled reminder whose permission is missing offers the permission action. */
    val asksForNotificationPermission: Boolean get() = remindersEnabled && !notificationsPermitted

    /** True while the last run left alarms behind that still need a retry. */
    val retriesLater: Boolean get() = lastFailureCount > 0

    /** The exact-alarm capability is only shown as a warning while reminders are actually on. */
    val showsInexactNote: Boolean get() = remindersEnabled && !exactAlarmsAvailable && loaded
}
