import SwiftData
import SwiftUI

@main
struct QingKeScheduleApp: App {
    private let container: ModelContainer
    @State private var state: ScheduleAppState

    @MainActor
    init() {
        do {
            let inMemory = ProcessInfo.processInfo.arguments.contains("--ui-testing")
            let container = try SwiftDataScheduleRepository.makeContainer(inMemory: inMemory)
            let repository = SwiftDataScheduleRepository(context: ModelContext(container))
            self.container = container
            _state = State(initialValue: ScheduleAppState(repository: repository))
        } catch {
            fatalError("无法初始化本地课表：\(error.localizedDescription)")
        }
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(state: state)
                .modelContainer(container)
        }
    }
}
