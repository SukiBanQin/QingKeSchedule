import Foundation

enum RepeatRule: String, Codable, CaseIterable, Sendable {
    case every
    case odd
    case even
}

struct PeriodDTO: Codable, Equatable, Sendable {
    let number: Int
    let startTime: String
    let endTime: String
}

struct SemesterDTO: Codable, Equatable, Sendable {
    let id: String
    let name: String
    let startDate: String
    let totalWeeks: Int
    let periods: [PeriodDTO]
}

struct CourseScheduleDTO: Codable, Equatable, Sendable {
    let id: String
    let dayOfWeek: Int
    let startPeriod: Int
    let endPeriod: Int
    let startWeek: Int
    let endWeek: Int
    let `repeat`: RepeatRule
    let classroom: String
}

struct CourseDTO: Codable, Equatable, Sendable {
    let id: String
    let name: String
    let teacher: String
    let color: String
    let schedules: [CourseScheduleDTO]
}

struct ScheduleDataDTO: Codable, Equatable, Sendable {
    static let supportedSchemaVersion = 1

    private enum CodingKeys: String, CodingKey {
        case schemaVersion
        case semester
        case courses
        case updatedAt
    }

    let schemaVersion: Int
    let semester: SemesterDTO?
    let courses: [CourseDTO]
    let updatedAt: String

    init(
        schemaVersion: Int = ScheduleDataDTO.supportedSchemaVersion,
        semester: SemesterDTO?,
        courses: [CourseDTO],
        updatedAt: String
    ) {
        self.schemaVersion = schemaVersion
        self.semester = semester
        self.courses = courses
        self.updatedAt = updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let schemaVersion = try container.decode(Int.self, forKey: .schemaVersion)
        guard schemaVersion == ScheduleDataDTO.supportedSchemaVersion else {
            throw DecodingError.dataCorruptedError(
                forKey: .schemaVersion,
                in: container,
                debugDescription: "Unsupported schedule data schema version: \(schemaVersion)"
            )
        }

        self.schemaVersion = schemaVersion
        guard container.contains(.semester) else {
            throw DecodingError.keyNotFound(
                CodingKeys.semester,
                .init(
                    codingPath: container.codingPath,
                    debugDescription: "Missing required field: semester"
                )
            )
        }
        semester = try container.decodeIfPresent(SemesterDTO.self, forKey: .semester)
        courses = try container.decode([CourseDTO].self, forKey: .courses)
        updatedAt = try container.decode(String.self, forKey: .updatedAt)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(schemaVersion, forKey: .schemaVersion)
        if let semester {
            try container.encode(semester, forKey: .semester)
        } else {
            try container.encodeNil(forKey: .semester)
        }
        try container.encode(courses, forKey: .courses)
        try container.encode(updatedAt, forKey: .updatedAt)
    }
}

struct CourseOccurrenceDTO: Equatable, Sendable {
    let course: CourseDTO
    let schedule: CourseScheduleDTO
}

struct ScheduleConflictDTO: Equatable, Sendable {
    let candidateCourse: CourseDTO
    let candidateSchedule: CourseScheduleDTO
    let existingCourse: CourseDTO
    let existingSchedule: CourseScheduleDTO
    let weeks: [Int]
}

enum CourseStatus: String, Equatable, Sendable {
    case finished
    case ongoing
    case upcoming
}
