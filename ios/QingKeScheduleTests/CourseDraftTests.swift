import Foundation
import Testing
@testable import QingKeSchedule

@Suite("课程表单")
struct CourseDraftTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("新课程默认使用当天、首节和整个学期")
    func newCourseDefaults() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let now = try date(2026, 9, 3)
        var draft = CourseDraft(
            semester: semester,
            now: now,
            calendar: calendar
        )

        #expect(draft.schedules.count == 1)
        #expect(draft.schedules[0].dayOfWeek == 4)
        #expect(draft.schedules[0].startPeriod == 1)
        #expect(draft.schedules[0].endWeek == semester.totalWeeks)
        #expect(draft.validationIssues(semester: semester, calendar: calendar).contains {
            $0.message == "请填写课程名称"
        })

        draft.name = "  移动开发  "
        draft.teacher = "  李老师  "
        #expect(draft.evaluateSave(
            semester: semester,
            existingCourses: [],
            calendar: calendar
        ) == .ready)
        #expect(draft.course().name == "移动开发")
        #expect(draft.course().teacher == "李老师")
    }

    @Test("一门课程可以增删多个安排且标识互不重复")
    func multipleSchedules() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        var draft = CourseDraft(semester: semester, now: try date(2026, 8, 31), calendar: calendar)
        let original = draft.schedules[0]

        draft.addSchedule(copying: original)
        #expect(draft.schedules.count == 2)
        #expect(draft.schedules[0].id != draft.schedules[1].id)
        #expect(draft.schedules[1].dayOfWeek == original.dayOfWeek)

        draft.removeSchedule(id: original.id)
        #expect(draft.schedules.count == 1)
        draft.removeSchedule(id: draft.schedules[0].id)
        #expect(draft.schedules.count == 1)
    }

    @Test("冲突需要确认，但编辑课程不会与自身冲突")
    func conflictEvaluation() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        var draft = CourseDraft(semester: semester, now: try date(2026, 8, 31), calendar: calendar)
        draft.name = "编译原理"

        let evaluation = draft.evaluateSave(
            semester: semester,
            existingCourses: data.courses,
            calendar: calendar
        )
        guard case .conflicting(let conflicts) = evaluation else {
            Issue.record("应检测到与现有课程的冲突")
            return
        }
        #expect(conflicts.contains { $0.existingCourse.id == "course-every" })

        let existing = try #require(data.courses.first)
        let editingDraft = CourseDraft(
            course: existing,
            semester: semester,
            now: try date(2026, 8, 31),
            calendar: calendar
        )
        #expect(editingDraft.evaluateSave(
            semester: semester,
            existingCourses: [existing],
            calendar: calendar
        ) == .ready)
        #expect(!editingDraft.isDirty)
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) throws -> Date {
        try #require(calendar.date(from: DateComponents(
            timeZone: calendar.timeZone,
            year: year,
            month: month,
            day: day
        )))
    }
}
