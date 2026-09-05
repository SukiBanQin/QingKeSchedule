import SwiftUI

struct SemesterFormView: View {
    let isOnboarding: Bool
    let onSave: (SemesterDTO) -> Bool
    let dataTransferState: ScheduleAppState?

    @State private var draft: SemesterDraft
    @State private var periodsExpanded = false
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
        let initialDraft = SemesterDraft(semester: semester, now: now)
        _draft = State(initialValue: initialDraft)
        _periodsExpanded = State(
            initialValue: Self.shouldExpandPeriodsByDefault(
                periodCount: initialDraft.periods.count
            )
        )
    }

    static func shouldExpandPeriodsByDefault(periodCount: Int) -> Bool {
        periodCount < 5
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
                            Button {
                                togglePeriods()
                            } label: {
                                HStack {
                                    Text(periodsExpanded ? "收起节次设置" : "展开节次设置")
                                        .font(.terminal(11, weight: .bold, relativeTo: .body))
                                    Spacer()
                                    Text("\(draft.periods.count) 节")
                                        .foregroundStyle(.secondary)
                                    Image(systemName: periodsExpanded ? "chevron.up" : "chevron.down")
                                        .font(.caption.bold())
                                }
                                .foregroundStyle(QingKeTheme.cyan)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    togglePeriods()
                                }
                            }
                            .buttonStyle(.plain)
                            .terminalControl()
                            .zIndex(1)
                            .accessibilityIdentifier("daily-periods-toggle")
                            .accessibilityLabel("每日节次")
                            .accessibilityValue(
                                "\(draft.periods.count) 节，\(periodsExpanded ? "已展开" : "已收起")"
                            )

                            if periodsExpanded {
                                TerminalFormDivider()
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
                        }

                        if let dataTransferState {
                            AcademicCalendarSettingsSection(
                                state: dataTransferState,
                                initialDate: draft.startDate
                            )
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

    private func togglePeriods() {
        withAnimation(.easeOut(duration: 0.18)) {
            periodsExpanded.toggle()
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

private struct AcademicCalendarSettingsSection: View {
    @Bindable var state: ScheduleAppState
    @State private var selectedDate: Date
    @State private var followsDayOfWeek = 1
    @State private var mode = ExceptionMode.nonTeaching
    @State private var calendarExpanded = false
    @State private var lunchStartTime: Date
    @State private var lunchEndTime: Date

    init(state: ScheduleAppState, initialDate: Date) {
        self.state = state
        _selectedDate = State(initialValue: initialDate)
        _lunchStartTime = State(initialValue: Self.timeDate(
            state.academicCalendarSettings.lunchBreak.startTime,
            calendar: state.calendar
        ))
        _lunchEndTime = State(initialValue: Self.timeDate(
            state.academicCalendarSettings.lunchBreak.endTime,
            calendar: state.calendar
        ))
    }

    var body: some View {
        TerminalFormSection(
            index: "03",
            title: "教学日历",
            detail: "CALENDAR",
            footer: "停课日优先级最高；调课日可指定按某个星期的课表上课，适用于节假日调休。"
        ) {
            Toggle(
                "周末默认不上课",
                isOn: Binding(
                    get: { state.academicCalendarSettings.weekendsAreNonTeachingDays },
                    set: state.setWeekendsAreNonTeachingDays
                )
            )
            .terminalControl()
            .accessibilityIdentifier("weekends-non-teaching-toggle")

            TerminalFormDivider()

            Toggle(
                "在周课表显示午休",
                isOn: Binding(
                    get: { state.academicCalendarSettings.lunchBreak.isEnabled },
                    set: state.setLunchBreakEnabled
                )
            )
            .terminalControl()
            .accessibilityIdentifier("lunch-break-toggle")

            if state.academicCalendarSettings.lunchBreak.isEnabled {
                HStack(spacing: 12) {
                    DatePicker(
                        "开始",
                        selection: $lunchStartTime,
                        displayedComponents: .hourAndMinute
                    )
                    DatePicker(
                        "结束",
                        selection: $lunchEndTime,
                        displayedComponents: .hourAndMinute
                    )
                }
                .environment(\.locale, Locale(identifier: "zh_CN"))
                .padding(.vertical, 10)
                .onChange(of: lunchStartTime) { _, _ in persistLunchBreak() }
                .onChange(of: lunchEndTime) { _, _ in persistLunchBreak() }
                .accessibilityIdentifier("lunch-break-times")

                if !lunchTimesAreValid {
                    Text("午休开始时间必须早于结束时间。")
                        .font(.caption)
                        .foregroundStyle(QingKeTheme.danger)
                }
            }

            TerminalFormDivider()

            HStack(spacing: 8) {
                modeButton(.nonTeaching)
                modeButton(.makeup)
            }
            .padding(.vertical, 10)

            TerminalFormDivider()

            Button {
                withAnimation(.easeOut(duration: 0.18)) {
                    calendarExpanded.toggle()
                }
            } label: {
                HStack {
                    Text("日期")
                    Spacer()
                    Text(selectedDate.formatted(
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
            .accessibilityIdentifier("calendar-exception-date")

            if calendarExpanded {
                TerminalFormDivider()
                DatePicker(
                    "日期",
                    selection: $selectedDate,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "zh_CN"))
                .tint(QingKeTheme.signal)
                .accessibilityIdentifier("calendar-exception-date-picker")
            }

            if mode == .makeup {
                TerminalFormDivider()
                Picker("按课表上课", selection: $followsDayOfWeek) {
                    ForEach(1...7, id: \.self) { day in
                        Text(ScheduleDisplayText.weekdayNames[day - 1]).tag(day)
                    }
                }
                .terminalControl()
                .accessibilityIdentifier("makeup-source-weekday")
            }

            TerminalFormDivider()

            Button(action: addException) {
                Text(mode == .nonTeaching ? "添加停课日" : "添加调课日")
                    .font(.headline)
                    .foregroundStyle(QingKeTheme.ink)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(QingKeTheme.signal)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("add-calendar-exception")

            if !state.academicCalendarSettings.nonTeachingDates.isEmpty {
                TerminalFormDivider()
                exceptionHeader("停课日 / OFF")
                ForEach(state.academicCalendarSettings.nonTeachingDates, id: \.self) { date in
                    exceptionRow(date: date, detail: "不显示课程") {
                        state.removeNonTeachingDate(date)
                    }
                }
            }

            if !state.academicCalendarSettings.makeupTeachingDays.isEmpty {
                TerminalFormDivider()
                exceptionHeader("调课日 / MAKEUP")
                ForEach(state.academicCalendarSettings.makeupTeachingDays) { day in
                    exceptionRow(
                        date: day.date,
                        detail: "按\(ScheduleDisplayText.weekdayNames[day.followsDayOfWeek - 1])课表"
                    ) {
                        state.removeMakeupTeachingDay(day.date)
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("academic-calendar-settings")
    }

    private func modeButton(_ candidate: ExceptionMode) -> some View {
        Button {
            mode = candidate
        } label: {
            Text(candidate.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(mode == candidate ? Color.white : Color.primary)
                .frame(maxWidth: .infinity, minHeight: 42)
                .background(mode == candidate ? QingKeTheme.ink : Color.clear)
                .overlay {
                    Rectangle().stroke(Color.primary.opacity(0.3), lineWidth: 1)
                }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(mode == candidate ? .isSelected : [])
    }

    private func exceptionHeader(_ title: String) -> some View {
        Text(title)
            .font(.terminal(9, weight: .black, relativeTo: .caption2))
            .tracking(1)
            .foregroundStyle(.secondary)
            .padding(.top, 12)
    }

    private func exceptionRow(
        date: String,
        detail: String,
        onDelete: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(localizedDate(date))
                    .font(.headline)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("删除 \(localizedDate(date))")
        }
        .padding(.vertical, 4)
    }

    private func addException() {
        switch mode {
        case .nonTeaching:
            state.addNonTeachingDate(selectedDate)
        case .makeup:
            state.addMakeupTeachingDay(selectedDate, followsDayOfWeek: followsDayOfWeek)
        }
        calendarExpanded = false
    }

    private func localizedDate(_ value: String) -> String {
        guard let date = ScheduleRules.localDate(from: value, calendar: state.calendar) else {
            return value
        }
        return date.formatted(
            .dateTime.locale(Locale(identifier: "zh_CN")).year().month().day().weekday()
        )
    }

    private var lunchTimesAreValid: Bool {
        timeString(lunchStartTime) < timeString(lunchEndTime)
    }

    private func persistLunchBreak() {
        _ = state.setLunchBreak(
            startTime: timeString(lunchStartTime),
            endTime: timeString(lunchEndTime)
        )
    }

    private func timeString(_ date: Date) -> String {
        let components = state.calendar.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", components.hour ?? 0, components.minute ?? 0)
    }

    private static func timeDate(_ value: String, calendar: Calendar) -> Date {
        let minutes = ScheduleRules.minutes(from: value) ?? 0
        return calendar.date(from: DateComponents(
            calendar: calendar,
            timeZone: calendar.timeZone,
            year: 2001,
            month: 1,
            day: 1,
            hour: minutes / 60,
            minute: minutes % 60
        )) ?? Date(timeIntervalSinceReferenceDate: 0)
    }
}

private enum ExceptionMode: Equatable {
    case nonTeaching
    case makeup

    var title: String {
        switch self {
        case .nonTeaching: "停课日"
        case .makeup: "调课上课"
        }
    }
}
