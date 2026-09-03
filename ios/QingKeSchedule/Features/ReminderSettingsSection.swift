import SwiftUI
import UIKit

struct ReminderSettingsSection: View {
    @Bindable var state: ScheduleAppState
    @Environment(\.openURL) private var openURL
    @State private var permissionExplanationPresented = false

    var body: some View {
        Section {
            Toggle(
                "上课提醒",
                isOn: Binding(
                    get: { state.reminderSettings.remindersEnabled },
                    set: updateReminderToggle
                )
            )
            .accessibilityIdentifier("reminders-toggle")
            .alert("开启上课提醒？", isPresented: $permissionExplanationPresented) {
                Button("暂不开启", role: .cancel) {}
                Button("启用提醒") {
                    state.setRemindersEnabled(true)
                }
            } message: {
                Text("青课会请求系统通知权限，只用于在课程开始前显示课程名称、时间和教室。")
            }

            if state.reminderSettings.remindersEnabled {
                Picker(
                    "提醒时间",
                    selection: Binding(
                        get: { state.reminderSettings.reminderLeadMinutes },
                        set: state.setReminderLeadMinutes
                    )
                ) {
                    ForEach(ReminderSettings.allowedLeadMinutes, id: \.self) { minutes in
                        Text(minutes == 0 ? "准时" : "提前 \(minutes) 分钟")
                            .tag(minutes)
                    }
                }
                .accessibilityIdentifier("reminder-lead-minutes")
            }

            HStack {
                Image(systemName: statusSystemImage)
                    .accessibilityHidden(true)
                Text(state.reminderStatusMessage)
                    .accessibilityIdentifier("reminders-status")
            }
            .foregroundStyle(statusColor)

            if state.reminderSettings.remindersEnabled,
               state.notificationPermission == .denied {
                Button {
                    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                    openURL(url)
                } label: {
                    Label("前往系统设置开启通知", systemImage: "gear")
                }
                .accessibilityIdentifier("system-notification-settings")
            }
        } header: {
            Text("上课提醒")
        } footer: {
            Text("提醒仅保存在这台 iPhone，并按课程开始时间维护最近 60 条。")
        }
    }

    private var statusSystemImage: String {
        if state.notificationDiagnostic != nil { return "exclamationmark.triangle.fill" }
        if !state.reminderSettings.remindersEnabled { return "bell.slash" }
        if state.notificationPermission == .denied { return "bell.slash.fill" }
        return "bell.badge"
    }

    private var statusColor: Color {
        if state.notificationDiagnostic != nil || state.notificationPermission == .denied {
            return .orange
        }
        return .secondary
    }

    private func updateReminderToggle(_ enabled: Bool) {
        if enabled, state.notificationPermission == .notDetermined {
            permissionExplanationPresented = true
        } else {
            state.setRemindersEnabled(enabled)
        }
    }
}
