import Foundation

struct TodayCourseItem: Equatable, Identifiable {
    let occurrence: CourseOccurrenceDTO
    let status: CourseStatus
    let isNext: Bool

    var id: String { occurrence.schedule.id }
}

struct TodaySchedulePresentation: Equatable {
    let teachingWeek: Int?
    let items: [TodayCourseItem]
    let emptyMessage: String

    init(
        semester: SemesterDTO,
        courses: [CourseDTO],
        now: Date,
        calendar: Calendar
    ) {
        let calculatedWeek = ScheduleRules.teachingWeek(
            for: now,
            semester: semester,
            calendar: calendar
        )
        teachingWeek = calculatedWeek

        let occurrences = ScheduleRules.occurrences(
            for: now,
            semester: semester,
            courses: courses,
            calendar: calendar
        )
        let statuses = occurrences.map {
            ScheduleRules.occurrenceStatus(
                $0,
                semester: semester,
                now: now,
                calendar: calendar
            )
        }
        let nextIndex = statuses.firstIndex(of: .upcoming)
        items = occurrences.enumerated().map { index, occurrence in
            TodayCourseItem(
                occurrence: occurrence,
                status: statuses[index],
                isNext: index == nextIndex
            )
        }

        if let calculatedWeek,
           ScheduleRules.isTeachingWeekInSemester(calculatedWeek, semester: semester) {
            emptyMessage = "今天没有课程，享受空闲时间吧。"
        } else {
            emptyMessage = "当前日期不在这个学期内。"
        }
    }
}

struct WeekCourseItem: Equatable, Identifiable {
    let occurrence: CourseOccurrenceDTO
    let isConflicting: Bool

    var id: String { occurrence.schedule.id }
}

struct WeekDayPresentation: Equatable, Identifiable {
    let dayOfWeek: Int
    let date: Date?
    let items: [WeekCourseItem]

    var id: Int { dayOfWeek }
}

struct WeekSchedulePresentation: Equatable {
    let week: Int
    let currentWeek: Int?
    let days: [WeekDayPresentation]

    init(
        week: Int,
        semester: SemesterDTO,
        courses: [CourseDTO],
        now: Date,
        calendar: Calendar
    ) {
        let resolvedWeek = min(max(week, 1), semester.totalWeeks)
        self.week = resolvedWeek
        let calculatedCurrentWeek = ScheduleRules.teachingWeek(
            for: now,
            semester: semester,
            calendar: calendar
        )
        currentWeek = calculatedCurrentWeek.flatMap {
            ScheduleRules.isTeachingWeekInSemester($0, semester: semester) ? $0 : nil
        }

        let occurrences = ScheduleRules.occurrences(forWeek: resolvedWeek, courses: courses)
        let conflictIDs = Self.conflictingScheduleIDs(in: occurrences, week: resolvedWeek)
        days = (1...7).map { dayOfWeek in
            let dayOccurrences = occurrences
                .filter { $0.schedule.dayOfWeek == dayOfWeek }
                .sorted(by: Self.stableOccurrenceOrder)
            return WeekDayPresentation(
                dayOfWeek: dayOfWeek,
                date: ScheduleRules.date(
                    forTeachingWeek: resolvedWeek,
                    dayOfWeek: dayOfWeek,
                    semester: semester,
                    calendar: calendar
                ),
                items: dayOccurrences.map {
                    WeekCourseItem(
                        occurrence: $0,
                        isConflicting: conflictIDs.contains($0.schedule.id)
                    )
                }
            )
        }
    }

    static func initialWeek(
        semester: SemesterDTO,
        now: Date,
        calendar: Calendar
    ) -> Int {
        guard let current = ScheduleRules.teachingWeek(
            for: now,
            semester: semester,
            calendar: calendar
        ) else {
            return 1
        }
        return min(max(current, 1), semester.totalWeeks)
    }

    private static func conflictingScheduleIDs(
        in occurrences: [CourseOccurrenceDTO],
        week: Int
    ) -> Set<String> {
        var identifiers = Set<String>()
        for leftIndex in occurrences.indices {
            for rightIndex in occurrences.indices where rightIndex > leftIndex {
                let left = occurrences[leftIndex]
                let right = occurrences[rightIndex]
                guard left.course.id != right.course.id,
                      ScheduleRules.scheduleApplies(left.schedule, inWeek: week),
                      ScheduleRules.scheduleApplies(right.schedule, inWeek: week),
                      left.schedule.dayOfWeek == right.schedule.dayOfWeek,
                      ScheduleRules.periodRangesOverlap(left.schedule, right.schedule)
                else {
                    continue
                }
                identifiers.insert(left.schedule.id)
                identifiers.insert(right.schedule.id)
            }
        }
        return identifiers
    }

    private static func stableOccurrenceOrder(
        _ left: CourseOccurrenceDTO,
        _ right: CourseOccurrenceDTO
    ) -> Bool {
        if left.schedule.startPeriod != right.schedule.startPeriod {
            return left.schedule.startPeriod < right.schedule.startPeriod
        }
        if left.schedule.endPeriod != right.schedule.endPeriod {
            return left.schedule.endPeriod < right.schedule.endPeriod
        }
        if left.course.name != right.course.name {
            return left.course.name.localizedCompare(right.course.name) == .orderedAscending
        }
        return left.schedule.id < right.schedule.id
    }
}
