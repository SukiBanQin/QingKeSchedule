import Foundation
import Testing
@testable import QingKeSchedule

@Suite("今日与周课表展示")
struct SchedulePresentationTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("今日展示排序、状态并只标记下一门课程")
    func todayPresentation() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let presentation = TodaySchedulePresentation(
            semester: semester,
            courses: data.courses,
            now: try date(2026, 8, 31, hour: 9, minute: 41),
            calendar: calendar
        )

        #expect(presentation.teachingWeek == 1)
        #expect(presentation.items.map(\.occurrence.schedule.id) == [
            "schedule-every",
            "schedule-odd",
            "schedule-alpha",
            "schedule-beta",
        ])
        #expect(presentation.items.map(\.status) == [.finished, .ongoing, .upcoming, .upcoming])
        #expect(presentation.items.filter(\.isNext).map(\.id) == ["schedule-alpha"])
    }

    @Test("学期外与学期内无课有不同空状态")
    func emptyStates() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)

        let outside = TodaySchedulePresentation(
            semester: semester,
            courses: data.courses,
            now: try date(2026, 8, 30),
            calendar: calendar
        )
        #expect(outside.items.isEmpty)
        #expect(outside.emptyMessage.contains("不在"))

        let noClass = TodaySchedulePresentation(
            semester: semester,
            courses: [],
            now: try date(2026, 8, 31),
            calendar: calendar
        )
        #expect(noClass.emptyMessage.contains("没有课程"))
    }

    @Test("周课表应用单双周并只标记所选周真实冲突")
    func weekPresentation() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let now = try date(2026, 9, 7)

        let oddWeek = WeekSchedulePresentation(
            week: 1,
            semester: semester,
            courses: data.courses,
            now: now,
            calendar: calendar
        )
        let oddMonday = oddWeek.days[0].items
        #expect(oddMonday.map(\.occurrence.course.id).contains("course-odd"))
        #expect(!oddMonday.map(\.occurrence.course.id).contains("course-even"))
        #expect(oddMonday.first { $0.occurrence.course.id == "course-every" }?.isConflicting == true)
        #expect(oddMonday.first { $0.occurrence.course.id == "course-odd" }?.isConflicting == true)

        let evenWeek = WeekSchedulePresentation(
            week: 2,
            semester: semester,
            courses: data.courses,
            now: now,
            calendar: calendar
        )
        let evenMonday = evenWeek.days[0].items
        #expect(!evenMonday.map(\.occurrence.course.id).contains("course-odd"))
        #expect(evenMonday.map(\.occurrence.course.id).contains("course-even"))
        #expect(evenWeek.currentWeek == 2)
        #expect(evenWeek.days[2].items.map(\.occurrence.schedule.id) == ["schedule-wednesday"])
    }

    @Test("默认周次限制在学期范围内")
    func initialWeekIsClamped() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        #expect(WeekSchedulePresentation.initialWeek(
            semester: semester,
            now: try date(2026, 8, 1),
            calendar: calendar
        ) == 1)
        #expect(WeekSchedulePresentation.initialWeek(
            semester: semester,
            now: try date(2027, 2, 1),
            calendar: calendar
        ) == 18)
    }

    private func date(
        _ year: Int,
        _ month: Int,
        _ day: Int,
        hour: Int = 0,
        minute: Int = 0
    ) throws -> Date {
        try #require(calendar.date(from: DateComponents(
            timeZone: calendar.timeZone,
            year: year,
            month: month,
            day: day,
            hour: hour,
            minute: minute
        )))
    }
}
