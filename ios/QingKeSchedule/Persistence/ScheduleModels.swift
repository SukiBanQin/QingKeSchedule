import Foundation
import SwiftData

@Model
final class ScheduleMetadataRecord {
    @Attribute(.unique) var key: String
    var schemaVersion: Int
    var updatedAt: String

    init(
        key: String = "current",
        schemaVersion: Int,
        updatedAt: String
    ) {
        self.key = key
        self.schemaVersion = schemaVersion
        self.updatedAt = updatedAt
    }
}

@Model
final class SemesterRecord {
    @Attribute(.unique) var id: String
    var name: String
    var startDate: String
    var totalWeeks: Int
    @Relationship(deleteRule: .cascade, inverse: \PeriodRecord.semester)
    var periods: [PeriodRecord]

    init(
        id: String,
        name: String,
        startDate: String,
        totalWeeks: Int,
        periods: [PeriodRecord]
    ) {
        self.id = id
        self.name = name
        self.startDate = startDate
        self.totalWeeks = totalWeeks
        self.periods = periods
    }
}

@Model
final class PeriodRecord {
    var number: Int
    var startTime: String
    var endTime: String
    var sortIndex: Int
    var semester: SemesterRecord?

    init(
        number: Int,
        startTime: String,
        endTime: String,
        sortIndex: Int
    ) {
        self.number = number
        self.startTime = startTime
        self.endTime = endTime
        self.sortIndex = sortIndex
    }
}

@Model
final class CourseRecord {
    @Attribute(.unique) var id: String
    var name: String
    var teacher: String
    var color: String
    var sortIndex: Int
    @Relationship(deleteRule: .cascade, inverse: \CourseScheduleRecord.course)
    var schedules: [CourseScheduleRecord]

    init(
        id: String,
        name: String,
        teacher: String,
        color: String,
        sortIndex: Int,
        schedules: [CourseScheduleRecord]
    ) {
        self.id = id
        self.name = name
        self.teacher = teacher
        self.color = color
        self.sortIndex = sortIndex
        self.schedules = schedules
    }
}

@Model
final class CourseScheduleRecord {
    @Attribute(.unique) var id: String
    var dayOfWeek: Int
    var startPeriod: Int
    var endPeriod: Int
    var startWeek: Int
    var endWeek: Int
    var repeatValue: String
    var classroom: String
    var sortIndex: Int
    var course: CourseRecord?

    init(
        id: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        startWeek: Int,
        endWeek: Int,
        repeatValue: String,
        classroom: String,
        sortIndex: Int
    ) {
        self.id = id
        self.dayOfWeek = dayOfWeek
        self.startPeriod = startPeriod
        self.endPeriod = endPeriod
        self.startWeek = startWeek
        self.endWeek = endWeek
        self.repeatValue = repeatValue
        self.classroom = classroom
        self.sortIndex = sortIndex
    }
}

enum ScheduleSchemaV1: VersionedSchema {
    static let versionIdentifier = Schema.Version(1, 0, 0)

    static var models: [any PersistentModel.Type] {
        [
            ScheduleMetadataRecord.self,
            SemesterRecord.self,
            PeriodRecord.self,
            CourseRecord.self,
            CourseScheduleRecord.self,
        ]
    }
}

enum ScheduleMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] {
        [ScheduleSchemaV1.self]
    }

    static var stages: [MigrationStage] {
        []
    }
}
