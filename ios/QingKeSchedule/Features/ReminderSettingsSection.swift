import SwiftUI
import UIKit

struct ReminderSettingsSection: View {
    @Bindable var state: ScheduleAppState
    @Environment(\.openURL) private var openURL
    @State private var permissionExplanationPresented = false
    @State private var customLeadMinutes = 20

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
                        get: { leadSelection },
                        set: updateLeadSelection
                    )
                ) {
                    ForEach(ReminderSettings.presetLeadMinutes, id: \.self) { minutes in
                        Text(minutes == 0 ? "准时" : "提前 \(minutes) 分钟")
                            .tag(ReminderLeadSelection.preset(minutes))
                    }
                    Text("自定义…")
                        .tag(ReminderLeadSelection.custom)
                }
                .accessibilityIdentifier("reminder-lead-minutes")

                if leadSelection == .custom {
                    Stepper(
                        "提前 \(customLeadMinutes) 分钟",
                        value: $customLeadMinutes,
                        in: 1...ReminderSettings.validLeadMinutes.upperBound
                    )
                    .onChange(of: customLeadMinutes) { _, newValue in
                        state.setReminderLeadMinutes(newValue)
                    }
                    .accessibilityIdentifier("reminder-custom-lead-minutes")

                    Text("可自定义 1–180 分钟；0 分钟请在上方选择“准时”。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
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
        .onAppear(perform: synchronizeCustomLeadMinutes)
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

    private var leadSelection: ReminderLeadSelection {
        let minutes = state.reminderSettings.reminderLeadMinutes
        return ReminderSettings.presetLeadMinutes.contains(minutes)
            ? .preset(minutes)
            : .custom
    }

    private func updateLeadSelection(_ selection: ReminderLeadSelection) {
        switch selection {
        case .preset(let minutes):
            state.setReminderLeadMinutes(minutes)
        case .custom:
            state.setReminderLeadMinutes(customLeadMinutes)
        }
    }

    private func synchronizeCustomLeadMinutes() {
        let stored = state.reminderSettings.reminderLeadMinutes
        if !ReminderSettings.presetLeadMinutes.contains(stored), stored > 0 {
            customLeadMinutes = stored
        }
    }
}

private enum ReminderLeadSelection: Hashable {
    case preset(Int)
    case custom
}
