import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class ScheduleAppState {
    private(set) var data = ScheduleDataDTO(
        semester: nil,
        courses: [],
        updatedAt: SwiftDataScheduleRepository.emptyUpdatedAt
    )
    private(set) var isLoaded = false
    var presentedError: String?
    private(set) var reminderSettings: ReminderSettings
    private(set) var academicCalendarSettings: AcademicCalendarSettings
    private(set) var notificationPermission: NotificationPermissionStatus = .notDetermined
    private(set) var lastNotificationReconciliation: NotificationReconciliation?
    private(set) var notificationDiagnostic: String?
    private(set) var pendingImportPreview: ScheduleImportPreview?
    private(set) var importFailure: String?
    private(set) var importStatusMessage: String?

    @ObservationIgnored private let repository: any ScheduleRepository
    @ObservationIgnored private let nowProvider: () -> Date
    @ObservationIgnored let calendar: Calendar
    @ObservationIgnored private let reminderSettingsStore: any ReminderSettingsStore
    @ObservationIgnored private let academicCalendarSettingsStore: any AcademicCalendarSettingsStore
    @ObservationIgnored private let notificationCoordinator: (any NotificationCoordinating)?
    @ObservationIgnored private var notificationTask: Task<Void, Never>?
    @ObservationIgnored private let notificationLogger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "QingKeSchedule",
        category: "Notifications"
    )

    init(
        repository: any ScheduleRepository,
        calendar: Calendar = ScheduleRules.gregorianCalendar(),
        now: @escaping () -> Date = { Date() },
        reminderSettingsStore: (any ReminderSettingsStore)? = nil,
        academicCalendarSettingsStore: (any AcademicCalendarSettingsStore)? = nil,
        notificationCoordinator: (any NotificationCoordinating)? = nil
    ) {
        let resolvedReminderSettingsStore = reminderSettingsStore
            ?? InMemoryReminderSettingsStore()
        let resolvedAcademicCalendarSettingsStore = academicCalendarSettingsStore
            ?? InMemoryAcademicCalendarSettingsStore()
        self.repository = repository
        self.calendar = calendar
        self.nowProvider = now
        self.reminderSettingsStore = resolvedReminderSettingsStore
        self.reminderSettings = resolvedReminderSettingsStore.load()
        self.academicCalendarSettingsStore = resolvedAcademicCalendarSettingsStore
        self.academicCalendarSettings = resolvedAcademicCalendarSettingsStore.load()
        self.notificationCoordinator = notificationCoordinator
    }

    var semester: SemesterDTO? { data.semester }
    var courses: [CourseDTO] { data.courses }
    var needsOnboarding: Bool { isLoaded && semester == nil }
    var now: Date { nowProvider() }

    var reminderStatusMessage: String {
        if let notificationDiagnostic {
            return "课表已保存，但提醒更新失败：\(notificationDiagnostic)"
        }
        guard reminderSettings.remindersEnabled else {
            return "提醒已关闭"
        }
        switch notificationPermission {
        case .notDetermined:
            return "尚未获得系统通知权限"
        case .denied:
            return "系统通知权限已关闭，请前往系统设置开启"
        case .authorized:
            let count = lastNotificationReconciliation?.desiredCount ?? 0
            return count == 0 ? "当前没有待安排的课程提醒" : "已安排最近 \(count) 条课程提醒"
        }
    }

    func load() {
        do {
            data = try repository.load()
            isLoaded = true
            presentedError = nil
            scheduleNotificationReconciliation()
        } catch {
            isLoaded = true
            present(error)
        }
    }

    @discardableResult
    func replace(with data: ScheduleDataDTO) -> Bool {
        perform {
            try repository.replace(with: data)
        }
    }

    @discardableResult
    func saveSemester(_ semester: SemesterDTO) -> Bool {
        perform {
            try repository.saveSemester(semester)
        }
    }

    @discardableResult
    func saveCourse(_ course: CourseDTO) -> Bool {
        perform {
            try repository.saveCourse(course)
        }
    }

    @discardableResult
    func deleteCourse(id: String) -> Bool {
        perform {
            try repository.deleteCourse(id: id)
        }
    }

    func dismissError() {
        presentedError = nil
    }

    func previewImport(contents: Data) throws -> ScheduleImportPreview {
        try ScheduleDataTransfer.previewImport(contents: contents, calendar: calendar)
    }

    func prepareImport(contents: Data) {
        importStatusMessage = nil
        do {
            pendingImportPreview = try previewImport(contents: contents)
            importFailure = nil
        } catch {
            pendingImportPreview = nil
            importFailure = error.localizedDescription
        }
    }

    func presentImportFailure(_ message: String) {
        importStatusMessage = nil
        pendingImportPreview = nil
        importFailure = message
    }

    func dismissImportPrompt() {
        pendingImportPreview = nil
        importFailure = nil
    }

    @discardableResult
    func confirmPreparedImport() -> Int? {
        guard let pendingImportPreview else { return nil }
        let courseCount = pendingImportPreview.courseCount
        guard confirmImport(pendingImportPreview) else { return nil }
        dismissImportPrompt()
        importStatusMessage = "已导入 \(courseCount) 门课程"
        return courseCount
    }

    @discardableResult
    func confirmImport(_ preview: ScheduleImportPreview) -> Bool {
        replace(with: preview.data)
    }

    func exportDocument() throws -> ScheduleExportDocument {
        try ScheduleDataTransfer.exportDocument(
            data: data,
            exportedAt: now,
            calendar: calendar
        )
    }

    func setRemindersEnabled(_ enabled: Bool) {
        guard reminderSettings.remindersEnabled != enabled else { return }
        reminderSettings.remindersEnabled = enabled
        reminderSettingsStore.save(reminderSettings)
        scheduleNotificationReconciliation(requestAuthorization: enabled)
    }

    func setReminderLeadMinutes(
        _ minutes: Int,
        usesCustomSelection: Bool? = nil
    ) {
        let resolvedCustomSelection = usesCustomSelection
            ?? !ReminderSettings.presetLeadMinutes.contains(minutes)
        guard
            ReminderSettings.isValidLeadMinutes(minutes),
            reminderSettings.reminderLeadMinutes != minutes
                || reminderSettings.usesCustomLeadTime != resolvedCustomSelection
        else {
            return
        }
        reminderSettings.reminderLeadMinutes = minutes
        reminderSettings.usesCustomLeadTime = resolvedCustomSelection
        reminderSettingsStore.save(reminderSettings)
        scheduleNotificationReconciliation()
    }

    func setWeekendsAreNonTeachingDays(_ enabled: Bool) {
        guard academicCalendarSettings.weekendsAreNonTeachingDays != enabled else { return }
        academicCalendarSettings.weekendsAreNonTeachingDays = enabled
        persistAcademicCalendarSettings()
    }

    func addNonTeachingDate(_ date: Date) {
        academicCalendarSettings.setNonTeaching(date, calendar: calendar)
        persistAcademicCalendarSettings()
    }

    func removeNonTeachingDate(_ dateString: String) {
        academicCalendarSettings.removeNonTeachingDate(dateString)
        persistAcademicCalendarSettings()
    }

    func addMakeupTeachingDay(_ date: Date, followsDayOfWeek: Int) {
        academicCalendarSettings.setMakeupTeachingDay(
            date,
            followsDayOfWeek: followsDayOfWeek,
            calendar: calendar
        )
        persistAcademicCalendarSettings()
    }

    func removeMakeupTeachingDay(_ dateString: String) {
        academicCalendarSettings.removeMakeupTeachingDay(dateString)
        persistAcademicCalendarSettings()
    }

    func setLunchBreakEnabled(_ enabled: Bool) {
        guard academicCalendarSettings.lunchBreak.isEnabled != enabled else { return }
        academicCalendarSettings.lunchBreak.isEnabled = enabled
        persistAcademicCalendarSettings()
    }

    @discardableResult
    func setLunchBreak(startTime: String, endTime: String) -> Bool {
        var updated = academicCalendarSettings.lunchBreak
        updated.startTime = startTime
        updated.endTime = endTime
        guard updated.isValid else { return false }
        academicCalendarSettings.lunchBreak = updated.sanitized()
        persistAcademicCalendarSettings()
        return true
    }

    func appBecameActive() {
        guard isLoaded else { return }
        scheduleNotificationReconciliation()
    }

    func waitForNotificationWork() async {
        let task = notificationTask
        await task?.value
    }

    private func perform(_ operation: () throws -> Void) -> Bool {
        do {
            try operation()
            data = try repository.load()
            presentedError = nil
            scheduleNotificationReconciliation()
            return true
        } catch {
            present(error)
            return false
        }
    }

    private func present(_ error: Error) {
        if let repositoryError = error as? ScheduleRepositoryError,
           let description = repositoryError.errorDescription {
            presentedError = description
        } else {
            presentedError = error.localizedDescription
        }
    }

    private func persistAcademicCalendarSettings() {
        academicCalendarSettingsStore.save(academicCalendarSettings)
        scheduleNotificationReconciliation()
    }

    private func scheduleNotificationReconciliation(requestAuthorization: Bool = false) {
        guard let notificationCoordinator else { return }
        notificationTask?.cancel()
        let dataSnapshot = data
        let settingsSnapshot = reminderSettings
        let academicCalendarSettingsSnapshot = academicCalendarSettings
        let nowSnapshot = nowProvider()
        let calendarSnapshot = calendar

        notificationTask = Task { [weak self] in
            do {
                let permission = await notificationCoordinator.authorizationStatus()
                if requestAuthorization, permission == .notDetermined {
                    _ = try await notificationCoordinator.requestAuthorization()
                }
                let result = try await notificationCoordinator.reconcile(
                    data: dataSnapshot,
                    remindersEnabled: settingsSnapshot.remindersEnabled,
                    leadMinutes: settingsSnapshot.reminderLeadMinutes,
                    academicCalendarSettings: academicCalendarSettingsSnapshot,
                    now: nowSnapshot,
                    calendar: calendarSnapshot
                )
                guard !Task.isCancelled, let self else { return }
                notificationPermission = result.permissionStatus
                lastNotificationReconciliation = result
                notificationDiagnostic = nil
            } catch {
                guard !Task.isCancelled, let self else { return }
                notificationPermission = await notificationCoordinator.authorizationStatus()
                notificationDiagnostic = error.localizedDescription
                notificationLogger.error(
                    "Notification reconciliation failed: \(error.localizedDescription, privacy: .public)"
                )
            }
        }
    }
}
