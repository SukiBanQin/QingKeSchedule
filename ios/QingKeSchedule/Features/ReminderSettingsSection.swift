import SwiftUI
import UIKit

struct ReminderSettingsSection: View {
    @Bindable var state: ScheduleAppState
    let onRequestPermissionExplanation: () -> Void
    @Environment(\.openURL) private var openURL
    @State private var customLeadMinutes = 20

    var body: some View {
        TerminalFormSection(
            index: "04",
            title: "上课提醒",
            detail: "NOTIFY",
            footer: "提醒仅保存在这台 iPhone，并按课程开始时间维护最近 60 条。"
        ) {
            Toggle(
                "上课提醒",
                isOn: Binding(
                    get: { state.reminderSettings.remindersEnabled },
                    set: updateReminderToggle
                )
            )
            .accessibilityIdentifier("reminders-toggle")
            .terminalControl()

            if state.reminderSettings.remindersEnabled {
                TerminalFormDivider()
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
                .terminalControl()
                .accessibilityIdentifier("reminder-lead-minutes")

                if leadSelection == .custom {
                    TerminalFormDivider()
                    Stepper(
                        "提前 \(customLeadMinutes) 分钟",
                        value: $customLeadMinutes,
                        in: 1...ReminderSettings.validLeadMinutes.upperBound
                    )
                    .onChange(of: customLeadMinutes) { _, newValue in
                        state.setReminderLeadMinutes(
                            newValue,
                            usesCustomSelection: true
                        )
                    }
                    .terminalControl()
                    .accessibilityIdentifier("reminder-custom-lead-minutes")

                    Text("可自定义 1–180 分钟；0 分钟请在上方选择“准时”。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            TerminalFormDivider()
            HStack {
                Image(systemName: statusSystemImage)
                    .accessibilityHidden(true)
                Text(state.reminderStatusMessage)
                    .accessibilityIdentifier("reminders-status")
            }
            .foregroundStyle(statusColor)
            .terminalControl()

            if state.reminderSettings.remindersEnabled,
               state.notificationPermission == .denied {
                TerminalFormDivider()
                Button {
                    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                    openURL(url)
                } label: {
                    Label("前往系统设置开启通知", systemImage: "gear")
                }
                .terminalControl()
                .accessibilityIdentifier("system-notification-settings")
            }
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
            onRequestPermissionExplanation()
        } else {
            state.setRemindersEnabled(enabled)
        }
    }

    private var leadSelection: ReminderLeadSelection {
        if state.reminderSettings.usesCustomLeadTime {
            return .custom
        }
        return .preset(state.reminderSettings.reminderLeadMinutes)
    }

    private func updateLeadSelection(_ selection: ReminderLeadSelection) {
        switch selection {
        case .preset(let minutes):
            state.setReminderLeadMinutes(minutes, usesCustomSelection: false)
        case .custom:
            state.setReminderLeadMinutes(
                customLeadMinutes,
                usesCustomSelection: true
            )
        }
    }

    private func synchronizeCustomLeadMinutes() {
        let stored = state.reminderSettings.reminderLeadMinutes
        if state.reminderSettings.usesCustomLeadTime, stored > 0 {
            customLeadMinutes = stored
        }
    }
}

private enum ReminderLeadSelection: Hashable {
    case preset(Int)
    case custom
}
