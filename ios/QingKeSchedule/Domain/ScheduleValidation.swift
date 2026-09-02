import Foundation

struct ScheduleValidationIssue: Equatable, Sendable {
    let path: String
    let message: String
}

enum ScheduleValidator {
    static let courseColors: Set<String> = [
        "#287B74",
        "#D96952",
        "#536FAF",
        "#9A6AAF",
        "#B87928",
        "#46835A",
    ]

    static func validate(
        _ data: ScheduleDataDTO,
        calendar: Calendar
    ) -> [ScheduleValidationIssue] {
        var issues: [ScheduleValidationIssue] = []

        if data.schemaVersion != ScheduleDataDTO.supportedSchemaVersion {
            issues.append(.init(path: "schemaVersion", message: "不支持的数据版本"))
        }
        if !isISO8601UTC(data.updatedAt) {
            issues.append(.init(path: "updatedAt", message: "更新时间格式无效"))
        }

        guard let semester = data.semester else {
            if !data.courses.isEmpty {
                issues.append(.init(path: "courses", message: "没有学期时不能包含课程"))
            }
            return issues
        }

        issues.append(contentsOf: validateSemester(semester, calendar: calendar))
        for (index, course) in data.courses.enumerated() {
            issues.append(
                contentsOf: validateCourse(
                    course,
                    semester: semester,
                    index: index
                )
            )
        }
        return issues
    }

    private static func validateSemester(
        _ semester: SemesterDTO,
        calendar: Calendar
    ) -> [ScheduleValidationIssue] {
        var issues: [ScheduleValidationIssue] = []
        if semester.id.isEmpty {
            issues.append(.init(path: "semester.id", message: "学期 ID 不能为空"))
        }
        if semester.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            issues.append(.init(path: "semester.name", message: "请填写学期名称"))
        }
        if ScheduleRules.localDate(from: semester.startDate, calendar: calendar) == nil {
            issues.append(.init(path: "semester.startDate", message: "请选择有效的开始日期"))
        }
        if !(1...52).contains(semester.totalWeeks) {
            issues.append(.init(path: "semester.totalWeeks", message: "总周数需要在 1 到 52 之间"))
        }
        if !(1...20).contains(semester.periods.count) {
            issues.append(.init(path: "semester.periods", message: "请设置 1 到 20 个节次"))
        }

        var periodNumbers = Set<Int>()
        for (index, period) in semester.periods.enumerated() {
            let path = "semester.periods.\(index)"
            guard
                period.number > 0,
                let startMinutes = ScheduleRules.minutes(from: period.startTime),
                let endMinutes = ScheduleRules.minutes(from: period.endTime),
                startMinutes < endMinutes
            else {
                issues.append(.init(path: path, message: "节次时间无效"))
                continue
            }

            if !periodNumbers.insert(period.number).inserted {
                issues.append(.init(path: path, message: "节次编号不能重复"))
            }
            if index > 0, semester.periods[index - 1].endTime > period.startTime {
                issues.append(.init(path: path, message: "相邻节次的时间不能重叠"))
            }
        }
        return issues
    }

    private static func validateCourse(
        _ course: CourseDTO,
        semester: SemesterDTO,
        index: Int
    ) -> [ScheduleValidationIssue] {
        let basePath = "courses.\(index)"
        var issues: [ScheduleValidationIssue] = []
        let validPeriodNumbers = Set(semester.periods.map(\.number))

        if course.id.isEmpty {
            issues.append(.init(path: "\(basePath).id", message: "课程 ID 不能为空"))
        }
        if course.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            issues.append(.init(path: "\(basePath).name", message: "请填写课程名称"))
        }
        if !courseColors.contains(course.color) {
            issues.append(.init(path: "\(basePath).color", message: "请选择一个可用的课程颜色"))
        }
        if course.schedules.isEmpty {
            issues.append(.init(path: "\(basePath).schedules", message: "至少需要一个上课安排"))
        }

        for (scheduleIndex, schedule) in course.schedules.enumerated() {
            let schedulePath = "\(basePath).schedules.\(scheduleIndex)"
            if schedule.id.isEmpty {
                issues.append(.init(path: "\(schedulePath).id", message: "安排 ID 不能为空"))
            }
            if !(1...7).contains(schedule.dayOfWeek) {
                issues.append(.init(path: "\(schedulePath).dayOfWeek", message: "请选择星期"))
            }
            if !validPeriodNumbers.contains(schedule.startPeriod)
                || !validPeriodNumbers.contains(schedule.endPeriod)
                || schedule.startPeriod > schedule.endPeriod {
                issues.append(.init(path: "\(schedulePath).periods", message: "请选择有效的起止节次"))
            }
            if schedule.startWeek < 1
                || schedule.endWeek > semester.totalWeeks
                || schedule.startWeek > schedule.endWeek {
                issues.append(.init(path: "\(schedulePath).weeks", message: "请选择有效的起止周"))
            }
        }
        return issues
    }

    private static func isISO8601UTC(_ value: String) -> Bool {
        guard value.hasSuffix("Z") else { return false }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if formatter.date(from: value) != nil { return true }

        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value) != nil
    }
}
