import SwiftUI

struct CourseEditorView: View {
    let semester: SemesterDTO
    let existingCourses: [CourseDTO]
    let editingCourse: CourseDTO?
    let calendar: Calendar
    let onSave: (CourseDTO) -> Bool
    let onDelete: (String) -> Bool

    @Environment(\.dismiss) private var dismiss
    @State private var draft: CourseDraft
    @State private var issues: [ScheduleValidationIssue] = []
    @State private var pendingCourse: CourseDTO?
    @State private var conflicts: [ScheduleConflictDTO] = []
    @State private var showsConflictConfirmation = false
    @State private var showsDeleteConfirmation = false
    @State private var showsDiscardConfirmation = false

    init(
        semester: SemesterDTO,
        existingCourses: [CourseDTO],
        course: CourseDTO?,
        now: Date,
        calendar: Calendar,
        onSave: @escaping (CourseDTO) -> Bool,
        onDelete: @escaping (String) -> Bool
    ) {
        self.semester = semester
        self.existingCourses = existingCourses
        editingCourse = course
        self.calendar = calendar
        self.onSave = onSave
        self.onDelete = onDelete
        _draft = State(initialValue: CourseDraft(
            course: course,
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

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        TerminalBrandHeader(
                            code: editingCourse == nil ? "CREATE / 04" : "EDIT / 04"
                        )

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

                        ForEach(Array(draft.schedules.indices), id: \.self) { index in
                            scheduleSection(index: index)
                        }

                        Button {
                            draft.addSchedule()
                        } label: {
                            Label("添加上课安排", systemImage: "plus")
                                .font(.headline)
                                .foregroundStyle(QingKeTheme.ink)
                                .frame(maxWidth: .infinity, minHeight: 54)
                                .background(QingKeTheme.signal)
                                .overlay {
                                    Rectangle().stroke(Color.white.opacity(0.78), lineWidth: 1)
                                }
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

                        if editingCourse != nil {
                            TerminalFormSection(
                                index: "99",
                                title: "危险操作",
                                footer: "删除后，这门课程的所有上课安排都会一并移除。"
                            ) {
                                Button("删除课程", role: .destructive) {
                                    showsDeleteConfirmation = true
                                }
                                .frame(maxWidth: .infinity, minHeight: 52)
                                .accessibilityIdentifier("course-delete")
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 14)
                    .padding(.bottom, 30)
                }
            }
        }
        .tint(QingKeTheme.cyan)
        .interactiveDismissDisabled(draft.isDirty)
        .confirmationDialog(
            "检测到课程冲突",
            isPresented: $showsConflictConfirmation,
            titleVisibility: .visible
        ) {
            Button("仍然保存") { persistPendingCourse() }
            Button("返回修改") { showsConflictConfirmation = false }
        } message: {
            Text(conflictMessage)
        }
        .confirmationDialog(
            "放弃未保存的修改？",
            isPresented: $showsDiscardConfirmation,
            titleVisibility: .visible
        ) {
            Button("放弃修改", role: .destructive) { dismiss() }
            Button("继续编辑", role: .cancel) {}
        }
        .alert("删除这门课程？", isPresented: $showsDeleteConfirmation) {
            Button("确认删除", role: .destructive) { deleteCourse() }
            Button("取消", role: .cancel) {}
        } message: {
            Text("课程及其所有上课安排都会被删除，这项操作无法撤销。")
        }
    }

    private var editorHeader: some View {
        HStack(spacing: 14) {
            Button("取消") { cancel() }
                .font(.headline)
                .accessibilityIdentifier("course-cancel")

            Spacer()

            VStack(spacing: 1) {
                Text(editingCourse == nil ? "添加课程" : "编辑课程")
                    .font(.headline)
                Text(editingCourse == nil ? "NEW COURSE" : "COURSE PROFILE")
                    .font(.terminal(8, weight: .black, relativeTo: .caption2))
                    .tracking(1)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Button("保存") { save() }
                .font(.headline)
                .foregroundStyle(QingKeTheme.signal)
                .accessibilityIdentifier("course-save")
        }
        .padding(.horizontal, 20)
        .frame(height: 58)
        .background(QingKeTheme.ink.opacity(0.96))
        .foregroundStyle(.white)
        .overlay(alignment: .bottom) {
            Rectangle().fill(QingKeTheme.signal).frame(height: 3)
        }
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
                                    .foregroundStyle(.white)
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
                .foregroundStyle(.secondary)
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
                Button("删除这个安排", role: .destructive) {
                    draft.removeSchedule(id: identifier)
                }
                .terminalControl()
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
            showsConflictConfirmation = true
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
            showsDiscardConfirmation = true
        } else {
            dismiss()
        }
    }

    private func deleteCourse() {
        guard let editingCourse else { return }
        if onDelete(editingCourse.id) { dismiss() }
    }
}
