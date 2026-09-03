import Foundation
import UserNotifications

enum NotificationPermissionStatus: String, Equatable, Sendable {
    case notDetermined
    case denied
    case authorized
}

struct CourseNotificationRequest: Equatable, Sendable {
    let identifier: String
    let title: String
    let body: String
    let fireDate: Date
}

struct PendingCourseNotification: Equatable, Sendable {
    let identifier: String
    let title: String
    let body: String
    let fireDate: Date?

    func matches(_ desired: CourseNotificationRequest) -> Bool {
        identifier == desired.identifier
            && title == desired.title
            && body == desired.body
            && fireDate == desired.fireDate
    }
}

struct NotificationReconciliation: Equatable, Sendable {
    let permissionStatus: NotificationPermissionStatus
    let desiredCount: Int
    let removedIdentifiers: [String]
    let addedIdentifiers: [String]
}

protocol NotificationCenterClient: Sendable {
    func authorizationStatus() async -> NotificationPermissionStatus
    func requestAuthorization() async throws -> Bool
    func pendingRequests() async -> [PendingCourseNotification]
    func add(_ request: CourseNotificationRequest, calendar: Calendar) async throws
    func removePendingRequests(withIdentifiers identifiers: [String]) async
}

protocol NotificationCoordinating: Sendable {
    func authorizationStatus() async -> NotificationPermissionStatus
    func requestAuthorization() async throws -> Bool
    func reconcile(
        data: ScheduleDataDTO,
        remindersEnabled: Bool,
        leadMinutes: Int,
        now: Date,
        calendar: Calendar
    ) async throws -> NotificationReconciliation
}

enum CourseNotificationPlanner {
    static let identifierPrefix = "schedule."

    static func identifier(
        courseID: String,
        scheduleID: String,
        teachingWeek: Int
    ) -> String {
        "\(identifierPrefix)\(courseID).\(scheduleID).week.\(teachingWeek)"
    }

    static func requests(
        data: ScheduleDataDTO,
        leadMinutes: Int,
        after now: Date,
        limit: Int,
        calendar: Calendar
    ) -> [CourseNotificationRequest] {
        guard let semester = data.semester, limit > 0 else { return [] }
        let safeLeadMinutes = max(0, leadMinutes)
        var requests: [CourseNotificationRequest] = []

        for course in data.courses {
            for schedule in course.schedules {
                guard
                    let startPeriod = semester.periods.first(where: {
                        $0.number == schedule.startPeriod
                    }),
                    let endPeriod = semester.periods.first(where: {
                        $0.number == schedule.endPeriod
                    }),
                    let startMinutes = ScheduleRules.minutes(from: startPeriod.startTime)
                else {
                    continue
                }

                for week in schedule.startWeek...schedule.endWeek
                where ScheduleRules.scheduleApplies(schedule, inWeek: week) {
                    guard
                        let courseDate = ScheduleRules.date(
                            forTeachingWeek: week,
                            dayOfWeek: schedule.dayOfWeek,
                            semester: semester,
                            calendar: calendar
                        ),
                        let startDate = date(
                            courseDate,
                            atMinutesAfterMidnight: startMinutes,
                            calendar: calendar
                        ),
                        let fireDate = calendar.date(
                            byAdding: .minute,
                            value: -safeLeadMinutes,
                            to: startDate
                        ),
                        fireDate > now
                    else {
                        continue
                    }

                    let timeRange = "\(startPeriod.startTime)–\(endPeriod.endTime)"
                    let classroom = schedule.classroom.trimmingCharacters(
                        in: .whitespacesAndNewlines
                    )
                    let body = classroom.isEmpty
                        ? timeRange
                        : "\(timeRange) · \(classroom)"
                    requests.append(
                        CourseNotificationRequest(
                            identifier: identifier(
                                courseID: course.id,
                                scheduleID: schedule.id,
                                teachingWeek: week
                            ),
                            title: course.name,
                            body: body,
                            fireDate: fireDate
                        )
                    )
                }
            }
        }

        return requests.sorted {
            if $0.fireDate != $1.fireDate { return $0.fireDate < $1.fireDate }
            return $0.identifier < $1.identifier
        }.prefix(limit).map { $0 }
    }

    private static func date(
        _ day: Date,
        atMinutesAfterMidnight minutes: Int,
        calendar: Calendar
    ) -> Date? {
        var components = calendar.dateComponents([.year, .month, .day], from: day)
        components.calendar = calendar
        components.timeZone = calendar.timeZone
        components.hour = minutes / 60
        components.minute = minutes % 60
        components.second = 0
        return calendar.date(from: components)
    }
}

