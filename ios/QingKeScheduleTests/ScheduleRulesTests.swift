import Foundation
import Testing
@testable import QingKeSchedule

@Suite("跨端课程领域规则")
struct ScheduleRulesTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("学期开始日所在周为第一周，并保留学期外周数")
    func teachingWeekBoundaries() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)

        #expect(ScheduleRules.teachingWeek(
            for: try date(2026, 8, 31),
            semester: semester,
            calendar: calendar
        ) == 1)
        #expect(ScheduleRules.teachingWeek(
            for: try date(2026, 9, 6),
            semester: semester,
            calendar: calendar
        ) == 1)
        #expect(ScheduleRules.teachingWeek(
            for: try date(2026, 9, 7),
            semester: semester,
            calendar: calendar
        ) == 2)
        #expect(ScheduleRules.teachingWeek(
            for: try date(2026, 8, 30),
            semester: semester,
            calendar: calendar
        ) == 0)
        #expect(ScheduleRules.teachingWeek(
            for: try date(2026, 12, 28),
            semester: semester,
            calendar: calendar
        ) == 18)
        #expect(ScheduleRules.teachingWeek(
            for: try date(2027, 1, 4),
            semester: semester,
            calendar: calendar
        ) == 19)
    }

    @Test("跨越夏令时仍按日历周计算")
    func teachingWeekAcrossDaylightSavingTime() throws {
        let losAngelesCalendar = ScheduleRules.gregorianCalendar(
            timeZone: try #require(TimeZone(identifier: "America/Los_Angeles"))
        )
        let semester = SemesterDTO(
            id: "dst-semester",
            name: "DST Semester",
            startDate: "2026-03-04",
            totalWeeks: 18,
            periods: [PeriodDTO(number: 1, startTime: "08:00", endTime: "08:45")]
        )
        let secondMonday = try #require(losAngelesCalendar.date(from: DateComponents(
            timeZone: losAngelesCalendar.timeZone,
            year: 2026,
            month: 3,
            day: 9
        )))

        #expect(ScheduleRules.teachingWeek(
            for: secondMonday,
            semester: semester,
            calendar: losAngelesCalendar
        ) == 2)
    }

    @Test("每周、单周和双周遵守范围边界")
    func repeatRules() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let every = try schedule(id: "schedule-every", in: data)
        let odd = try schedule(id: "schedule-odd", in: data)
        let even = try schedule(id: "schedule-even", in: data)

        #expect(ScheduleRules.scheduleApplies(every, inWeek: 2))
        #expect(ScheduleRules.scheduleApplies(odd, inWeek: 3))
        #expect(!ScheduleRules.scheduleApplies(odd, inWeek: 2))
        #expect(!ScheduleRules.scheduleApplies(odd, inWeek: 6))
        #expect(ScheduleRules.scheduleApplies(even, inWeek: 2))
        #expect(!ScheduleRules.scheduleApplies(even, inWeek: 3))
    }

    @Test("今日课程按开始节次、结束节次和名称稳定排序")
    func todayOccurrencesAreSorted() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let occurrences = ScheduleRules.occurrences(
            for: try date(2026, 8, 31, hour: 8),
            semester: semester,
            courses: data.courses,
            calendar: calendar
        )

        #expect(occurrences.map(\.schedule.id) == [
            "schedule-every",
            "schedule-odd",
            "schedule-alpha",
            "schedule-beta",
        ])
        #expect(ScheduleRules.occurrences(
            for: try date(2026, 8, 30),
            semester: semester,
            courses: data.courses,
            calendar: calendar
        ).isEmpty)
    }

    @Test("课程状态包含开始和结束分钟边界")
    func occurrenceStatusBoundaries() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let occurrence = CourseOccurrenceDTO(
            course: try course(id: "course-every", in: data),
            schedule: try schedule(id: "schedule-every", in: data)
        )

        #expect(status(atHour: 7, minute: 59, occurrence: occurrence, semester: semester) == .upcoming)
        #expect(status(atHour: 8, minute: 0, occurrence: occurrence, semester: semester) == .ongoing)
        #expect(status(atHour: 9, minute: 40, occurrence: occurrence, semester: semester) == .ongoing)
        #expect(status(atHour: 9, minute: 41, occurrence: occurrence, semester: semester) == .finished)
    }

    @Test("冲突同时考虑星期、节次、周次和单双周")
    func conflictsUseAllDimensions() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let every = try schedule(id: "schedule-every", in: data)
        let odd = try schedule(id: "schedule-odd", in: data)
        let even = try schedule(id: "schedule-even", in: data)

        #expect(ScheduleRules.schedulesConflict(every, odd))
        #expect(ScheduleRules.overlappingWeeks(every, odd) == [1, 3, 5])
        #expect(!ScheduleRules.schedulesConflict(odd, even))

        let candidate = try course(id: "course-odd", in: data)
        let conflicts = ScheduleRules.conflicts(
            for: candidate,
            against: [try course(id: "course-every", in: data)]
        )
        #expect(conflicts.count == 1)
        #expect(conflicts.first?.weeks == [1, 3, 5])
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

    private func status(
        atHour hour: Int,
        minute: Int,
        occurrence: CourseOccurrenceDTO,
        semester: SemesterDTO
    ) -> CourseStatus {
        let now = calendar.date(from: DateComponents(
            timeZone: calendar.timeZone,
            year: 2026,
            month: 8,
            day: 31,
            hour: hour,
            minute: minute
        ))!
        return ScheduleRules.occurrenceStatus(
            occurrence,
            semester: semester,
            now: now,
            calendar: calendar
        )
    }

    private func course(id: String, in data: ScheduleDataDTO) throws -> CourseDTO {
        try #require(data.courses.first(where: { $0.id == id }))
    }

    private func schedule(id: String, in data: ScheduleDataDTO) throws -> CourseScheduleDTO {
        try #require(data.courses.flatMap(\.schedules).first(where: { $0.id == id }))
    }
}
