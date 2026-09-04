import SwiftUI

struct SemesterFormView: View {
    let isOnboarding: Bool
    let onSave: (SemesterDTO) -> Bool
    let dataTransferState: ScheduleAppState?

    @State private var draft: SemesterDraft
    @State private var issues: [ScheduleValidationIssue] = []
    @State private var savedMessage: String?
    @State private var calendarExpanded = false

    init(
        semester: SemesterDTO?,
        isOnboarding: Bool,
        now: Date = Date(),
        dataTransferState: ScheduleAppState? = nil,
        onSave: @escaping (SemesterDTO) -> Bool
    ) {
        self.isOnboarding = isOnboarding
        self.onSave = onSave
        self.dataTransferState = dataTransferState
        _draft = State(initialValue: SemesterDraft(semester: semester, now: now))
    }

    var body: some View {
        ZStack {
            TerminalBackdrop()

            VStack(spacing: 0) {
                settingsHeader

                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        terminalIntro

                        TerminalFormSection(index: "01", title: "学期信息", detail: "TERM") {
                            TextField("学期名称", text: $draft.name)
                                .textInputAutocapitalization(.never)
                                .textFieldStyle(.plain)
                                .terminalControl()
                                .accessibilityIdentifier("semester-name")

                            TerminalFormDivider()
                            chineseDatePicker

                            TerminalFormDivider()
                            Stepper(
                                "总周数：\(draft.totalWeeks)",
                                value: $draft.totalWeeks,
                                in: 1...52
                            )
                            .terminalControl()
                            .accessibilityIdentifier("semester-total-weeks")
                        }

                        TerminalFormSection(
                            index: "02",
                            title: "每日节次",
                            detail: "PERIODS / \(draft.periods.count)",
                            footer: "教学周从开始日期所在周的周一算起；课程统一使用这里的节次时间。"
                        ) {
                            ForEach(Array(draft.periods.indices), id: \.self) { index in
                                periodRow(index: index)
                                if index < draft.periods.count - 1 {
                                    TerminalFormDivider()
                                }
                            }

                            TerminalFormDivider()
                            Button {
                                draft.addPeriod()
                            } label: {
                                Label("添加节次", systemImage: "plus")
                            }
                            .terminalControl()
                            .disabled(draft.periods.count >= 20)
                            .accessibilityIdentifier("add-period")
                        }

                        if let issue = issues.first {
                            Label(issue.message, systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(QingKeTheme.danger)
                                .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                                .terminalPanel(accent: QingKeTheme.danger)
                                .accessibilityIdentifier("semester-validation-error")
                        }

                        if !isOnboarding, let dataTransferState {
                            ReminderSettingsSection(state: dataTransferState)
                        }

                        if let dataTransferState {
                            DataTransferSection(state: dataTransferState)
                        }

                        saveButton
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 14)
                    .padding(.bottom, 32)
                }
            }

            if let dataTransferState,
               dataTransferState.pendingImportPreview != nil
                   || dataTransferState.importFailure != nil {
                ScheduleImportPromptView(state: dataTransferState)
                    .zIndex(20)
            }
        }
        .tint(QingKeTheme.cyan)
        .overlay(alignment: .bottom) {
            if let savedMessage {
                TerminalToast(message: savedMessage)
                    .accessibilityIdentifier("semester-save-success")
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.22), value: savedMessage)
    }

    private var settingsHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text(isOnboarding ? "首次设置" : "学期与节次")
                    .font(.headline)
                Text(isOnboarding ? "INITIAL SETUP" : "SYSTEM CONFIG")
                    .font(.terminal(8, weight: .black, relativeTo: .caption2))
                    .tracking(1)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(isOnboarding ? "继续" : "保存") { save() }
                .font(.headline)
                .foregroundStyle(QingKeTheme.signal)
                .accessibilityIdentifier("semester-save-toolbar")
        }
        .padding(.horizontal, 20)
        .frame(height: 58)
        .background(QingKeTheme.ink.opacity(0.96))
        .foregroundStyle(.white)
        .overlay(alignment: .bottom) {
            Rectangle().fill(QingKeTheme.signal).frame(height: 3)
        }
    }

    private var chineseDatePicker: some View {
        VStack(spacing: 0) {
            Button {
                withAnimation(.easeOut(duration: 0.18)) {
                    calendarExpanded.toggle()
                }
            } label: {
                HStack {
                    Text("开始日期")
                    Spacer()
                    Text(draft.startDate.formatted(
                        .dateTime.locale(Locale(identifier: "zh_CN")).year().month().day()
                    ))
                        .foregroundStyle(.secondary)
                    Image(systemName: calendarExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption.bold())
                }
                .foregroundStyle(.primary)
            }
            .buttonStyle(.plain)
            .terminalControl()
            .accessibilityIdentifier("semester-start-date")

            if calendarExpanded {
                TerminalFormDivider()
                DatePicker(
                    "开始日期",
                    selection: $draft.startDate,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "zh_CN"))
                .tint(QingKeTheme.signal)
                .accessibilityIdentifier("semester-start-date-calendar")
            }
        }
    }

    private func periodRow(index: Int) -> some View {
        let period = $draft.periods[index]
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("第 \(period.wrappedValue.number) 节")
                    .font(.headline)
                Spacer()
                if draft.periods.count > 1 {
                    Button(role: .destructive) {
                        draft.removePeriod(id: period.wrappedValue.id)
                    } label: {
                        Image(systemName: "trash")
                    }
                    .buttonStyle(.plain)
                    .frame(width: 44, height: 44)
                    .accessibilityLabel("删除第 \(period.wrappedValue.number) 节")
                }
            }
            HStack(spacing: 12) {
                DatePicker(
                    "开始",
                    selection: period.startTime,
                    displayedComponents: .hourAndMinute
                )
                DatePicker(
                    "结束",
                    selection: period.endTime,
                    displayedComponents: .hourAndMinute
                )
            }
            .environment(\.locale, Locale(identifier: "zh_CN"))
        }
        .padding(.vertical, 10)
    }

    private var saveButton: some View {
        Button { save() } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(isOnboarding ? "创建课表" : "保存学期设置")
                        .font(.headline)
                    Text(isOnboarding ? "INITIALIZE TERMINAL" : "COMMIT CHANGES")
                        .font(.terminal(8, weight: .bold, relativeTo: .caption2))
                        .tracking(1)
                        .foregroundStyle(.white.opacity(0.62))
                }
                Spacer()
                Image(systemName: "arrow.right")
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 58)
            .background(QingKeTheme.ink)
            .overlay(alignment: .bottom) {
                Rectangle().fill(QingKeTheme.signal).frame(height: 4)
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("semester-save")
    }

    private var terminalIntro: some View {
        VStack(alignment: .leading, spacing: 18) {
            TerminalBrandHeader(code: isOnboarding ? "SETUP / 00" : "SYSTEM / 03")

            HStack(alignment: .bottom, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    TerminalStatusTag(
                        text: isOnboarding ? "FIRST BOOT" : "CONFIGURATION",
                        tint: isOnboarding ? QingKeTheme.signal : QingKeTheme.cyan
                    )
                    Text(isOnboarding ? "首次设置" : "系统设置")
                        .font(.system(size: 36, weight: .black))
                        .accessibilityIdentifier(
                            isOnboarding ? "onboarding-title" : "settings-title"
                        )
                    Text(isOnboarding
                         ? "配置学期与每日节次，完成后即可录入第一门课程。"
                         : "管理学期、节次、提醒与本地课表备份。")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 0)

                VStack(alignment: .trailing, spacing: -4) {
                    Text(isOnboarding ? "INIT" : "SYS")
                        .font(.terminal(9, weight: .black, relativeTo: .caption2))
                        .tracking(1)
                    Text(isOnboarding ? "00" : "03")
                        .font(.terminal(42, weight: .light, relativeTo: .title))
                }
            }
        }
        .padding(.bottom, 6)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(
            isOnboarding ? "onboarding-terminal-header" : "settings-terminal-header"
        )
    }

    private func save() {
        issues = draft.validationIssues()
        savedMessage = nil
        guard issues.isEmpty else { return }

        if onSave(draft.semester()), !isOnboarding {
            let message = "SYSTEM // 学期与提醒设置已保存"
            savedMessage = message
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.6) {
                if savedMessage == message {
                    savedMessage = nil
                }
            }
        }
    }
}
