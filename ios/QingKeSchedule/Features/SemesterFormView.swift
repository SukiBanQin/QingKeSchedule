import SwiftUI

struct SemesterFormView: View {
    let isOnboarding: Bool
    let onSave: (SemesterDTO) -> Bool

    @State private var draft: SemesterDraft
    @State private var issues: [ScheduleValidationIssue] = []
    @State private var savedMessage: String?

    init(
        semester: SemesterDTO?,
        isOnboarding: Bool,
        now: Date = Date(),
        onSave: @escaping (SemesterDTO) -> Bool
    ) {
        self.isOnboarding = isOnboarding
        self.onSave = onSave
        _draft = State(initialValue: SemesterDraft(semester: semester, now: now))
    }

    var body: some View {
        Form {
            if isOnboarding {
                Section {
                    Label("欢迎使用青课", systemImage: "calendar.badge.clock")
                        .font(.title2.bold())
                        .accessibilityIdentifier("onboarding-title")
                    Text("先设置当前学期和每日节次，下一步再添加第一门课程。")
                        .foregroundStyle(.secondary)
                }
            }

            Section("学期信息") {
                TextField("学期名称", text: $draft.name)
                    .textInputAutocapitalization(.never)
                    .accessibilityIdentifier("semester-name")

                DatePicker(
                    "开始日期",
                    selection: $draft.startDate,
                    displayedComponents: .date
                )
                .accessibilityIdentifier("semester-start-date")

                Stepper("总周数：\(draft.totalWeeks)", value: $draft.totalWeeks, in: 1...52)
                    .accessibilityIdentifier("semester-total-weeks")
            }

            Section {
                ForEach($draft.periods) { $period in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("第 \(period.number) 节")
                                .font(.headline)
                            Spacer()
                            if draft.periods.count > 1 {
                                Button(role: .destructive) {
                                    draft.removePeriod(id: period.id)
                                } label: {
                                    Image(systemName: "trash")
                                }
                                .buttonStyle(.borderless)
                                .accessibilityLabel("删除第 \(period.number) 节")
                            }
                        }
                        HStack {
                            DatePicker(
                                "开始",
                                selection: $period.startTime,
                                displayedComponents: .hourAndMinute
                            )
                            DatePicker(
                                "结束",
                                selection: $period.endTime,
                                displayedComponents: .hourAndMinute
                            )
                        }
                    }
                    .padding(.vertical, 4)
                }

                Button {
                    draft.addPeriod()
                } label: {
                    Label("添加节次", systemImage: "plus")
                }
                .disabled(draft.periods.count >= 20)
                .accessibilityIdentifier("add-period")
            } header: {
                Text("每日节次")
            } footer: {
                Text("教学周从开始日期所在周的周一算起；课程统一使用这里的节次时间。")
            }

            if let issue = issues.first {
                Section {
                    Label(issue.message, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("semester-validation-error")
                }
            }

            if let savedMessage {
                Section {
                    Label(savedMessage, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                }
            }

            Section {
                Button(isOnboarding ? "创建课表" : "保存学期设置") {
                    save()
                }
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("semester-save")
            }
        }
        .navigationTitle(isOnboarding ? "首次设置" : "学期与节次")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(isOnboarding ? "继续" : "保存") {
                    save()
                }
                .accessibilityIdentifier("semester-save-toolbar")
            }
        }
    }

    private func save() {
        issues = draft.validationIssues()
        savedMessage = nil
        guard issues.isEmpty else { return }

        if onSave(draft.semester()), !isOnboarding {
            savedMessage = "设置已保存"
        }
    }
}
