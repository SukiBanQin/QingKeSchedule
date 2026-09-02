import Foundation

enum ScheduleRules {
    static func gregorianCalendar(timeZone: TimeZone = .current) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        calendar.timeZone = timeZone
        calendar.firstWeekday = 2
        calendar.minimumDaysInFirstWeek = 1
        return calendar
    }

    static func localDate(from value: String, calendar: Calendar) -> Date? {
        guard value.range(
            of: #"^[0-9]{4}-[0-9]{2}-[0-9]{2}$"#,
            options: .regularExpression
        ) != nil else {
            return nil
        }

        let parts = value.split(separator: "-", omittingEmptySubsequences: false)
        guard
            parts.count == 3,
            parts[0].count == 4,
            parts[1].count == 2,
            parts[2].count == 2,
            let year = Int(parts[0]),
            let month = Int(parts[1]),
            let day = Int(parts[2])
        else {
            return nil
        }

        var components = DateComponents()
        components.calendar = calendar
        components.timeZone = calendar.timeZone
        components.year = year
        components.month = month
        components.day = day

        guard let date = calendar.date(from: components) else { return nil }
        let resolved = calendar.dateComponents([.year, .month, .day], from: date)
        guard resolved.year == year, resolved.month == month, resolved.day == day else { return nil }
        return date
    }

    static func startOfMondayWeek(containing date: Date, calendar: Calendar) -> Date? {
        let startOfDay = calendar.startOfDay(for: date)
        let sundayBasedWeekday = calendar.component(.weekday, from: startOfDay)
        let daysSinceMonday = sundayBasedWeekday == 1 ? 6 : sundayBasedWeekday - 2
        return calendar.date(byAdding: .day, value: -daysSinceMonday, to: startOfDay)
    }

    static func teachingWeek(
        for date: Date,
        semester: SemesterDTO,
        calendar: Calendar
    ) -> Int? {
        guard
            let semesterStart = localDate(from: semester.startDate, calendar: calendar),
            let semesterMonday = startOfMondayWeek(containing: semesterStart, calendar: calendar),
            let dateMonday = startOfMondayWeek(containing: date, calendar: calendar),
            let dayDifference = calendar.dateComponents(
                [.day],
                from: semesterMonday,
                to: dateMonday
            ).day
        else {
            return nil
        }

        return dayDifference / 7 + 1
    }

    static func isTeachingWeekInSemester(_ week: Int, semester: SemesterDTO) -> Bool {
        (1...semester.totalWeeks).contains(week)
    }

    static func date(
        forTeachingWeek week: Int,
        dayOfWeek: Int,
        semester: SemesterDTO,
        calendar: Calendar
    ) -> Date? {
        guard
            (1...7).contains(dayOfWeek),
            let semesterStart = localDate(from: semester.startDate, calendar: calendar),
            let semesterMonday = startOfMondayWeek(containing: semesterStart, calendar: calendar)
        else {
            return nil
        }

        return calendar.date(
            byAdding: .day,
            value: (week - 1) * 7 + dayOfWeek - 1,
            to: semesterMonday
        )
    }

    static func scheduleApplies(_ schedule: CourseScheduleDTO, inWeek week: Int) -> Bool {
        guard week >= schedule.startWeek, week <= schedule.endWeek else { return false }
        switch schedule.repeat {
        case .every:
            return true
        case .odd:
            return !week.isMultiple(of: 2)
        case .even:
            return week.isMultiple(of: 2)
        }
    }

    static func occurrences(forWeek week: Int, courses: [CourseDTO]) -> [CourseOccurrenceDTO] {
        courses.flatMap { course in
            course.schedules.compactMap { schedule in
                guard scheduleApplies(schedule, inWeek: week) else { return nil }
                return CourseOccurrenceDTO(course: course, schedule: schedule)
            }
        }
    }

    static func occurrences(
        for date: Date,
        semester: SemesterDTO,
        courses: [CourseDTO],
        calendar: Calendar
    ) -> [CourseOccurrenceDTO] {
        guard
            let week = teachingWeek(for: date, semester: semester, calendar: calendar),
            isTeachingWeekInSemester(week, semester: semester)
        else {
            return []
        }

        let sundayBasedWeekday = calendar.component(.weekday, from: date)
        let dayOfWeek = sundayBasedWeekday == 1 ? 7 : sundayBasedWeekday - 1
        let matching = occurrences(forWeek: week, courses: courses).filter {
            $0.schedule.dayOfWeek == dayOfWeek
        }

        return matching.enumerated().sorted { left, right in
            if left.element.schedule.startPeriod != right.element.schedule.startPeriod {
                return left.element.schedule.startPeriod < right.element.schedule.startPeriod
            }
            if left.element.schedule.endPeriod != right.element.schedule.endPeriod {
                return left.element.schedule.endPeriod < right.element.schedule.endPeriod
            }

            let nameOrder = left.element.course.name.compare(
                right.element.course.name,
                options: [],
                range: nil,
                locale: Locale(identifier: "zh_CN")
            )
            if nameOrder != .orderedSame {
                return nameOrder == .orderedAscending
            }
            return left.offset < right.offset
        }.map(\.element)
    }

    static func occurrenceStatus(
        _ occurrence: CourseOccurrenceDTO,
        semester: SemesterDTO,
        now: Date,
        calendar: Calendar
    ) -> CourseStatus {
        guard
            let startPeriod = semester.periods.first(where: {
                $0.number == occurrence.schedule.startPeriod
            }),
            let endPeriod = semester.periods.first(where: {
                $0.number == occurrence.schedule.endPeriod
            }),
            let startMinutes = minutes(from: startPeriod.startTime),
            let endMinutes = minutes(from: endPeriod.endTime)
        else {
            return .upcoming
        }

        let components = calendar.dateComponents([.hour, .minute], from: now)
        guard let hour = components.hour, let minute = components.minute else {
            return .upcoming
        }
        let currentMinutes = hour * 60 + minute

        if currentMinutes < startMinutes { return .upcoming }
        if currentMinutes <= endMinutes { return .ongoing }
        return .finished
    }

    static func periodRangesOverlap(
        _ left: CourseScheduleDTO,
        _ right: CourseScheduleDTO
    ) -> Bool {
        left.startPeriod <= right.endPeriod && right.startPeriod <= left.endPeriod
    }

    static func overlappingWeeks(
        _ left: CourseScheduleDTO,
        _ right: CourseScheduleDTO
    ) -> [Int] {
        let firstWeek = max(left.startWeek, right.startWeek)
        let lastWeek = min(left.endWeek, right.endWeek)
        guard firstWeek <= lastWeek else { return [] }

        return (firstWeek...lastWeek).filter {
            scheduleApplies(left, inWeek: $0) && scheduleApplies(right, inWeek: $0)
        }
    }

    static func schedulesConflict(
        _ left: CourseScheduleDTO,
        _ right: CourseScheduleDTO
    ) -> Bool {
        left.dayOfWeek == right.dayOfWeek
            && periodRangesOverlap(left, right)
            && !overlappingWeeks(left, right).isEmpty
    }

    static func conflicts(
        for candidate: CourseDTO,
        against existingCourses: [CourseDTO]
    ) -> [ScheduleConflictDTO] {
        var conflicts: [ScheduleConflictDTO] = []

        for candidateSchedule in candidate.schedules {
            for existingCourse in existingCourses where existingCourse.id != candidate.id {
                for existingSchedule in existingCourse.schedules {
                    guard schedulesConflict(candidateSchedule, existingSchedule) else { continue }
                    conflicts.append(
                        ScheduleConflictDTO(
                            candidateCourse: candidate,
                            candidateSchedule: candidateSchedule,
                            existingCourse: existingCourse,
                            existingSchedule: existingSchedule,
                            weeks: overlappingWeeks(candidateSchedule, existingSchedule)
                        )
                    )
                }
            }
        }

        return conflicts
    }

    static func minutes(from value: String) -> Int? {
        guard value.range(
            of: #"^([01][0-9]|2[0-3]):[0-5][0-9]$"#,
            options: .regularExpression
        ) != nil else {
            return nil
        }

        let parts = value.split(separator: ":", omittingEmptySubsequences: false)
        guard
            parts.count == 2,
            parts[0].count == 2,
            parts[1].count == 2,
            let hour = Int(parts[0]),
            let minute = Int(parts[1]),
            (0...23).contains(hour),
            (0...59).contains(minute)
        else {
            return nil
        }
        return hour * 60 + minute
    }
}
