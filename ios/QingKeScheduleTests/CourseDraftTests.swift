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

    @Test("复用已有课程时只新增安排并继承课程资料")
    func appendingScheduleReusesCourseProfile() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let existing = try #require(data.courses.first)
        let originalScheduleIDs = Set(existing.schedules.map(\.id))
        let draft = CourseDraft(
            course: existing,
            appendingSchedule: true,
            semester: semester,
            now: try date(2026, 9, 4),
            calendar: calendar
        )

        #expect(draft.id == existing.id)
        #expect(draft.name == existing.name)
        #expect(draft.teacher == existing.teacher)
        #expect(draft.color == existing.color)
        #expect(draft.schedules.count == existing.schedules.count + 1)
        #expect(!originalScheduleIDs.contains(try #require(draft.schedules.last).id))
        #expect(draft.schedules.last?.dayOfWeek == 5)
        #expect(draft.isDirty)
    }

    @Test("新课程新增完全重复安排会被阻止")
    func newCourseDuplicateScheduleIsInvalid() throws {
        let semester = testSemester()
        var draft = CourseDraft(
            semester: semester,
            now: try date(2026, 8, 31),
            calendar: calendar
        )
        draft.name = "编译原理"
        draft.schedules[0].classroom = "A101"
        draft.addSchedule(copying: draft.schedules[0])
        draft.schedules[1].classroom = "  A101  "

        let evaluation = draft.evaluateSave(
            semester: semester,
            existingCourses: [],
            calendar: calendar
        )

        assertDuplicateSchedule(evaluation)
    }

    @Test("复用已有课程新增完全重复安排会被阻止")
    func appendedDuplicateScheduleIsInvalid() throws {
        let semester = testSemester()
        let existing = testCourse(
            id: "course-existing",
            schedules: [testSchedule(id: "schedule-existing")]
        )
        let draft = CourseDraft(
            course: existing,
            appendingSchedule: true,
            semester: semester,
            now: try date(2026, 8, 31),
            calendar: calendar
        )

        let evaluation = draft.evaluateSave(
            semester: semester,
            existingCourses: [existing],
            calendar: calendar
        )

        assertDuplicateSchedule(evaluation)
    }

    @Test("编辑已有安排为同课程重复安排会被阻止")
    func editingScheduleIntoDuplicateIsInvalid() throws {
        let semester = testSemester()
        let original = testSchedule(id: "schedule-original")
        let other = testSchedule(
            id: "schedule-other",
            dayOfWeek: 2,
            startPeriod: 2,
            endPeriod: 2,
            classroom: "B202"
        )
        let existing = testCourse(
            id: "course-existing",
            schedules: [original, other]
        )
        var draft = CourseDraft(
            course: existing,
            semester: semester,
            now: try date(2026, 8, 31),
            calendar: calendar
        )
        draft.schedules[1].dayOfWeek = draft.schedules[0].dayOfWeek
        draft.schedules[1].startPeriod = draft.schedules[0].startPeriod
        draft.schedules[1].endPeriod = draft.schedules[0].endPeriod
        draft.schedules[1].startWeek = draft.schedules[0].startWeek
        draft.schedules[1].endWeek = draft.schedules[0].endWeek
        draft.schedules[1].repeatRule = draft.schedules[0].repeatRule
        draft.schedules[1].classroom = draft.schedules[0].classroom

        let evaluation = draft.evaluateSave(
            semester: semester,
            existingCourses: [existing],
            calendar: calendar
        )

        assertDuplicateSchedule(evaluation)
    }

    @Test("历史重复安排未增加时可保存，新增后会被阻止")
    func historicalDuplicatesCanBeEditedButNotIncreased() throws {
        let semester = testSemester()
        let repeated = testSchedule(id: "schedule-legacy-1")
        let existing = testCourse(
            id: "course-legacy",
            schedules: [
                repeated,
                testSchedule(id: "schedule-legacy-2"),
            ]
        )
        var draft = CourseDraft(
            course: existing,
            semester: semester,
            now: try date(2026, 8, 31),
            calendar: calendar
        )
        draft.name = "历史课程（已更新）"

        #expect(draft.evaluateSave(
            semester: semester,
            existingCourses: [existing],
            calendar: calendar
        ) == .ready)

        draft.addSchedule(copying: draft.schedules[0])
        assertDuplicateSchedule(draft.evaluateSave(
            semester: semester,
            existingCourses: [existing],
            calendar: calendar
        ))
    }

    @Test("不同重复规则或教室的安排不是完全重复")
    func schedulesWithDifferentRepeatOrClassroomAreAllowed() throws {
        let semester = testSemester()
        var draft = CourseDraft(
            semester: semester,
            now: try date(2026, 8, 31),
            calendar: calendar
        )
        draft.name = "数据库"
        let original = draft.schedules[0]
        draft.addSchedule(copying: original)
        draft.schedules[1].repeatRule = .odd
        draft.addSchedule(copying: original)
        draft.schedules[2].classroom = "B202"

        #expect(draft.evaluateSave(
            semester: semester,
            existingCourses: [],
            calendar: calendar
        ) == .ready)
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

    private func assertDuplicateSchedule(_ evaluation: CourseSaveEvaluation) {
        guard case .invalid(let issues) = evaluation else {
            Issue.record("完全重复安排应阻止保存")
            return
        }
        #expect(issues.contains {
            $0.path == "courses.0.schedules"
                && $0.message == "该上课安排已存在，请勿重复添加"
        })
    }

    private func testSemester() -> SemesterDTO {
        SemesterDTO(
            id: "semester-test",
            name: "测试学期",
            startDate: "2026-09-01",
            totalWeeks: 18,
            periods: [
                PeriodDTO(number: 1, startTime: "08:00", endTime: "08:45"),
                PeriodDTO(number: 2, startTime: "08:55", endTime: "09:40"),
            ]
        )
    }

    private func testCourse(id: String, schedules: [CourseScheduleDTO]) -> CourseDTO {
        CourseDTO(
            id: id,
            name: "测试课程",
            teacher: "测试教师",
            color: "#287B74",
            schedules: schedules
        )
    }

    private func testSchedule(
        id: String,
        dayOfWeek: Int = 1,
        startPeriod: Int = 1,
        endPeriod: Int = 1,
        startWeek: Int = 1,
        endWeek: Int = 18,
        repeatRule: RepeatRule = .every,
        classroom: String = ""
    ) -> CourseScheduleDTO {
        CourseScheduleDTO(
            id: id,
            dayOfWeek: dayOfWeek,
            startPeriod: startPeriod,
            endPeriod: endPeriod,
            startWeek: startWeek,
            endWeek: endWeek,
            repeat: repeatRule,
            classroom: classroom
        )
    }
}
