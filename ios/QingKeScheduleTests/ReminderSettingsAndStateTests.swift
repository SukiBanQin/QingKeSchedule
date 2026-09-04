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

        store.save(ReminderSettings(
            remindersEnabled: true,
            reminderLeadMinutes: 15,
            usesCustomLeadTime: true
        ))
        #expect(store.load() == ReminderSettings(
            remindersEnabled: true,
            reminderLeadMinutes: 15,
            usesCustomLeadTime: true
        ))

        #expect(ReminderSettings.presetLeadMinutes == [0, 5, 10, 15, 30])
        #expect(ReminderSettings.isValidLeadMinutes(0))
        #expect(ReminderSettings.isValidLeadMinutes(180))
        #expect(!ReminderSettings.isValidLeadMinutes(181))

        defaults.set(999, forKey: "reminderLeadMinutes")
        let sanitized = store.load()
        #expect(sanitized.remindersEnabled)
        #expect(sanitized.reminderLeadMinutes == ReminderSettings.defaults.reminderLeadMinutes)
        #expect(!sanitized.usesCustomLeadTime)

        defaults.removeObject(forKey: "usesCustomLeadTime")
        defaults.set(37, forKey: "reminderLeadMinutes")
        #expect(store.load().usesCustomLeadTime)
    }

    @Test("教学日历持久化会去重并丢弃无效日期")
    func academicCalendarRoundTripAndSanitization() throws {
        let suiteName = "AcademicCalendarSettingsTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = UserDefaultsAcademicCalendarSettingsStore(
            defaults: defaults,
            calendar: calendar
        )

        #expect(store.load() == .defaults)
        defaults.set(try JSONEncoder().encode(AcademicCalendarSettings(
            weekendsAreNonTeachingDays: true,
            nonTeachingDates: ["2026-10-01", "2026-10-01", "invalid"],
            makeupTeachingDays: [
                MakeupTeachingDay(date: "2026-10-01", followsDayOfWeek: 1),
                MakeupTeachingDay(date: "2026-10-10", followsDayOfWeek: 3),
                MakeupTeachingDay(date: "2026-10-11", followsDayOfWeek: 8),
            ]
        )), forKey: "academicCalendarSettings")

        #expect(store.load() == AcademicCalendarSettings(
            weekendsAreNonTeachingDays: true,
            nonTeachingDates: ["2026-10-01"],
            makeupTeachingDays: [
                MakeupTeachingDay(date: "2026-10-10", followsDayOfWeek: 3),
            ]
        ))

        defaults.set(Data(#"{"weekendsAreNonTeachingDays":true,"nonTeachingDates":[],"makeupTeachingDays":[]}"#.utf8), forKey: "academicCalendarSettings")
        #expect(store.load().lunchBreak == .defaultLunch)
    }

    @Test("停课日优先于调课和周末规则")
    func academicDayResolutionPriority() throws {
        let saturday = try #require(ScheduleRules.localDate(
            from: "2026-09-05",
            calendar: calendar
        ))
        var settings = AcademicCalendarSettings.defaults
        settings.weekendsAreNonTeachingDays = true
        #expect(settings.resolution(for: saturday, calendar: calendar)
                == .nonTeaching(reason: "周末默认停课"))

        settings.setMakeupTeachingDay(saturday, followsDayOfWeek: 1, calendar: calendar)
        #expect(settings.resolution(for: saturday, calendar: calendar)
                == .teaching(sourceDayOfWeek: 1, isMakeup: true))

        settings.setNonTeaching(saturday, calendar: calendar)
        #expect(settings.makeupTeachingDays.isEmpty)
        #expect(settings.resolution(for: saturday, calendar: calendar)
                == .nonTeaching(reason: "已设为停课日"))
    }

    @Test("修改教学日历会保存并重新对齐提醒")
    func academicCalendarChangesTriggerReconciliation() async throws {
        let repository = TestScheduleRepository()
        let settingsStore = InMemoryAcademicCalendarSettingsStore()
        let coordinator = RecordingNotificationCoordinator(status: .authorized)
        let state = ScheduleAppState(
            repository: repository,
            calendar: calendar,
            academicCalendarSettingsStore: settingsStore,
            notificationCoordinator: coordinator
        )
        let date = try #require(ScheduleRules.localDate(
            from: "2026-10-01",
            calendar: calendar
        ))

        state.load()
        await state.waitForNotificationWork()
        state.setWeekendsAreNonTeachingDays(true)
        await state.waitForNotificationWork()
        state.addNonTeachingDate(date)
        await state.waitForNotificationWork()
        #expect(state.setLunchBreak(startTime: "12:10", endTime: "13:20"))
        await state.waitForNotificationWork()
        #expect(!state.setLunchBreak(startTime: "14:00", endTime: "13:00"))

        let expected = AcademicCalendarSettings(
            weekendsAreNonTeachingDays: true,
            nonTeachingDates: ["2026-10-01"],
            makeupTeachingDays: [],
            lunchBreak: ScheduleBreakSettings(
                isEnabled: true,
                title: "午休",
                startTime: "12:10",
                endTime: "13:20"
            )
        )
        #expect(state.academicCalendarSettings == expected)
        #expect(settingsStore.load() == expected)
        #expect(await coordinator.recordedCalls().last?.academicCalendarSettings == expected)
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
        state.setReminderLeadMinutes(37)
        await state.waitForNotificationWork()
        #expect(state.reminderSettings.usesCustomLeadTime)
        state.setReminderLeadMinutes(15, usesCustomSelection: true)
        await state.waitForNotificationWork()
        #expect(state.reminderSettings.reminderLeadMinutes == 15)
        #expect(state.reminderSettings.usesCustomLeadTime)
        state.appBecameActive()
        await state.waitForNotificationWork()

        let calls = await coordinator.recordedCalls()
        #expect(calls.count == 8)
        #expect(calls.last?.data == fixture)
        #expect(calls.last?.remindersEnabled == true)
        #expect(calls.last?.leadMinutes == 15)
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
    let academicCalendarSettings: AcademicCalendarSettings
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
        academicCalendarSettings: AcademicCalendarSettings,
        now: Date,
        calendar: Calendar
    ) async throws -> NotificationReconciliation {
        calls.append(NotificationStateCall(
            data: data,
            remindersEnabled: remindersEnabled,
            leadMinutes: leadMinutes,
            academicCalendarSettings: academicCalendarSettings
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
        academicCalendarSettings: AcademicCalendarSettings,
        now: Date,
        calendar: Calendar
    ) async throws -> NotificationReconciliation {
        throw Failure.reconcile
    }
}
