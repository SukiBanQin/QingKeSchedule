import Foundation
import SwiftData

@MainActor
final class SwiftDataScheduleRepository: ScheduleRepository {
    static let emptyUpdatedAt = "1970-01-01T00:00:00.000Z"

    private let container: ModelContainer
    private let calendar: Calendar
    private let now: () -> Date
    private let beforeSave: () throws -> Void

    init(
        context: ModelContext,
        calendar: Calendar = ScheduleRules.gregorianCalendar(),
        now: @escaping () -> Date = { Date() },
        beforeSave: @escaping () throws -> Void = {}
    ) {
        self.container = context.container
        self.calendar = calendar
        self.now = now
        self.beforeSave = beforeSave
    }

    static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        let configuration = ModelConfiguration(isStoredInMemoryOnly: inMemory)
        let schema = Schema(versionedSchema: ScheduleSchemaV1.self)
        return try ModelContainer(
            for: schema,
            migrationPlan: ScheduleMigrationPlan.self,
            configurations: configuration
        )
    }

    func load() throws -> ScheduleDataDTO {
        let context = makeContext()
        let metadataRecords = try context.fetch(FetchDescriptor<ScheduleMetadataRecord>())
        let semesterRecords = try context.fetch(FetchDescriptor<SemesterRecord>())
        let courseRecords = try context.fetch(FetchDescriptor<CourseRecord>())

        guard metadataRecords.count <= 1 else {
            throw ScheduleRepositoryError.inconsistentStore("存在多条元数据")
        }
        guard semesterRecords.count <= 1 else {
            throw ScheduleRepositoryError.inconsistentStore("存在多个当前学期")
        }

        guard let metadata = metadataRecords.first else {
            guard semesterRecords.isEmpty, courseRecords.isEmpty else {
                throw ScheduleRepositoryError.inconsistentStore("元数据缺失")
            }
            return emptyData()
        }
        guard metadata.schemaVersion == ScheduleDataDTO.supportedSchemaVersion else {
            throw ScheduleRepositoryError.inconsistentStore("数据版本不受支持")
        }

        let semester = try semesterRecords.first.map(mapSemester)
        let courses = try courseRecords
            .sorted(by: stableRecordOrder)
            .map(mapCourse)
        let data = ScheduleDataDTO(
            semester: semester,
            courses: courses,
            updatedAt: metadata.updatedAt
        )
        let issues = ScheduleValidator.validate(data, calendar: calendar)
        guard issues.isEmpty else {
            throw ScheduleRepositoryError.invalidData(issues)
        }
        return data
    }

    func replace(with data: ScheduleDataDTO) throws {
        let issues = ScheduleValidator.validate(data, calendar: calendar)
        guard issues.isEmpty else {
            throw ScheduleRepositoryError.invalidData(issues)
        }

        let context = makeContext()
        try deleteAllRecords(in: context)
        context.insert(
            ScheduleMetadataRecord(
                schemaVersion: data.schemaVersion,
                updatedAt: data.updatedAt
            )
        )
        if let semester = data.semester {
            context.insert(makeSemesterRecord(from: semester))
        }
        for (index, course) in data.courses.enumerated() {
            context.insert(makeCourseRecord(from: course, sortIndex: index))
        }
        try beforeSave()
        try context.save()
    }

    func saveSemester(_ semester: SemesterDTO) throws {
        var data = try load()
        data = ScheduleDataDTO(
            semester: semester,
            courses: data.courses,
            updatedAt: timestamp(for: now())
        )
        try replace(with: data)
    }

    func saveCourse(_ course: CourseDTO) throws {
        let current = try load()
        guard current.semester != nil else {
            throw ScheduleRepositoryError.inconsistentStore("请先设置学期")
        }

        var courses = current.courses
        if let index = courses.firstIndex(where: { $0.id == course.id }) {
            courses[index] = course
        } else {
            courses.append(course)
        }
        try replace(
            with: ScheduleDataDTO(
                semester: current.semester,
                courses: courses,
                updatedAt: timestamp(for: now())
            )
        )
    }

    func deleteCourse(id: String) throws {
        let context = makeContext()
        let courseID = id
        let descriptor = FetchDescriptor<CourseRecord>(
            predicate: #Predicate { $0.id == courseID }
        )
        guard let course = try context.fetch(descriptor).first else { return }

        context.delete(course)
        let metadata = try requiredMetadata(in: context)
        metadata.updatedAt = timestamp(for: now())
        try beforeSave()
        try context.save()
    }

    private func emptyData() -> ScheduleDataDTO {
        ScheduleDataDTO(
            semester: nil,
            courses: [],
            updatedAt: Self.emptyUpdatedAt
        )
    }

    private func makeContext() -> ModelContext {
        let context = ModelContext(container)
        context.autosaveEnabled = false
        return context
    }

    private func requiredMetadata(in context: ModelContext) throws -> ScheduleMetadataRecord {
        let records = try context.fetch(FetchDescriptor<ScheduleMetadataRecord>())
        guard records.count == 1, let metadata = records.first else {
            throw ScheduleRepositoryError.inconsistentStore("元数据缺失")
        }
        return metadata
    }

    private func deleteAllRecords(in context: ModelContext) throws {
        for course in try context.fetch(FetchDescriptor<CourseRecord>()) {
            context.delete(course)
        }
        for semester in try context.fetch(FetchDescriptor<SemesterRecord>()) {
            context.delete(semester)
        }
        for metadata in try context.fetch(FetchDescriptor<ScheduleMetadataRecord>()) {
            context.delete(metadata)
        }
    }

    private func makeSemesterRecord(from semester: SemesterDTO) -> SemesterRecord {
        let periods = semester.periods.enumerated().map { index, period in
            PeriodRecord(
                number: period.number,
                startTime: period.startTime,
                endTime: period.endTime,
                sortIndex: index
            )
        }
        return SemesterRecord(
            id: semester.id,
            name: semester.name,
            startDate: semester.startDate,
            totalWeeks: semester.totalWeeks,
            periods: periods
        )
    }

    private func makeCourseRecord(from course: CourseDTO, sortIndex: Int) -> CourseRecord {
        let schedules = course.schedules.enumerated().map { index, schedule in
            CourseScheduleRecord(
                id: schedule.id,
                dayOfWeek: schedule.dayOfWeek,
                startPeriod: schedule.startPeriod,
                endPeriod: schedule.endPeriod,
                startWeek: schedule.startWeek,
                endWeek: schedule.endWeek,
                repeatValue: schedule.repeat.rawValue,
                classroom: schedule.classroom,
                sortIndex: index
            )
        }
        return CourseRecord(
            id: course.id,
            name: course.name,
            teacher: course.teacher,
            color: course.color,
            sortIndex: sortIndex,
            schedules: schedules
        )
    }

    private func mapSemester(_ record: SemesterRecord) throws -> SemesterDTO {
        SemesterDTO(
            id: record.id,
            name: record.name,
            startDate: record.startDate,
            totalWeeks: record.totalWeeks,
            periods: record.periods.sorted(by: stableRecordOrder).map {
                PeriodDTO(
                    number: $0.number,
                    startTime: $0.startTime,
                    endTime: $0.endTime
                )
            }
        )
    }

    private func mapCourse(_ record: CourseRecord) throws -> CourseDTO {
        let schedules = try record.schedules.sorted(by: stableRecordOrder).map { schedule in
            guard let repeatRule = RepeatRule(rawValue: schedule.repeatValue) else {
                throw ScheduleRepositoryError.inconsistentStore("课程重复规则无效")
            }
            return CourseScheduleDTO(
                id: schedule.id,
                dayOfWeek: schedule.dayOfWeek,
                startPeriod: schedule.startPeriod,
                endPeriod: schedule.endPeriod,
                startWeek: schedule.startWeek,
                endWeek: schedule.endWeek,
                repeat: repeatRule,
                classroom: schedule.classroom
            )
        }
        return CourseDTO(
            id: record.id,
            name: record.name,
            teacher: record.teacher,
            color: record.color,
            schedules: schedules
        )
    }

    private func stableRecordOrder(_ left: PeriodRecord, _ right: PeriodRecord) -> Bool {
        if left.sortIndex != right.sortIndex { return left.sortIndex < right.sortIndex }
        return left.number < right.number
    }

    private func stableRecordOrder(_ left: CourseRecord, _ right: CourseRecord) -> Bool {
        if left.sortIndex != right.sortIndex { return left.sortIndex < right.sortIndex }
        return left.id < right.id
    }

    private func stableRecordOrder(
        _ left: CourseScheduleRecord,
        _ right: CourseScheduleRecord
    ) -> Bool {
        if left.sortIndex != right.sortIndex { return left.sortIndex < right.sortIndex }
        return left.id < right.id
    }

    private func timestamp(for date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: date)
    }
}
