import Foundation
import Testing
@testable import QingKeSchedule

@MainActor
@Suite("提醒设置与应用状态", .serialized)
struct ReminderSettingsAndStateTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("UserDefaults 使用安全默认值并持久化有效设置")
    func userDefaultsRoundTripAndSanitization() {
        let suiteName = "ReminderSettingsAndStateTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = UserDefaultsReminderSettingsStore(defaults: defaults)

        #expect(store.load() == .defaults)

        store.save(ReminderSettings(remindersEnabled: true, reminderLeadMinutes: 30))
        #expect(store.load() == ReminderSettings(
            remindersEnabled: true,
            reminderLeadMinutes: 30
        ))

        defaults.set(999, forKey: "reminderLeadMinutes")
        #expect(store.load().reminderLeadMinutes == ReminderSettings.defaults.reminderLeadMinutes)
    }

    @Test("加载、课表变更、提前量和回到前台都会对齐提醒")
    func scheduleChangesTriggerReconciliation() async throws {
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(fixture.semester)
        let course = try #require(fixture.courses.first)
        let repository = TestScheduleRepository()
        let store = InMemoryReminderSettingsStore(settings: ReminderSettings(
            remindersEnabled: true,
            reminderLeadMinutes: 10
        ))
        let coordinator = RecordingNotificationCoordinator(status: .authorized)
        let state = ScheduleAppState(
            repository: repository,
            calendar: calendar,
            now: { Date(timeIntervalSince1970: 1_788_134_400) },
            reminderSettingsStore: store,
            notificationCoordinator: coordinator
        )

        state.load()
        await state.waitForNotificationWork()
        #expect(state.saveSemester(semester))
        await state.waitForNotificationWork()
        #expect(state.saveCourse(course))
        await state.waitForNotificationWork()
        #expect(state.deleteCourse(id: course.id))
        await state.waitForNotificationWork()
        #expect(state.replace(with: fixture))
        await state.waitForNotificationWork()
        state.setReminderLeadMinutes(30)
        await state.waitForNotificationWork()
        state.appBecameActive()
        await state.waitForNotificationWork()

        let calls = await coordinator.recordedCalls()
        #expect(calls.count == 7)
        #expect(calls.last?.data == fixture)
        #expect(calls.last?.remindersEnabled == true)
        #expect(calls.last?.leadMinutes == 30)
        #expect(state.lastNotificationReconciliation?.permissionStatus == .authorized)
    }

    @Test("仅用户显式开启时请求权限，拒绝不阻断关闭")
    func explicitPermissionFlowHandlesDenial() async throws {
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let repository = TestScheduleRepository(data: fixture)
        let store = InMemoryReminderSettingsStore()
        let coordinator = RecordingNotificationCoordinator(
            status: .notDetermined,
            authorizationResult: false
        )
        let state = ScheduleAppState(
            repository: repository,
            calendar: calendar,
            reminderSettingsStore: store,
            notificationCoordinator: coordinator
        )

        state.load()
        await state.waitForNotificationWork()
        let requestsAfterLoad = await coordinator.authorizationRequestCount()
        #expect(requestsAfterLoad == 0)

        state.setRemindersEnabled(true)
        await state.waitForNotificationWork()
        let requestsAfterEnable = await coordinator.authorizationRequestCount()
        #expect(requestsAfterEnable == 1)
        #expect(state.notificationPermission == .denied)
        #expect(state.reminderSettings.remindersEnabled)
        #expect(state.data == fixture)

        state.setRemindersEnabled(false)
        await state.waitForNotificationWork()
        let finalRequests = await coordinator.authorizationRequestCount()
        #expect(finalRequests == 1)
        #expect(state.reminderStatusMessage == "提醒已关闭")
    }

    @Test("提醒对齐失败只记录诊断，不回滚课表")
    func notificationFailureDoesNotRollBackSchedule() async throws {
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(fixture.semester)
        let repository = TestScheduleRepository()
        let coordinator = FailingNotificationCoordinator()
        let state = ScheduleAppState(
            repository: repository,
            calendar: calendar,
            reminderSettingsStore: InMemoryReminderSettingsStore(settings: ReminderSettings(
                remindersEnabled: true,
                reminderLeadMinutes: 10
            )),
            notificationCoordinator: coordinator
        )

        state.load()
        await state.waitForNotificationWork()
        #expect(state.saveSemester(semester))
        await state.waitForNotificationWork()

        let stored = try repository.load()
        #expect(stored.semester == semester)
        #expect(state.semester == semester)
        #expect(state.presentedError == nil)
        #expect(state.notificationDiagnostic?.contains("测试通知失败") == true)
    }
}

