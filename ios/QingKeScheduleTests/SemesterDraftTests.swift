import Foundation
import Testing
@testable import QingKeSchedule

@Suite("学期表单")
struct SemesterDraftTests {
    private let timeZone = TimeZone(secondsFromGMT: 8 * 60 * 60)!

    @Test("新学期使用与 Web 一致的默认节次")
    func defaultsMatchWeb() throws {
        let calendar = ScheduleRules.gregorianCalendar(timeZone: timeZone)
        let now = try #require(
            ScheduleRules.localDate(from: "2026-09-03", calendar: calendar)
        )

        let semester = SemesterDraft(now: now, calendar: calendar).semester(calendar: calendar)

        #expect(semester.name == "2026 秋季学期")
        #expect(semester.startDate == "2026-09-03")
        #expect(semester.totalWeeks == 18)
        #expect(semester.periods.count == 10)
        #expect(semester.periods.first == PeriodDTO(number: 1, startTime: "08:00", endTime: "08:45"))
        #expect(semester.periods.last == PeriodDTO(number: 10, startTime: "19:55", endTime: "20:40"))
    }

    @Test("增删节次会连续编号并生成合理默认时间")
    func addAndRemovePeriod() throws {
        let calendar = ScheduleRules.gregorianCalendar(timeZone: timeZone)
        var draft = SemesterDraft(now: Date(), calendar: calendar)
        let removedID = draft.periods[4].id

        draft.removePeriod(id: removedID)
        #expect(draft.periods.map(\.number) == Array(1...9))

        draft.addPeriod(calendar: calendar)
        let semester = draft.semester(calendar: calendar)
        #expect(semester.periods.last?.number == 10)
        #expect(semester.periods.last?.startTime == "20:50")
        #expect(semester.periods.last?.endTime == "21:35")
    }

    @Test("表单保留已有学期标识并报告校验错误")
    func editingAndValidation() {
        let calendar = ScheduleRules.gregorianCalendar(timeZone: timeZone)
        let existing = SemesterDTO(
            id: "semester-existing",
            name: "原学期",
            startDate: "2026-02-23",
            totalWeeks: 18,
            periods: [PeriodDTO(number: 1, startTime: "08:00", endTime: "08:45")]
        )
        var draft = SemesterDraft(semester: existing, calendar: calendar)
        draft.name = "  修改后的学期  "
        #expect(draft.semester(calendar: calendar).id == existing.id)
        #expect(draft.semester(calendar: calendar).name == "修改后的学期")

        draft.name = "   "
        #expect(draft.validationIssues(calendar: calendar).contains { $0.message == "请填写学期名称" })
    }
}
