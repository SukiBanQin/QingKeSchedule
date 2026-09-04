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
        let current = try #require(presentation.items.first { $0.id == "schedule-odd" })
        #expect(current.timingProgress == CourseTimingProgress(
            elapsedMinutes: 46,
            remainingMinutes: 64,
            fraction: 46.0 / 110.0
        ))
        #expect(presentation.items.first?.timingProgress == nil)
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

        let matrix = WeekMatrixPresentation(semester: semester, days: oddWeek.days)
        #expect(matrix.periods.map(\.number) == [1, 2, 3, 4])
        #expect(matrix.items.count == 6)
        let every = try #require(matrix.items.first {
            $0.occurrence.schedule.id == "schedule-every"
        })
        let odd = try #require(matrix.items.first {
            $0.occurrence.schedule.id == "schedule-odd"
        })
        #expect(every.dayColumn == 0)
        #expect(every.startRow == 0)
        #expect(every.rowSpan == 2)
        #expect(every.laneCount == 2)
        #expect(odd.lane != every.lane)
        let wednesday = try #require(matrix.items.first {
            $0.occurrence.schedule.id == "schedule-wednesday"
        })
        #expect(wednesday.dayColumn == 2)
        #expect(wednesday.startRow == 2)
        #expect(wednesday.rowSpan == 1)
        #expect(wednesday.laneCount == 1)
    }

    @Test("停课日不显示课程，调课日按指定星期课表展示")
    func calendarExceptionsAffectTodayAndWeek() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let monday = try date(2026, 8, 31, hour: 9)
        let saturday = try date(2026, 9, 5)
        var settings = AcademicCalendarSettings.defaults
        settings.setNonTeaching(monday, calendar: calendar)
        settings.setMakeupTeachingDay(saturday, followsDayOfWeek: 1, calendar: calendar)

        let today = TodaySchedulePresentation(
            semester: semester,
            courses: data.courses,
            now: monday,
            academicCalendarSettings: settings,
            calendar: calendar
        )
        #expect(today.isNonTeachingDay)
        #expect(today.items.isEmpty)
        #expect(today.emptyMessage.contains("停课"))

        let week = WeekSchedulePresentation(
            week: 1,
            semester: semester,
            courses: data.courses,
            now: monday,
            academicCalendarSettings: settings,
            calendar: calendar
        )
        #expect(week.days[0].isNonTeachingDay)
        #expect(week.days[0].items.isEmpty)
        #expect(week.days[5].scheduleSourceDayOfWeek == 1)
        #expect(!week.days[5].isNonTeachingDay)
        #expect(week.days[5].items.map(\.occurrence.course.id).contains("course-every"))
        #expect(week.days[5].items.allSatisfy { $0.displayDayOfWeek == 6 })
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

    @Test("周课表摘要和课程紧凑信息使用真实数据")
    func weekMatrixLabels() throws {
        let data = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(data.semester)
        let course = try #require(data.courses.first { !$0.teacher.isEmpty })
        let schedule = try #require(course.schedules.first { !$0.classroom.isEmpty })

        #expect(ScheduleDisplayText.weekMatrixSummary(periodCount: 4) == "MON–FRI / 4 PERIODS")
        #expect(
            ScheduleDisplayText.compactCourseDetails(course: course, schedule: schedule)
                == "\(schedule.classroom) · \(course.teacher)"
        )

        let week = WeekSchedulePresentation(
            week: 1,
            semester: semester,
            courses: data.courses,
            now: try date(2026, 8, 31),
            calendar: calendar
        )
        var settings = AcademicCalendarSettings.defaults
        settings.lunchBreak.startTime = "09:40"
        settings.lunchBreak.endTime = "10:00"
        let matrix = WeekMatrixPresentation(
            semester: semester,
            days: week.days,
            academicCalendarSettings: settings
        )
        #expect(matrix.scheduleBreak == WeekMatrixBreak(
            title: "午休",
            startTime: "09:40",
            endTime: "10:00",
            insertionRow: 2
        ))

        settings.lunchBreak.isEnabled = false
        #expect(WeekMatrixPresentation(
            semester: semester,
            days: week.days,
            academicCalendarSettings: settings
        ).scheduleBreak == nil)
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