actor NotificationCoordinator: NotificationCoordinating {
    static let defaultMaximumPending = 60

    private let client: any NotificationCenterClient
    private let maximumPending: Int

    init(
        client: any NotificationCenterClient,
        maximumPending: Int = NotificationCoordinator.defaultMaximumPending
    ) {
        self.client = client
        self.maximumPending = maximumPending
    }

    func authorizationStatus() async -> NotificationPermissionStatus {
        await client.authorizationStatus()
    }

    func requestAuthorization() async throws -> Bool {
        try await client.requestAuthorization()
    }

    func reconcile(
        data: ScheduleDataDTO,
        remindersEnabled: Bool,
        leadMinutes: Int,
        now: Date,
        calendar: Calendar
    ) async throws -> NotificationReconciliation {
        let status = await client.authorizationStatus()
        let pending = await client.pendingRequests()
        let managedPending = pending.filter {
            $0.identifier.hasPrefix(CourseNotificationPlanner.identifierPrefix)
        }

        guard remindersEnabled, status == .authorized else {
            let removals = managedPending.map(\.identifier).sorted()
            if !removals.isEmpty {
                await client.removePendingRequests(withIdentifiers: removals)
            }
            return NotificationReconciliation(
                permissionStatus: status,
                desiredCount: 0,
                removedIdentifiers: removals,
                addedIdentifiers: []
            )
        }

        let desired = CourseNotificationPlanner.requests(
            data: data,
            leadMinutes: leadMinutes,
            after: now,
            limit: maximumPending,
            calendar: calendar
        )
        let desiredByIdentifier = Dictionary(uniqueKeysWithValues: desired.map {
            ($0.identifier, $0)
        })
        let removals = managedPending.compactMap { pendingRequest -> String? in
            guard let desiredRequest = desiredByIdentifier[pendingRequest.identifier] else {
                return pendingRequest.identifier
            }
            return pendingRequest.matches(desiredRequest) ? nil : pendingRequest.identifier
        }.sorted()
        if !removals.isEmpty {
            await client.removePendingRequests(withIdentifiers: removals)
        }

        let removedSet = Set(removals)
        let unchangedIdentifiers = Set(managedPending.compactMap { pendingRequest -> String? in
            guard
                !removedSet.contains(pendingRequest.identifier),
                let desiredRequest = desiredByIdentifier[pendingRequest.identifier],
                pendingRequest.matches(desiredRequest)
            else {
                return nil
            }
            return pendingRequest.identifier
        })
        let additions = desired.filter { !unchangedIdentifiers.contains($0.identifier) }
        for request in additions {
            try await client.add(request, calendar: calendar)
        }

        return NotificationReconciliation(
            permissionStatus: status,
            desiredCount: desired.count,
            removedIdentifiers: removals,
            addedIdentifiers: additions.map(\.identifier)
        )
    }
}

final class UserNotificationCenterClient: NotificationCenterClient, @unchecked Sendable {
    private let center: UNUserNotificationCenter

    init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    func authorizationStatus() async -> NotificationPermissionStatus {
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return .authorized
        case .denied:
            return .denied
        case .notDetermined:
            return .notDetermined
        @unknown default:
            return .denied
        }
    }

    func requestAuthorization() async throws -> Bool {
        try await center.requestAuthorization(options: [.alert, .sound])
    }

    func pendingRequests() async -> [PendingCourseNotification] {
        await center.pendingNotificationRequests().map { request in
            PendingCourseNotification(
                identifier: request.identifier,
                title: request.content.title,
                body: request.content.body,
                fireDate: (request.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate()
            )
        }
    }

    func add(_ request: CourseNotificationRequest, calendar: Calendar) async throws {
        let content = UNMutableNotificationContent()
        content.title = request.title
        content.body = request.body
        content.sound = .default
        let dateComponents = calendar.dateComponents(
            [.calendar, .timeZone, .year, .month, .day, .hour, .minute, .second],
            from: request.fireDate
        )
        let trigger = UNCalendarNotificationTrigger(
            dateMatching: dateComponents,
            repeats: false
        )
        try await center.add(
            UNNotificationRequest(
                identifier: request.identifier,
                content: content,
                trigger: trigger
            )
        )
    }

    func removePendingRequests(withIdentifiers identifiers: [String]) async {
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
    }
}
