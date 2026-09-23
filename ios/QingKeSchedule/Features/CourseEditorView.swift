import SwiftUI

struct CourseEditorView: View {
    let semester: SemesterDTO
    let existingCourses: [CourseDTO]
    let editingCourse: CourseDTO?
    let appendingScheduleOnly: Bool
    let originalScheduleCount: Int
    let calendar: Calendar
    let onSave: (CourseDTO) -> Bool
    let onDelete: (String) -> Bool

    @Environment(\.dismiss) private var dismiss
    @State private var draft: CourseDraft
    @State private var issues: [ScheduleValidationIssue] = []
    @State private var pendingCourse: CourseDTO?
    @State private var conflicts: [ScheduleConflictDTO] = []
    @State private var prompt: CourseEditorPrompt?

    init(
        semester: SemesterDTO,
        existingCourses: [CourseDTO],
        course: CourseDTO?,
        appendingScheduleOnly: Bool = false,
        now: Date,
        calendar: Calendar,
        onSave: @escaping (CourseDTO) -> Bool,
        onDelete: @escaping (String) -> Bool
    ) {
        self.semester = semester
        self.existingCourses = existingCourses
        editingCourse = course
        self.appendingScheduleOnly = appendingScheduleOnly
        originalScheduleCount = appendingScheduleOnly ? (course?.schedules.count ?? 0) : 0
        self.calendar = calendar
        self.onSave = onSave
        self.onDelete = onDelete
        _draft = State(initialValue: CourseDraft(
            course: course,
            appendingSchedule: appendingScheduleOnly,
            semester: semester,
            now: now,
            calendar: calendar
        ))
    }

