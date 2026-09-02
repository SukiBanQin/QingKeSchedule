import Foundation

@MainActor
protocol ScheduleRepository: AnyObject {
    func load() throws -> ScheduleDataDTO
    func replace(with data: ScheduleDataDTO) throws
    func saveSemester(_ semester: SemesterDTO) throws
    func saveCourse(_ course: CourseDTO) throws
    func deleteCourse(id: String) throws
}

enum ScheduleRepositoryError: Error, Equatable, LocalizedError {
    case invalidData([ScheduleValidationIssue])
    case inconsistentStore(String)

    var errorDescription: String? {
        switch self {
        case .invalidData(let issues):
            return issues.first?.message ?? "课表数据无效"
        case .inconsistentStore(let detail):
            return "本地课表无法读取：\(detail)"
        }
    }
}