@MainActor
private final class TestScheduleRepository: ScheduleRepository {
    private var stored: ScheduleDataDTO

    init(data: ScheduleDataDTO = ScheduleDataDTO(
        semester: nil,
        courses: [],
        updatedAt: "1970-01-01T00:00:00.000Z"
    )) {
        stored = data
    }

    func load() throws -> ScheduleDataDTO { stored }

    func replace(with data: ScheduleDataDTO) throws {
        stored = data
    }

    func saveSemester(_ semester: SemesterDTO) throws {
        stored = ScheduleDataDTO(
            semester: semester,
            courses: stored.courses,
            updatedAt: stored.updatedAt
        )
    }

    func saveCourse(_ course: CourseDTO) throws {
        var courses = stored.courses.filter { $0.id != course.id }
        courses.append(course)
        stored = ScheduleDataDTO(
            semester: stored.semester,
            courses: courses,
            updatedAt: stored.updatedAt
        )
    }

    func deleteCourse(id: String) throws {
        stored = ScheduleDataDTO(
            semester: stored.semester,
            courses: stored.courses.filter { $0.id != id },
            updatedAt: stored.updatedAt
        )
    }
}

private struct NotificationStateCall: Equatable, Sendable {
    let data: ScheduleDataDTO
    let remindersEnabled: Bool
    let leadMinutes: Int
}

private actor RecordingNotificationCoordinator: NotificationCoordinating {
    private var status: NotificationPermissionStatus
    private let authorizationResult: Bool
    private var requestCount = 0
    private var calls: [NotificationStateCall] = []

    init(
        status: NotificationPermissionStatus,
        authorizationResult: Bool = true
    ) {
        self.status = status
        self.authorizationResult = authorizationResult
    }

    func authorizationStatus() async -> NotificationPermissionStatus { status }

    func requestAuthorization() async throws -> Bool {
        requestCount += 1
        status = authorizationResult ? .authorized : .denied
        return authorizationResult
    }

    func reconcile(
        data: ScheduleDataDTO,
        remindersEnabled: Bool,
        leadMinutes: Int,
        now: Date,
        calendar: Calendar
    ) async throws -> NotificationReconciliation {
        calls.append(NotificationStateCall(
            data: data,
            remindersEnabled: remindersEnabled,
            leadMinutes: leadMinutes
        ))
        return NotificationReconciliation(
            permissionStatus: status,
            desiredCount: 0,
            removedIdentifiers: [],
            addedIdentifiers: []
        )
    }

    func authorizationRequestCount() -> Int { requestCount }
    func recordedCalls() -> [NotificationStateCall] { calls }
}

private actor FailingNotificationCoordinator: NotificationCoordinating {
    private enum Failure: LocalizedError {
        case reconcile

        var errorDescription: String? { "测试通知失败" }
    }

    func authorizationStatus() async -> NotificationPermissionStatus { .authorized }
    func requestAuthorization() async throws -> Bool { true }

    func reconcile(
        data: ScheduleDataDTO,
        remindersEnabled: Bool,
        leadMinutes: Int,
        now: Date,
        calendar: Calendar
    ) async throws -> NotificationReconciliation {
        throw Failure.reconcile
    }
}
