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
            let reminderSettingsStore: any ReminderSettingsStore
            let notificationClient: any NotificationCenterClient
            if inMemory {
                let notificationsDenied = ProcessInfo.processInfo.arguments.contains(
                    "--ui-testing-notifications-denied"
                )
                let remindersEnabled = ProcessInfo.processInfo.arguments.contains(
                    "--ui-testing-reminders-enabled"
                )
                reminderSettingsStore = InMemoryReminderSettingsStore(settings: ReminderSettings(
                    remindersEnabled: remindersEnabled,
                    reminderLeadMinutes: ReminderSettings.defaults.reminderLeadMinutes
                ))
                notificationClient = InMemoryNotificationCenterClient(
                    status: notificationsDenied ? .denied : .authorized,
                    authorizationResult: !notificationsDenied
                )
            } else {
                reminderSettingsStore = UserDefaultsReminderSettingsStore()
                notificationClient = UserNotificationCenterClient()
            }
            let notificationCoordinator = NotificationCoordinator(client: notificationClient)
            self.container = container
            _state = State(initialValue: ScheduleAppState(
                repository: repository,
                reminderSettingsStore: reminderSettingsStore,
                notificationCoordinator: notificationCoordinator
            ))
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
