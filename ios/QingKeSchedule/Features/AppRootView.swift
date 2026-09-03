import SwiftUI

struct AppRootView: View {
    @Bindable var state: ScheduleAppState

    var body: some View {
        Group {
            if !state.isLoaded {
                ProgressView("正在读取课表…")
            } else if state.needsOnboarding {
                NavigationStack {
                    SemesterFormView(
                        semester: nil,
                        isOnboarding: true,
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
}

private struct MainTabView: View {
    @Bindable var state: ScheduleAppState
    @State private var editorRoute: CourseEditorRoute?

    var body: some View {
        TabView {
            NavigationStack {
                if let semester = state.semester {
                    TodayScheduleView(
                        semester: semester,
                        courses: state.courses,
                        now: state.now,
                        calendar: state.calendar,
                        onAddCourse: { editorRoute = CourseEditorRoute(course: nil) },
                        onSelectCourse: { editorRoute = CourseEditorRoute(course: $0) }
                    )
                }
            }
            .tabItem { Label("今日", systemImage: "sun.max") }
            .accessibilityIdentifier("today-tab")

            NavigationStack {
                if let semester = state.semester {
                    WeekScheduleView(
                        semester: semester,
                        courses: state.courses,
                        now: state.now,
                        calendar: state.calendar,
                        onAddCourse: { editorRoute = CourseEditorRoute(course: nil) },
                        onSelectCourse: { editorRoute = CourseEditorRoute(course: $0) }
                    )
                }
            }
            .tabItem { Label("课表", systemImage: "calendar") }
            .accessibilityIdentifier("schedule-tab")

            NavigationStack {
                SemesterFormView(
                    semester: state.semester,
                    isOnboarding: false,
                    dataTransferState: state,
                    onSave: state.saveSemester
                )
            }
            .tabItem { Label("设置", systemImage: "gearshape") }
            .accessibilityIdentifier("settings-tab")
        }
        .sheet(item: $editorRoute) { route in
            if let semester = state.semester {
                NavigationStack {
                    CourseEditorView(
                        semester: semester,
                        existingCourses: state.courses,
                        course: route.course,
                        now: state.now,
                        calendar: state.calendar,
                        onSave: state.saveCourse,
                        onDelete: state.deleteCourse
                    )
                }
            }
        }
    }
}

private struct CourseEditorRoute: Identifiable {
    let id = UUID()
    let course: CourseDTO?
}
