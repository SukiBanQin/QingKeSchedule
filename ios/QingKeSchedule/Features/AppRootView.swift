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

    var body: some View {
        TabView {
            NavigationStack {
                ContentUnavailableView(
                    "添加第一门课程",
                    systemImage: "book.closed",
                    description: Text("学期已经准备好，接下来添加课程安排。")
                )
                .navigationTitle("今日")
            }
            .tabItem { Label("今日", systemImage: "sun.max") }
            .accessibilityIdentifier("today-tab")

            NavigationStack {
                ContentUnavailableView(
                    "还没有课程",
                    systemImage: "calendar",
                    description: Text("添加课程后即可按教学周查看。")
                )
                .navigationTitle("课表")
            }
            .tabItem { Label("课表", systemImage: "calendar") }
            .accessibilityIdentifier("schedule-tab")

            NavigationStack {
                SemesterFormView(
                    semester: state.semester,
                    isOnboarding: false,
                    onSave: state.saveSemester
                )
            }
            .tabItem { Label("设置", systemImage: "gearshape") }
            .accessibilityIdentifier("settings-tab")
        }
    }
}
