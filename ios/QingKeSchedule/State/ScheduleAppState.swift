import Foundation
import Observation

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

    @ObservationIgnored private let repository: any ScheduleRepository
    @ObservationIgnored private let nowProvider: () -> Date
    @ObservationIgnored let calendar: Calendar

    init(
        repository: any ScheduleRepository,
        calendar: Calendar = ScheduleRules.gregorianCalendar(),
        now: @escaping () -> Date = { Date() }
    ) {
        self.repository = repository
        self.calendar = calendar
        self.nowProvider = now
    }

    var semester: SemesterDTO? { data.semester }
    var courses: [CourseDTO] { data.courses }
    var needsOnboarding: Bool { isLoaded && semester == nil }
    var now: Date { nowProvider() }

    func load() {
        do {
            data = try repository.load()
            isLoaded = true
            presentedError = nil
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

    private func perform(_ operation: () throws -> Void) -> Bool {
        do {
            try operation()
            data = try repository.load()
            presentedError = nil
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
}
