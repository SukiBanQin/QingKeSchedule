import SwiftUI

struct AppRootView: View {
    @Bindable var state: ScheduleAppState
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if !state.isLoaded {
                ZStack {
                    TerminalBackdrop()
                    VStack(spacing: 12) {
                        ProgressView()
                            .tint(QingKeTheme.cyan)
                        Text("正在读取课表…")
                            .font(.terminal(12, weight: .bold, relativeTo: .body))
                            .tracking(1)
                    }
                }
            } else if state.needsOnboarding {
                NavigationStack {
                    SemesterFormView(
                        semester: nil,
                        isOnboarding: true,
                        now: state.now,
                        dataTransferState: state,
                        onSave: state.saveSemester
                    )
                }
            } else {
                MainTabView(state: state)
            }
        }
        .task {
            if !state.isLoaded {
                state.load()
                prepareUITestImportIfRequested()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                state.appBecameActive()
            }
        }
        .alert(
            "无法完成操作",
            isPresented: Binding(
                get: { state.presentedError != nil },
                set: { isPresented in
                    if !isPresented { state.dismissError() }
                }
            )
        ) {
            Button("好") { state.dismissError() }
        } message: {
            Text(state.presentedError ?? "未知错误")
        }
    }

    private func prepareUITestImportIfRequested() {
        #if DEBUG
        guard
            ProcessInfo.processInfo.arguments.contains("--ui-testing-auto-import"),
            let raw = ProcessInfo.processInfo.environment["UI_TEST_IMPORT_JSON"],
            let contents = raw.data(using: .utf8)
        else { return }
        state.prepareImport(contents: contents)
        #endif
    }
}

private struct MainTabView: View {
    @Bindable var state: ScheduleAppState
    @State private var editorRoute: CourseEditorRoute?
    @State private var selectedTab = MainTab.today

    var body: some View {
        Group {
            switch selectedTab {
            case .today:
                if let semester = state.semester {
                    TodayScheduleView(
                        semester: semester,
                        courses: state.courses,
                        now: state.now,
                        academicCalendarSettings: state.academicCalendarSettings,
                        calendar: state.calendar,
                        onAddCourse: presentCourseCreation,
                        onSelectCourse: {
                            editorRoute = CourseEditorRoute.editor(course: $0)
                        }
                    )
                }
            case .week:
                if let semester = state.semester {
                    WeekScheduleView(
                        semester: semester,
                        courses: state.courses,
                        now: state.now,
                        academicCalendarSettings: state.academicCalendarSettings,
                        calendar: state.calendar,
                        onAddCourse: presentCourseCreation,
                        onSelectCourse: {
                            editorRoute = CourseEditorRoute.editor(course: $0)
                        }
                    )
                }
            case .settings:
                SemesterFormView(
                    semester: state.semester,
                    isOnboarding: false,
                    now: state.now,
                    dataTransferState: state,
                    onSave: state.saveSemester
                )
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            TerminalTabBar(selection: $selectedTab)
        }
        .fullScreenCover(item: $editorRoute) { route in
            if route.showsChooser {
                if let semester = state.semester {
                    CourseAddChoiceView(
                        semester: semester,
                        courses: state.courses,
                        now: state.now,
                        calendar: state.calendar,
                        onCancel: { editorRoute = nil },
                        onSave: state.saveCourse,
                        onDelete: state.deleteCourse
                    )
                }
            } else if let semester = state.semester {
                CourseEditorView(
                    semester: semester,
                    existingCourses: state.courses,
                    course: route.course,
                    appendingScheduleOnly: route.appendingScheduleOnly,
                    now: state.now,
                    calendar: state.calendar,
                    onSave: state.saveCourse,
                    onDelete: state.deleteCourse
                )
            }
        }
    }

    private func presentCourseCreation() {
        editorRoute = state.courses.isEmpty ? .editor(course: nil) : .chooser()
    }

}

private enum MainTab: String, CaseIterable, Identifiable {
    case today
    case week
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .today: "今日"
        case .week: "课表"
        case .settings: "设置"
        }
    }

    var index: String {
        switch self {
        case .today: "01"
        case .week: "02"
        case .settings: "03"
        }
    }

    var symbol: String {
        switch self {
        case .today: "square.grid.2x2"
        case .week: "calendar"
        case .settings: "slider.horizontal.3"
        }
    }

    var accessibilityIdentifier: String {
        switch self {
        case .today: "today-tab"
        case .week: "schedule-tab"
        case .settings: "settings-tab"
        }
    }
}

