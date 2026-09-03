import Foundation

struct CourseScheduleDraft: Identifiable, Equatable {
    let id: String
    var dayOfWeek: Int
    var startPeriod: Int
    var endPeriod: Int
    var startWeek: Int
    var endWeek: Int
    var repeatRule: RepeatRule
    var classroom: String
}

enum CourseSaveEvaluation: Equatable {
    case invalid([ScheduleValidationIssue])
    case conflicting([ScheduleConflictDTO])
    case ready
}

struct CourseDraft {
    var id: String
    var name: String
    var teacher: String
    var color: String
    var schedules: [CourseScheduleDraft]

    private let baseline: CourseDTO

    init(
        course: CourseDTO? = nil,
        semester: SemesterDTO,
        now: Date = Date(),
        calendar: Calendar = ScheduleRules.gregorianCalendar()
    ) {
        let firstPeriod = semester.periods.first?.number ?? 1
        let sundayBasedWeekday = calendar.component(.weekday, from: now)
        let currentDayOfWeek = sundayBasedWeekday == 1 ? 7 : sundayBasedWeekday - 1

        if let course {
            id = course.id
            name = course.name
            teacher = course.teacher
            color = course.color
            schedules = course.schedules.map(Self.makeScheduleDraft)
            baseline = course
        } else {
            let schedule = CourseScheduleDraft(
                id: UUID().uuidString,
                dayOfWeek: currentDayOfWeek,
                startPeriod: firstPeriod,
                endPeriod: firstPeriod,
                startWeek: 1,
                endWeek: semester.totalWeeks,
                repeatRule: .every,
                classroom: ""
            )
            let courseID = UUID().uuidString
            id = courseID
            name = ""
            teacher = ""
            color = "#287B74"
            schedules = [schedule]
            baseline = CourseDTO(
                id: courseID,
                name: "",
                teacher: "",
                color: "#287B74",
                schedules: [Self.makeScheduleDTO(schedule)]
            )
        }
    }

    var isDirty: Bool { course() != baseline }

    func course() -> CourseDTO {
        CourseDTO(
            id: id,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            teacher: teacher.trimmingCharacters(in: .whitespacesAndNewlines),
            color: color,
            schedules: schedules.map(Self.makeScheduleDTO)
        )
    }

    func validationIssues(
        semester: SemesterDTO,
        calendar: Calendar = ScheduleRules.gregorianCalendar()
    ) -> [ScheduleValidationIssue] {
        ScheduleValidator.validate(
            ScheduleDataDTO(
                semester: semester,
                courses: [course()],
                updatedAt: "1970-01-01T00:00:00.000Z"
            ),
            calendar: calendar
        )
    }

    func evaluateSave(
        semester: SemesterDTO,
        existingCourses: [CourseDTO],
        calendar: Calendar = ScheduleRules.gregorianCalendar()
    ) -> CourseSaveEvaluation {
        let issues = validationIssues(semester: semester, calendar: calendar)
        guard issues.isEmpty else { return .invalid(issues) }

        let conflicts = ScheduleRules.conflicts(
            for: course(),
            against: existingCourses
        )
        return conflicts.isEmpty ? .ready : .conflicting(conflicts)
    }

    mutating func addSchedule(copying source: CourseScheduleDraft? = nil) {
        let template = source ?? schedules.last
        schedules.append(
            CourseScheduleDraft(
                id: UUID().uuidString,
                dayOfWeek: template?.dayOfWeek ?? 1,
                startPeriod: template?.startPeriod ?? 1,
                endPeriod: template?.endPeriod ?? 1,
                startWeek: template?.startWeek ?? 1,
                endWeek: template?.endWeek ?? 1,
                repeatRule: template?.repeatRule ?? .every,
                classroom: template?.classroom ?? ""
            )
        )
    }

    mutating func removeSchedule(id: String) {
        guard schedules.count > 1 else { return }
        schedules.removeAll { $0.id == id }
    }

    private static func makeScheduleDraft(_ schedule: CourseScheduleDTO) -> CourseScheduleDraft {
        CourseScheduleDraft(
            id: schedule.id,
            dayOfWeek: schedule.dayOfWeek,
            startPeriod: schedule.startPeriod,
            endPeriod: schedule.endPeriod,
            startWeek: schedule.startWeek,
            endWeek: schedule.endWeek,
            repeatRule: schedule.repeat,
            classroom: schedule.classroom
        )
    }

    private static func makeScheduleDTO(_ schedule: CourseScheduleDraft) -> CourseScheduleDTO {
        CourseScheduleDTO(
            id: schedule.id,
            dayOfWeek: schedule.dayOfWeek,
            startPeriod: schedule.startPeriod,
            endPeriod: schedule.endPeriod,
            startWeek: schedule.startWeek,
            endWeek: schedule.endWeek,
            repeat: schedule.repeatRule,
            classroom: schedule.classroom.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }
}
