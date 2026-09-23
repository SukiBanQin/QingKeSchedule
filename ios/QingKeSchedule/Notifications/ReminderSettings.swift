import Foundation

struct ReminderSettings: Equatable, Sendable {
    static let presetLeadMinutes = [0, 5, 10, 15, 30]
    static let validLeadMinutes = 0...180

    var remindersEnabled: Bool
    var reminderLeadMinutes: Int
    var usesCustomLeadTime: Bool = false

    static let defaults = ReminderSettings(
        remindersEnabled: false,
        reminderLeadMinutes: 10,
        usesCustomLeadTime: false
    )

    static func isValidLeadMinutes(_ minutes: Int) -> Bool {
        validLeadMinutes.contains(minutes)
    }
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
        static let usesCustomLeadTime = "usesCustomLeadTime"
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
        let leadMinutes = ReminderSettings.isValidLeadMinutes(storedLeadMinutes)
            ? storedLeadMinutes
            : ReminderSettings.defaults.reminderLeadMinutes
        let usesCustomLeadTime: Bool
        if !ReminderSettings.isValidLeadMinutes(storedLeadMinutes) {
            usesCustomLeadTime = ReminderSettings.defaults.usesCustomLeadTime
        } else if defaults.object(forKey: Key.usesCustomLeadTime) == nil {
            usesCustomLeadTime = !ReminderSettings.presetLeadMinutes.contains(leadMinutes)
        } else {
            usesCustomLeadTime = defaults.bool(forKey: Key.usesCustomLeadTime)
        }
        return ReminderSettings(
            remindersEnabled: enabled,
            reminderLeadMinutes: leadMinutes,
            usesCustomLeadTime: usesCustomLeadTime
        )
    }

    func save(_ settings: ReminderSettings) {
        defaults.set(settings.remindersEnabled, forKey: Key.remindersEnabled)
        defaults.set(settings.reminderLeadMinutes, forKey: Key.reminderLeadMinutes)
        defaults.set(settings.usesCustomLeadTime, forKey: Key.usesCustomLeadTime)
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

enum AcademicDayResolution: Equatable, Sendable {
    case teaching(sourceDayOfWeek: Int, isMakeup: Bool)
    case nonTeaching(reason: String)
}

struct MakeupTeachingDay: Codable, Equatable, Hashable, Identifiable, Sendable {
    var date: String
    var followsDayOfWeek: Int

    var id: String { date }
}

struct ScheduleBreakSettings: Codable, Equatable, Sendable {
    var isEnabled: Bool
    var title: String
    var startTime: String
    var endTime: String

    static let defaultLunch = ScheduleBreakSettings(
        isEnabled: true,
        title: "午休",
        startTime: "11:40",
        endTime: "14:00"
    )

    var isValid: Bool {
        guard
            let start = ScheduleRules.minutes(from: startTime),
            let end = ScheduleRules.minutes(from: endTime)
        else {
            return false
        }
        return start < end
    }

    func sanitized() -> ScheduleBreakSettings {
        guard isValid else {
            var fallback = Self.defaultLunch
            fallback.isEnabled = isEnabled
            return fallback
        }
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return ScheduleBreakSettings(
            isEnabled: isEnabled,
            title: trimmedTitle.isEmpty ? Self.defaultLunch.title : trimmedTitle,
            startTime: startTime,
            endTime: endTime
        )
    }
}

struct AcademicCalendarSettings: Codable, Equatable, Sendable {
    var weekendsAreNonTeachingDays: Bool
    var nonTeachingDates: [String]
    var makeupTeachingDays: [MakeupTeachingDay]
    var lunchBreak: ScheduleBreakSettings

    init(
        weekendsAreNonTeachingDays: Bool,
        nonTeachingDates: [String],
        makeupTeachingDays: [MakeupTeachingDay],
        lunchBreak: ScheduleBreakSettings = .defaultLunch
    ) {
        self.weekendsAreNonTeachingDays = weekendsAreNonTeachingDays
        self.nonTeachingDates = nonTeachingDates
        self.makeupTeachingDays = makeupTeachingDays
        self.lunchBreak = lunchBreak
    }

    static let defaults = AcademicCalendarSettings(
        weekendsAreNonTeachingDays: false,
        nonTeachingDates: [],
        makeupTeachingDays: [],
        lunchBreak: .defaultLunch
    )

    func resolution(for date: Date, calendar: Calendar) -> AcademicDayResolution {
        let dateString = Self.dateString(from: date, calendar: calendar)
        if nonTeachingDates.contains(dateString) {
            return .nonTeaching(reason: "已设为停课日")
        }
        if let makeup = makeupTeachingDays.first(where: { $0.date == dateString }) {
            return .teaching(sourceDayOfWeek: makeup.followsDayOfWeek, isMakeup: true)
        }

        let dayOfWeek = Self.dayOfWeek(for: date, calendar: calendar)
        if weekendsAreNonTeachingDays, dayOfWeek >= 6 {
            return .nonTeaching(reason: "周末默认停课")
        }
        return .teaching(sourceDayOfWeek: dayOfWeek, isMakeup: false)
    }

    mutating func setNonTeaching(_ date: Date, calendar: Calendar) {
        let dateString = Self.dateString(from: date, calendar: calendar)
        guard ScheduleRules.localDate(from: dateString, calendar: calendar) != nil else { return }
        makeupTeachingDays.removeAll { $0.date == dateString }
        if !nonTeachingDates.contains(dateString) {
            nonTeachingDates.append(dateString)
            nonTeachingDates.sort()
        }
    }

    mutating func removeNonTeachingDate(_ dateString: String) {
        nonTeachingDates.removeAll { $0 == dateString }
    }

    mutating func setMakeupTeachingDay(
        _ date: Date,
        followsDayOfWeek: Int,
        calendar: Calendar
    ) {
        guard (1...7).contains(followsDayOfWeek) else { return }
        let dateString = Self.dateString(from: date, calendar: calendar)
        guard ScheduleRules.localDate(from: dateString, calendar: calendar) != nil else { return }
        nonTeachingDates.removeAll { $0 == dateString }
        makeupTeachingDays.removeAll { $0.date == dateString }
        makeupTeachingDays.append(MakeupTeachingDay(
            date: dateString,
            followsDayOfWeek: followsDayOfWeek
        ))
        makeupTeachingDays.sort { $0.date < $1.date }
    }

    mutating func removeMakeupTeachingDay(_ dateString: String) {
        makeupTeachingDays.removeAll { $0.date == dateString }
    }

    func sanitized(calendar: Calendar) -> AcademicCalendarSettings {
        let validNonTeachingDates = Set(nonTeachingDates.filter {
            ScheduleRules.localDate(from: $0, calendar: calendar) != nil
        })
        var validMakeupByDate: [String: MakeupTeachingDay] = [:]
        for day in makeupTeachingDays where
            ScheduleRules.localDate(from: day.date, calendar: calendar) != nil
                && (1...7).contains(day.followsDayOfWeek)
                && !validNonTeachingDates.contains(day.date) {
            validMakeupByDate[day.date] = day
        }
        return AcademicCalendarSettings(
            weekendsAreNonTeachingDays: weekendsAreNonTeachingDays,
            nonTeachingDates: validNonTeachingDates.sorted(),
            makeupTeachingDays: validMakeupByDate.values.sorted { $0.date < $1.date },
            lunchBreak: lunchBreak.sanitized()
        )
    }

    private enum CodingKeys: String, CodingKey {
        case weekendsAreNonTeachingDays
        case nonTeachingDates
        case makeupTeachingDays
        case lunchBreak
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        weekendsAreNonTeachingDays = try container.decodeIfPresent(
            Bool.self,
            forKey: .weekendsAreNonTeachingDays
        ) ?? Self.defaults.weekendsAreNonTeachingDays
        nonTeachingDates = try container.decodeIfPresent(
            [String].self,
            forKey: .nonTeachingDates
        ) ?? []
        makeupTeachingDays = try container.decodeIfPresent(
            [MakeupTeachingDay].self,
            forKey: .makeupTeachingDays
        ) ?? []
        lunchBreak = try container.decodeIfPresent(
            ScheduleBreakSettings.self,
            forKey: .lunchBreak
        ) ?? .defaultLunch
    }

    static func dateString(from date: Date, calendar: Calendar) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(
            format: "%04d-%02d-%02d",
            components.year ?? 0,
            components.month ?? 0,
            components.day ?? 0
        )
    }

    private static func dayOfWeek(for date: Date, calendar: Calendar) -> Int {
        let sundayBasedWeekday = calendar.component(.weekday, from: date)
        return sundayBasedWeekday == 1 ? 7 : sundayBasedWeekday - 1
    }
}

