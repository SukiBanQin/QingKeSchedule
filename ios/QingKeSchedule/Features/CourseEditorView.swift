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
        Form {
            Section("课程信息") {
                TextField("课程名称", text: $draft.name)
                    .accessibilityIdentifier("course-name")
                TextField("教师（选填）", text: $draft.teacher)
                    .accessibilityIdentifier("course-teacher")
                colorPicker
            }

            ForEach(Array(draft.schedules.indices), id: \.self) { index in
                scheduleSection(index: index)
            }

            Section {
                Button {
                    draft.addSchedule()
                } label: {
                    Label("添加上课安排", systemImage: "plus")
                }
                .accessibilityIdentifier("add-course-schedule")
            }

            if let issue = issues.first {
                Section {
                    Label(issue.message, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("course-validation-error")
                }
            }

            if editingCourse != nil {
                Section {
                    Button("删除课程", role: .destructive) {
                        showsDeleteConfirmation = true
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityIdentifier("course-delete")
                } footer: {
                    Text("删除后，这门课程的所有上课安排都会一并移除。")
                }
            }
        }
        .navigationTitle(editingCourse == nil ? "添加课程" : "编辑课程")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("取消") { cancel() }
                    .accessibilityIdentifier("course-cancel")
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("保存") { save() }
                    .accessibilityIdentifier("course-save")
            }
            if editingCourse != nil {
                ToolbarItem(placement: .bottomBar) {
                    Button("删除课程", role: .destructive) {
                        showsDeleteConfirmation = true
                    }
                    .accessibilityIdentifier("course-delete-toolbar")
                }
            }
        }
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

    private var colorPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("课程颜色")
                .font(.subheadline)
            HStack(spacing: 14) {
                ForEach(Self.palette, id: \.value) { option in
                    Button {
                        draft.color = option.value
                    } label: {
                        ZStack {
                            Circle()
                                .fill(Color(courseHex: option.value))
                                .frame(width: 34, height: 34)
                            if draft.color == option.value {
                                Image(systemName: "checkmark")
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(option.name)
                    .accessibilityAddTraits(draft.color == option.value ? .isSelected : [])
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private func scheduleSection(index: Int) -> some View {
        let identifier = draft.schedules[index].id
        return Section {
            Picker("星期", selection: $draft.schedules[index].dayOfWeek) {
                ForEach(1...7, id: \.self) { day in
                    Text(ScheduleDisplayText.weekdayNames[day - 1]).tag(day)
                }
            }
            .accessibilityIdentifier("course-weekday-\(index)")

            Picker("开始节次", selection: $draft.schedules[index].startPeriod) {
                ForEach(semester.periods, id: \.number) { period in
                    Text("第 \(period.number) 节 · \(period.startTime)").tag(period.number)
                }
            }
            .accessibilityIdentifier("course-start-period-\(index)")
            .onChange(of: draft.schedules[index].startPeriod) { _, newValue in
                if draft.schedules[index].endPeriod < newValue {
                    draft.schedules[index].endPeriod = newValue
                }
            }

            Picker("结束节次", selection: $draft.schedules[index].endPeriod) {
                ForEach(semester.periods, id: \.number) { period in
                    Text("第 \(period.number) 节 · \(period.endTime)").tag(period.number)
                }
            }
            .accessibilityIdentifier("course-end-period-\(index)")
            .onChange(of: draft.schedules[index].endPeriod) { _, newValue in
                if draft.schedules[index].startPeriod > newValue {
                    draft.schedules[index].startPeriod = newValue
                }
            }

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

            Picker("重复", selection: $draft.schedules[index].repeatRule) {
                Text("每周").tag(RepeatRule.every)
                Text("单周").tag(RepeatRule.odd)
                Text("双周").tag(RepeatRule.even)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("course-repeat-\(index)")

            TextField("教室（选填）", text: $draft.schedules[index].classroom)
                .accessibilityIdentifier("course-classroom-\(index)")

            if draft.schedules.count > 1 {
                Button("删除这个安排", role: .destructive) {
                    draft.removeSchedule(id: identifier)
                }
                .accessibilityIdentifier("delete-course-schedule-\(index)")
            }
        } header: {
            Text("上课安排 \(index + 1)")
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

    private static let palette: [(name: String, value: String)] = [
        ("青绿色", "#287B74"),
        ("珊瑚色", "#D96952"),
        ("靛蓝色", "#536FAF"),
        ("紫色", "#9A6AAF"),
        ("琥珀色", "#B87928"),
        ("绿色", "#46835A"),
    ]
}