private struct TerminalTabBar: View {
    @Binding var selection: MainTab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(MainTab.allCases) { tab in
                Button {
                    selection = tab
                } label: {
                    HStack(spacing: 9) {
                        Image(systemName: tab.symbol)
                            .font(.system(size: 18, weight: .medium))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(tab.title)
                                .font(.headline)
                            Text(tab.index)
                                .font(.terminal(8, weight: .black, relativeTo: .caption2))
                                .tracking(1)
                                .foregroundStyle(
                                    selection == tab ? QingKeTheme.signal : .secondary
                                )
                        }
                    }
                    .foregroundStyle(selection == tab ? Color.white : Color.primary)
                    .frame(maxWidth: .infinity, minHeight: 62)
                    .background(selection == tab ? QingKeTheme.ink.opacity(0.94) : .clear)
                    .overlay(alignment: .bottom) {
                        Rectangle()
                            .fill(selection == tab ? QingKeTheme.signal : .clear)
                            .frame(width: 30, height: 4)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tab.title)
                .accessibilityAddTraits(selection == tab ? .isSelected : [])
                .accessibilityIdentifier(tab.accessibilityIdentifier)
            }
        }
        .padding(6)
        .background { TerminalAcrylicSurface() }
        .overlay {
            Rectangle().stroke(Color.white.opacity(0.75), lineWidth: 1)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .background(.ultraThinMaterial)
        .accessibilityElement(children: .contain)
    }
}

private struct CourseEditorRoute: Identifiable {
    let id = UUID()
    let course: CourseDTO?
    let appendingScheduleOnly: Bool
    let showsChooser: Bool

    static func chooser() -> Self {
        Self(course: nil, appendingScheduleOnly: false, showsChooser: true)
    }

    static func editor(course: CourseDTO?) -> Self {
        Self(course: course, appendingScheduleOnly: false, showsChooser: false)
    }

    static func appendSchedule(to course: CourseDTO) -> Self {
        Self(course: course, appendingScheduleOnly: true, showsChooser: false)
    }
}

private struct CourseAddChoiceView: View {
    let semester: SemesterDTO
    let courses: [CourseDTO]
    let now: Date
    let calendar: Calendar
    let onCancel: () -> Void
    let onSave: (CourseDTO) -> Bool
    let onDelete: (String) -> Bool

    @State private var selection: CourseAddSelection?

    var body: some View {
        if let selection {
            CourseEditorView(
                semester: semester,
                existingCourses: courses,
                course: selection.course,
                appendingScheduleOnly: selection.appendingScheduleOnly,
                now: now,
                calendar: calendar,
                onSave: onSave,
                onDelete: onDelete
            )
        } else {
            choiceContent
        }
    }

    private var choiceContent: some View {
        ZStack {
            TerminalBackdrop()

            VStack(spacing: 0) {
                HStack {
                    Button("取消", action: onCancel)
                        .font(.headline)
                    Spacer()
                    VStack(spacing: 1) {
                        Text("添加课程")
                            .font(.headline)
                        Text("SELECT PROFILE")
                            .font(.terminal(8, weight: .black, relativeTo: .caption2))
                            .tracking(1)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Color.clear.frame(width: 34, height: 1)
                }
                .padding(.horizontal, 20)
                .frame(height: 58)
                .foregroundStyle(.white)
                .background(QingKeTheme.ink.opacity(0.96))
                .overlay(alignment: .bottom) {
                    Rectangle().fill(QingKeTheme.signal).frame(height: 3)
                }

                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        TerminalBrandHeader(code: "PROFILE / 04")
                        TerminalFormSection(
                            index: "01",
                            title: "创建方式",
                            detail: "COURSE DATA",
                            footer: "已有课程会复用名称、教师和识别色，只新增一条上课安排。"
                        ) {
                            Label("新建一门课程", systemImage: "plus.square")
                                .terminalControl()
                                .onTapGesture {
                                    selection = .newCourse
                                }
                                .accessibilityElement(children: .combine)
                                .accessibilityAddTraits(.isButton)
                                .accessibilityAction {
                                    selection = .newCourse
                                }
                                .accessibilityIdentifier("add-new-course")
                        }

                        TerminalFormSection(
                            index: "02",
                            title: "已有课程",
                            detail: "REUSE / \(courses.count)"
                        ) {
                            ForEach(Array(courses.sorted { $0.name < $1.name }.enumerated()), id: \.element.id) { index, course in
                                if index > 0 { TerminalFormDivider() }
                                HStack(spacing: 12) {
                                    Rectangle()
                                        .fill(Color(courseHex: course.color))
                                        .frame(width: 6, height: 42)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(course.name)
                                            .font(.headline)
                                        Text(course.teacher.isEmpty ? "未填写教师" : course.teacher)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text("添加安排")
                                        .font(.terminal(9, weight: .black, relativeTo: .caption2))
                                        .foregroundStyle(QingKeTheme.cyan)
                                }
                                .terminalControl()
                                .onTapGesture {
                                    selection = .append(course)
                                }
                                .accessibilityElement(children: .combine)
                                .accessibilityAddTraits(.isButton)
                                .accessibilityLabel("为 \(course.name) 添加上课安排")
                                .accessibilityAction {
                                    selection = .append(course)
                                }
                                .accessibilityIdentifier("reuse-course-\(course.id)")
                            }
                        }
                    }
                    .padding(20)
                }
            }
        }
        .tint(QingKeTheme.cyan)
    }
}

private enum CourseAddSelection {
    case newCourse
    case append(CourseDTO)

    var course: CourseDTO? {
        switch self {
        case .newCourse: nil
        case .append(let course): course
        }
    }

    var appendingScheduleOnly: Bool {
        switch self {
        case .newCourse: false
        case .append: true
        }
    }
}
