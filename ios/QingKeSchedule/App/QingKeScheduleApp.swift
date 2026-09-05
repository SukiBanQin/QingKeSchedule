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
            let academicCalendarSettingsStore: any AcademicCalendarSettingsStore
            let appearanceSettingsStore: any AppearanceSettingsStore
            let notificationClient: any NotificationCenterClient
            let nowProvider: () -> Date
            if inMemory {
                let notificationsDenied = ProcessInfo.processInfo.arguments.contains(
                    "--ui-testing-notifications-denied"
                )
                let remindersEnabled = ProcessInfo.processInfo.arguments.contains(
                    "--ui-testing-reminders-enabled"
                )
                let usesCustomReminder = ProcessInfo.processInfo.arguments.contains(
                    "--ui-testing-custom-reminder"
                )
                reminderSettingsStore = InMemoryReminderSettingsStore(settings: ReminderSettings(
                    remindersEnabled: remindersEnabled,
                    reminderLeadMinutes: usesCustomReminder
                        ? 15
                        : ReminderSettings.defaults.reminderLeadMinutes,
                    usesCustomLeadTime: usesCustomReminder
                ))
                academicCalendarSettingsStore = InMemoryAcademicCalendarSettingsStore()
                appearanceSettingsStore = InMemoryAppearanceSettingsStore()
                notificationClient = InMemoryNotificationCenterClient(
                    status: notificationsDenied ? .denied : .authorized,
                    authorizationResult: !notificationsDenied
                )
                let calendar = ScheduleRules.gregorianCalendar()
                let fixedDate = ScheduleRules.localDate(
                    from: "2026-09-04",
                    calendar: calendar
                ) ?? Date(timeIntervalSince1970: 1_788_451_200)
                nowProvider = { fixedDate }
            } else {
                reminderSettingsStore = UserDefaultsReminderSettingsStore()
                academicCalendarSettingsStore = UserDefaultsAcademicCalendarSettingsStore()
                appearanceSettingsStore = UserDefaultsAppearanceSettingsStore()
                notificationClient = UserNotificationCenterClient()
                nowProvider = { Date() }
            }
            let notificationCoordinator = NotificationCoordinator(client: notificationClient)
            self.container = container
            _state = State(initialValue: ScheduleAppState(
                repository: repository,
                now: nowProvider,
                reminderSettingsStore: reminderSettingsStore,
                academicCalendarSettingsStore: academicCalendarSettingsStore,
                appearanceSettingsStore: appearanceSettingsStore,
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
                .preferredColorScheme(state.appearanceMode.preferredColorScheme)
        }
    }
}

private extension AppearanceMode {
    var preferredColorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}