@MainActor
protocol AcademicCalendarSettingsStore: AnyObject {
    func load() -> AcademicCalendarSettings
    func save(_ settings: AcademicCalendarSettings)
}

@MainActor
final class UserDefaultsAcademicCalendarSettingsStore: AcademicCalendarSettingsStore {
    private static let key = "academicCalendarSettings"

    private let defaults: UserDefaults
    private let calendar: Calendar

    init(
        defaults: UserDefaults = .standard,
        calendar: Calendar = ScheduleRules.gregorianCalendar()
    ) {
        self.defaults = defaults
        self.calendar = calendar
    }

    func load() -> AcademicCalendarSettings {
        guard
            let data = defaults.data(forKey: Self.key),
            let decoded = try? JSONDecoder().decode(AcademicCalendarSettings.self, from: data)
        else {
            return .defaults
        }
        return decoded.sanitized(calendar: calendar)
    }

    func save(_ settings: AcademicCalendarSettings) {
        let sanitized = settings.sanitized(calendar: calendar)
        if let data = try? JSONEncoder().encode(sanitized) {
            defaults.set(data, forKey: Self.key)
        }
    }
}

@MainActor
final class InMemoryAcademicCalendarSettingsStore: AcademicCalendarSettingsStore {
    private var settings: AcademicCalendarSettings

    init(settings: AcademicCalendarSettings = .defaults) {
        self.settings = settings
    }

    func load() -> AcademicCalendarSettings { settings }

    func save(_ settings: AcademicCalendarSettings) {
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
