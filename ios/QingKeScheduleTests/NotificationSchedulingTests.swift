import Foundation
import Testing
@testable import QingKeSchedule

@Suite("课程通知计划")
struct CourseNotificationPlannerTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("生成稳定标识、正确内容、提前时间和单双周")
    func createsStableRequests() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let now = try date("2026-08-31", hour: 7, minute: 49)

        let first = CourseNotificationPlanner.requests(
            data: data,
            leadMinutes: 10,
            after: now,
            limit: 60,
            calendar: calendar
        )
        let second = CourseNotificationPlanner.requests(
            data: data,
            leadMinutes: 10,
            after: now,
            limit: 60,
            calendar: calendar
        )
        let expectedFireDate = try date("2026-08-31", hour: 7, minute: 50)

        #expect(first == second)
        let every = try #require(first.first { $0.identifier == "schedule.course-every.schedule-every.week.1" })
        #expect(every.title == "数据结构")
        #expect(every.body == "08:00–09:40 · A101")
        #expect(every.fireDate == expectedFireDate)
        #expect(!first.contains { $0.identifier == "schedule.course-odd.schedule-odd.week.2" })
        #expect(first.contains { $0.identifier == "schedule.course-odd.schedule-odd.week.3" })
        #expect(first.contains { $0.identifier == "schedule.course-even.schedule-even.week.2" })

        let noClassroom = try #require(first.first {
            $0.identifier == "schedule.course-alpha.schedule-alpha.week.1"
        })
        #expect(noClassroom.body == "10:55–11:40")
    }

    @Test("按时间排序并限制最近 60 条")
    func appliesRollingLimit() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let now = try date("2026-08-30", hour: 0, minute: 0)

        let all = CourseNotificationPlanner.requests(
            data: data,
            leadMinutes: 0,
            after: now,
            limit: 1_000,
            calendar: calendar
        )
        let limited = CourseNotificationPlanner.requests(
            data: data,
            leadMinutes: 0,
            after: now,
            limit: 60,
            calendar: calendar
        )

        #expect(all.count > 60)
        #expect(limited.count == 60)
        #expect(limited == Array(all.prefix(60)))
        #expect(limited.map(\.fireDate) == limited.map(\.fireDate).sorted())
    }

    @Test("忽略已经错过提醒时间和学期后的课程")
    func excludesPastRequests() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let afterSemester = try date("2027-01-31", hour: 0, minute: 0)

        #expect(CourseNotificationPlanner.requests(
            data: data,
            leadMinutes: 10,
            after: afterSemester,
            limit: 60,
            calendar: calendar
        ).isEmpty)
    }

    private func date(_ localDate: String, hour: Int, minute: Int) throws -> Date {
        let day = try #require(ScheduleRules.localDate(from: localDate, calendar: calendar))
        return try #require(calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day))
    }
}

@Suite("通知滚动协调器")
struct NotificationCoordinatorTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("保留相同请求并替换过期、变化或缺失请求")
    func reconcilesDiff() async throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let now = try #require(ScheduleRules.localDate(from: "2026-08-30", calendar: calendar))
        let desired = CourseNotificationPlanner.requests(
            data: data,
            leadMinutes: 10,
            after: now,
            limit: 2,
            calendar: calendar
        )
        let first = try #require(desired.first)
        let second = try #require(desired.last)
        let client = RecordingNotificationCenterClient(
            status: .authorized,
            pending: [
                PendingCourseNotification(
                    identifier: first.identifier,
                    title: first.title,
                    body: first.body,
                    fireDate: first.fireDate
                ),
                PendingCourseNotification(
                    identifier: second.identifier,
                    title: "旧标题",
                    body: second.body,
                    fireDate: second.fireDate
                ),
                PendingCourseNotification(
                    identifier: "schedule.deleted.old.week.1",
                    title: "已删除",
                    body: "",
                    fireDate: nil
                ),
                PendingCourseNotification(
                    identifier: "unmanaged.notification",
                    title: "其他功能",
                    body: "",
                    fireDate: nil
                ),
            ]
        )
        let coordinator = NotificationCoordinator(client: client, maximumPending: 2)

        let result = try await coordinator.reconcile(
            data: data,
            remindersEnabled: true,
            leadMinutes: 10,
            now: now,
            calendar: calendar
        )
        let addedRequests = await client.addedRequests()
        let currentPending = await client.currentPending()

        #expect(result.desiredCount == 2)
        #expect(result.removedIdentifiers == [
            "schedule.deleted.old.week.1",
            second.identifier,
        ].sorted())
        #expect(result.addedIdentifiers == [second.identifier])
        #expect(addedRequests == [second])
        #expect(currentPending.contains {
            $0.identifier == "unmanaged.notification"
        })
    }

    @Test("提醒关闭或权限拒绝时只清除本应用课程提醒")
    func clearsManagedRequestsWhenUnavailable() async throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let managed = PendingCourseNotification(
            identifier: "schedule.course.schedule.week.1",
            title: "课程",
            body: "08:00–08:45",
            fireDate: nil
        )
        let unrelated = PendingCourseNotification(
            identifier: "another.feature",
            title: "其他",
            body: "",
            fireDate: nil
        )
        let client = RecordingNotificationCenterClient(
            status: .authorized,
            pending: [managed, unrelated]
        )
        let coordinator = NotificationCoordinator(client: client)

        let disabled = try await coordinator.reconcile(
            data: data,
            remindersEnabled: false,
            leadMinutes: 10,
            now: Date(),
            calendar: calendar
        )
        let pendingAfterDisable = await client.currentPending()
        #expect(disabled.removedIdentifiers == [managed.identifier])
        #expect(pendingAfterDisable == [unrelated])

        await client.replacePending(with: [managed, unrelated], status: .denied)
        let denied = try await coordinator.reconcile(
            data: data,
            remindersEnabled: true,
            leadMinutes: 10,
            now: Date(),
            calendar: calendar
        )
        let pendingAfterDenial = await client.currentPending()
        #expect(denied.permissionStatus == .denied)
        #expect(denied.removedIdentifiers == [managed.identifier])
        #expect(pendingAfterDenial == [unrelated])
    }
}

private actor RecordingNotificationCenterClient: NotificationCenterClient {
    private var status: NotificationPermissionStatus
    private var pending: [PendingCourseNotification]
    private var added: [CourseNotificationRequest] = []

    init(status: NotificationPermissionStatus, pending: [PendingCourseNotification]) {
        self.status = status
        self.pending = pending
    }

    func authorizationStatus() async -> NotificationPermissionStatus { status }
    func requestAuthorization() async throws -> Bool { status == .authorized }
    func pendingRequests() async -> [PendingCourseNotification] { pending }

    func add(_ request: CourseNotificationRequest, calendar: Calendar) async throws {
        added.append(request)
        pending.removeAll { $0.identifier == request.identifier }
        pending.append(
            PendingCourseNotification(
                identifier: request.identifier,
                title: request.title,
                body: request.body,
                fireDate: request.fireDate
            )
        )
    }

    func removePendingRequests(withIdentifiers identifiers: [String]) async {
        pending.removeAll { identifiers.contains($0.identifier) }
    }

    func addedRequests() -> [CourseNotificationRequest] { added }
    func currentPending() -> [PendingCourseNotification] { pending }

    func replacePending(
        with requests: [PendingCourseNotification],
        status: NotificationPermissionStatus
    ) {
        pending = requests
        self.status = status
    }
}
