import Foundation

struct ReminderSettings: Equatable, Sendable {
    static let allowedLeadMinutes = [0, 5, 10, 15, 30, 60]

    var remindersEnabled: Bool
    var reminderLeadMinutes: Int

    static let defaults = ReminderSettings(
        remindersEnabled: false,
        reminderLeadMinutes: 10
    )
}

@MainActor
protocol ReminderSettingsStore: AnyObject {
    func load() -> ReminderSettings
    func save(_ settings: ReminderSettings)
}

@MainActor
final class UserDefaultsReminderSettingsStore: ReminderSettingsStore {
    private enum Key {
        static let remindersEnabled = "remindersEnabled"
        static let reminderLeadMinutes = "reminderLeadMinutes"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> ReminderSettings {
        let enabled = defaults.object(forKey: Key.remindersEnabled) == nil
            ? ReminderSettings.defaults.remindersEnabled
            : defaults.bool(forKey: Key.remindersEnabled)
        let storedLeadMinutes = defaults.object(forKey: Key.reminderLeadMinutes) == nil
            ? ReminderSettings.defaults.reminderLeadMinutes
            : defaults.integer(forKey: Key.reminderLeadMinutes)
        let leadMinutes = ReminderSettings.allowedLeadMinutes.contains(storedLeadMinutes)
            ? storedLeadMinutes
            : ReminderSettings.defaults.reminderLeadMinutes
        return ReminderSettings(
            remindersEnabled: enabled,
            reminderLeadMinutes: leadMinutes
        )
    }

    func save(_ settings: ReminderSettings) {
        defaults.set(settings.remindersEnabled, forKey: Key.remindersEnabled)
        defaults.set(settings.reminderLeadMinutes, forKey: Key.reminderLeadMinutes)
    }
}

@MainActor
final class InMemoryReminderSettingsStore: ReminderSettingsStore {
    private var settings: ReminderSettings

    init(settings: ReminderSettings = .defaults) {
        self.settings = settings
    }

    func load() -> ReminderSettings { settings }

    func save(_ settings: ReminderSettings) {
        self.settings = settings
    }
}

actor InMemoryNotificationCenterClient: NotificationCenterClient {
    private var status: NotificationPermissionStatus
    private let authorizationResult: Bool
    private var requests: [PendingCourseNotification]

    init(
        status: NotificationPermissionStatus = .notDetermined,
        authorizationResult: Bool = true,
        requests: [PendingCourseNotification] = []
    ) {
        self.status = status
        self.authorizationResult = authorizationResult
        self.requests = requests
    }

    func authorizationStatus() async -> NotificationPermissionStatus { status }

    func requestAuthorization() async throws -> Bool {
        status = authorizationResult ? .authorized : .denied
        return authorizationResult
    }

    func pendingRequests() async -> [PendingCourseNotification] { requests }

    func add(_ request: CourseNotificationRequest, calendar: Calendar) async throws {
        requests.removeAll { $0.identifier == request.identifier }
        requests.append(
            PendingCourseNotification(
                identifier: request.identifier,
                title: request.title,
                body: request.body,
                fireDate: request.fireDate
            )
        )
    }

    func removePendingRequests(withIdentifiers identifiers: [String]) async {
        requests.removeAll { identifiers.contains($0.identifier) }
    }

    func snapshot() -> [PendingCourseNotification] { requests }
}