    var body: some View {
        ZStack {
            TerminalBackdrop()

            VStack(spacing: 0) {
                editorHeader
                TerminalPinnedBrandHeader(
                    code: appendingScheduleOnly
                        ? "APPEND / 04"
                        : (editingCourse == nil ? "CREATE / 04" : "EDIT / 04"),
                    accessibilityIdentifier: "course-editor-brand-header"
                )

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        courseIdentitySection

                        ForEach(visibleScheduleIndices, id: \.self) { index in
                            scheduleSection(index: index)
                        }

                        Button {
                            draft.addSchedule()
                        } label: {
                            Label("添加上课安排", systemImage: "plus")
                                .font(.headline)
                                .foregroundStyle(QingKeTheme.textOnAccent)
                                .frame(maxWidth: .infinity, minHeight: 54)
                                .background(QingKeTheme.signal)
                                .overlay {
                                    Rectangle().stroke(QingKeTheme.textOnInverse.opacity(0.78), lineWidth: 1)
                                }
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("add-course-schedule")

                        if let issue = issues.first {
                            Label(issue.message, systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(QingKeTheme.danger)
                                .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                                .terminalPanel(accent: QingKeTheme.danger)
                                .accessibilityIdentifier("course-validation-error")
                        }

                        if editingCourse != nil, !appendingScheduleOnly {
                            TerminalFormSection(
                                index: "99",
                                title: "危险操作",
                                footer: "删除后，这门课程的所有上课安排都会一并移除。"
                            ) {
                                Button(role: .destructive) {
                                    prompt = .delete
                                } label: {
                                    Text("删除课程")
                                        .frame(maxWidth: .infinity, minHeight: 52)
                                        .contentShape(Rectangle())
                                }
                                .accessibilityIdentifier("course-delete")
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 14)
                    .padding(.bottom, 30)
                }
            }
            .accessibilityHidden(prompt != nil)

            if let prompt {
                terminalDialog(for: prompt)
            }
        }
        .tint(QingKeTheme.cyan)
        .interactiveDismissDisabled(draft.isDirty)
        .animation(.easeOut(duration: 0.18), value: prompt)
    }

    private var editorHeader: some View {
        HStack(spacing: 14) {
            Button("取消") { cancel() }
                .font(.headline)
                .accessibilityIdentifier("course-cancel")

            Spacer()

            VStack(spacing: 1) {
                Text(appendingScheduleOnly
                     ? "添加上课安排"
                     : (editingCourse == nil ? "添加课程" : "编辑课程"))
                    .font(.headline)
                Text(appendingScheduleOnly
                     ? "NEW SCHEDULE"
                     : (editingCourse == nil ? "NEW COURSE" : "COURSE PROFILE"))
                    .font(.terminal(8, weight: .black, relativeTo: .caption2))
                    .tracking(1)
                    .foregroundStyle(QingKeTheme.textOnInverse.opacity(0.68))
            }

            Spacer()

            Button("保存") { save() }
                .font(.headline)
                .foregroundStyle(QingKeTheme.signal)
                .accessibilityIdentifier("course-save")
        }
        .padding(.horizontal, 20)
        .frame(height: 58)
        .background(QingKeTheme.inverseSurface)
        .foregroundStyle(QingKeTheme.textOnInverse)
        .overlay(alignment: .bottom) {
            Rectangle().fill(QingKeTheme.signal).frame(height: 3)
        }
    }

    @ViewBuilder
    private var courseIdentitySection: some View {
        if appendingScheduleOnly {
            TerminalFormSection(
                index: "01",
                title: "沿用课程资料",
                detail: "REUSED PROFILE",
                footer: "课程名称、教师和识别色沿用已有课程；这里只新增上课安排。"
            ) {
                HStack(spacing: 12) {
                    Rectangle()
                        .fill(Color(courseHex: draft.color))
                        .frame(width: 8, height: 52)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(draft.name)
                            .font(.title3.bold())
                        Text(draft.teacher.isEmpty ? "未填写教师" : draft.teacher)
                            .font(.subheadline)
                            .foregroundStyle(QingKeTheme.textSecondary)
                    }
                }
                .padding(.vertical, 10)
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("reused-course-profile")
            }
        } else {
            TerminalFormSection(index: "01", title: "课程信息") {
                TextField("课程名称", text: $draft.name)
                    .textFieldStyle(.plain)
                    .terminalControl()
                    .accessibilityIdentifier("course-name")
                TerminalFormDivider()
                TextField("教师（选填）", text: $draft.teacher)
                    .textFieldStyle(.plain)
                    .terminalControl()
                    .accessibilityIdentifier("course-teacher")
                TerminalFormDivider()
                colorPicker
                    .padding(.vertical, 12)
            }
        }
    }

    private var visibleScheduleIndices: [Int] {
        if appendingScheduleOnly {
            return Array(draft.schedules.indices.dropFirst(originalScheduleCount))
        }
        return Array(draft.schedules.indices)
    }

    private var colorPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("课程颜色")
                .font(.subheadline)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 42), spacing: 12)], spacing: 12) {
                ForEach(CourseColorPalette.presets, id: \.value) { option in
                    Button {
                        draft.color = option.value
                    } label: {
                        ZStack {
                            Circle()
                                .fill(Color(courseHex: option.value))
                                .frame(width: 34, height: 34)
                            if draft.color.caseInsensitiveCompare(option.value) == .orderedSame {
                                Image(systemName: "checkmark")
                                    .font(.caption.bold())
                                    .foregroundStyle(QingKeTheme.courseContentColor(for: option.value))
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(option.name)
                    .accessibilityAddTraits(
                        draft.color.caseInsensitiveCompare(option.value) == .orderedSame
                            ? .isSelected
                            : []
                    )
                }
            }
            .frame(maxWidth: .infinity)

            Divider()

            ColorPicker(
                "自定义颜色",
                selection: Binding(
                    get: { Color(courseHex: draft.color) },
                    set: { selectedColor in
                        if let value = selectedColor.courseHexValue {
                            draft.color = value
                        }
                    }
                ),
                supportsOpacity: false
            )
            .accessibilityIdentifier("course-custom-color")

            Text("当前色值  \(draft.color.uppercased())")
                .font(.terminal(10, weight: .bold, relativeTo: .caption))
                .tracking(0.7)
                .foregroundStyle(QingKeTheme.textSecondary)
        }
    }

    private func scheduleSection(index: Int) -> some View {
        let identifier = draft.schedules[index].id
        return TerminalFormSection(
            index: String(format: "%02d", index + 2),
            title: "上课安排 \(index + 1)",
            detail: "SCHEDULE"
        ) {
            Picker("星期", selection: $draft.schedules[index].dayOfWeek) {
                ForEach(1...7, id: \.self) { day in
                    Text(ScheduleDisplayText.weekdayNames[day - 1]).tag(day)
                }
            }
            .terminalControl()
            .accessibilityIdentifier("course-weekday-\(index)")

            TerminalFormDivider()

            Picker("开始节次", selection: $draft.schedules[index].startPeriod) {
                ForEach(semester.periods, id: \.number) { period in
                    Text("第 \(period.number) 节 · \(period.startTime)").tag(period.number)
                }
            }
            .terminalControl()
            .accessibilityIdentifier("course-start-period-\(index)")
            .onChange(of: draft.schedules[index].startPeriod) { _, newValue in
                if draft.schedules[index].endPeriod < newValue {
                    draft.schedules[index].endPeriod = newValue
                }
            }

            TerminalFormDivider()

            Picker("结束节次", selection: $draft.schedules[index].endPeriod) {
                ForEach(semester.periods, id: \.number) { period in
                    Text("第 \(period.number) 节 · \(period.endTime)").tag(period.number)
                }
            }
            .terminalControl()
            .accessibilityIdentifier("course-end-period-\(index)")
            .onChange(of: draft.schedules[index].endPeriod) { _, newValue in
                if draft.schedules[index].startPeriod > newValue {
                    draft.schedules[index].startPeriod = newValue
                }
            }

            TerminalFormDivider()

            Stepper(
                "开始周：\(draft.schedules[index].startWeek)",
                value: $draft.schedules[index].startWeek,
                in: 1...semester.totalWeeks
            )
            .onChange(of: draft.schedules[index].startWeek) { _, newValue in
                if draft.schedules[index].endWeek < newValue {
                    draft.schedules[index].endWeek = newValue
                }
            }
            .terminalControl()

            TerminalFormDivider()

            Stepper(
                "结束周：\(draft.schedules[index].endWeek)",
                value: $draft.schedules[index].endWeek,
                in: 1...semester.totalWeeks
            )
            .onChange(of: draft.schedules[index].endWeek) { _, newValue in
                if draft.schedules[index].startWeek > newValue {
                    draft.schedules[index].startWeek = newValue
                }
            }
            .terminalControl()

            TerminalFormDivider()

            Picker("重复", selection: $draft.schedules[index].repeatRule) {
                Text("每周").tag(RepeatRule.every)
                Text("单周").tag(RepeatRule.odd)
                Text("双周").tag(RepeatRule.even)
            }
            .pickerStyle(.segmented)
            .padding(.vertical, 10)
            .accessibilityIdentifier("course-repeat-\(index)")

            TerminalFormDivider()

            TextField("教室（选填）", text: $draft.schedules[index].classroom)
                .textFieldStyle(.plain)
                .terminalControl()
                .accessibilityIdentifier("course-classroom-\(index)")

            if draft.schedules.count > 1 {
                TerminalFormDivider()
                Button(role: .destructive) {
                    draft.removeSchedule(id: identifier)
                } label: {
                    Text("删除这个安排")
                        .terminalControl()
                }
                .accessibilityIdentifier("delete-course-schedule-\(index)")
            }
        }
    }

    private var conflictMessage: String {
        let names = Array(Set(conflicts.map(\.existingCourse.name))).sorted()
        let weeks = Array(Set(conflicts.flatMap(\.weeks))).sorted()
        let weekDescription: String
        if weeks.count <= 6 {
            weekDescription = weeks.map(String.init).joined(separator: "、")
        } else {
            weekDescription = "\(weeks.first ?? 1)–\(weeks.last ?? 1)"
        }
        return "与 \(names.joined(separator: "、")) 在第 \(weekDescription) 周有时间重叠。冲突会被标记，但仍可保存。"
    }

    private func save() {
        let candidate = draft.course()
        switch draft.evaluateSave(
            semester: semester,
            existingCourses: existingCourses,
            calendar: calendar
        ) {
        case .invalid(let validationIssues):
            issues = validationIssues
        case .conflicting(let detectedConflicts):
            issues = []
            pendingCourse = candidate
            conflicts = detectedConflicts
            prompt = .conflict
        case .ready:
            issues = []
            if onSave(candidate) { dismiss() }
        }
    }

    private func persistPendingCourse() {
        guard let pendingCourse else { return }
        if onSave(pendingCourse) { dismiss() }
    }

    private func cancel() {
        if draft.isDirty {
            prompt = .discard
        } else {
            dismiss()
        }
    }

    private func deleteCourse() {
        guard let editingCourse else { return }
        if onDelete(editingCourse.id) { dismiss() }
    }

    @ViewBuilder
    private func terminalDialog(for prompt: CourseEditorPrompt) -> some View {
        switch prompt {
        case .conflict:
            TerminalDialog(
                code: "WARNING / CONFLICT",
                tag: "SCHEDULE COLLISION",
                title: "检测到课程冲突",
                message: conflictMessage,
                tone: .warning,
                accessibilityIdentifier: "course-conflict-dialog",
                primaryActionTitle: "仍然保存",
                primaryActionIdentifier: "course-conflict-save-anyway",
                primaryAction: {
                    self.prompt = nil
                    persistPendingCourse()
                },
                secondaryActionTitle: "返回修改",
                secondaryActionIdentifier: "course-conflict-return",
                secondaryAction: { self.prompt = nil }
            )
        case .discard:
            TerminalDialog(
                code: "WARNING / UNSAVED",
                tag: "DISCARD CHANGES",
                title: "放弃未保存的修改？",
                message: "当前编辑内容尚未保存。放弃后，本次修改不会保留。",
                tone: .danger,
                accessibilityIdentifier: "course-discard-dialog",
                primaryActionTitle: "放弃修改",
                primaryActionIdentifier: "course-discard-confirm",
                primaryAction: {
                    self.prompt = nil
                    dismiss()
                },
                secondaryActionTitle: "继续编辑",
                secondaryActionIdentifier: "course-discard-continue",
                secondaryAction: { self.prompt = nil }
            )
        case .delete:
            TerminalDialog(
                code: "DANGER / DELETE",
                tag: "IRREVERSIBLE",
                title: "删除这门课程？",
                message: "课程及其所有上课安排都会被删除，这项操作无法撤销。",
                tone: .danger,
                accessibilityIdentifier: "course-delete-dialog",
                primaryActionTitle: "确认删除",
                primaryActionIdentifier: "course-delete-confirm",
                primaryAction: {
                    self.prompt = nil
                    deleteCourse()
                },
                secondaryActionTitle: "取消",
                secondaryActionIdentifier: "course-delete-cancel",
                secondaryAction: { self.prompt = nil }
            )
        }
    }
}

private enum CourseEditorPrompt: Equatable {
    case conflict
    case discard
    case delete
}
