import Foundation

struct TodayCourseItem: Equatable, Identifiable {
    let occurrence: CourseOccurrenceDTO
    let status: CourseStatus
    let isNext: Bool
    let timingProgress: CourseTimingProgress?

    var id: String { occurrence.schedule.id }
}

struct CourseTimingProgress: Equatable {
    let elapsedSeconds: Int
    let remainingSeconds: Int
    let fraction: Double

    var elapsedMinutes: Int { elapsedSeconds / 60 }

    var remainingClockText: String {
        String(format: "%d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    }
}

struct TodaySchedulePresentation: Equatable {
    let teachingWeek: Int?
    let items: [TodayCourseItem]
    let emptyMessage: String
    let isNonTeachingDay: Bool

    init(
        semester: SemesterDTO,
        courses: [CourseDTO],
        now: Date,
        academicCalendarSettings: AcademicCalendarSettings = .defaults,
        calendar: Calendar
    ) {
        let calculatedWeek = ScheduleRules.teachingWeek(
            for: now,
            semester: semester,
            calendar: calendar
        )
        teachingWeek = calculatedWeek

        let resolution = academicCalendarSettings.resolution(for: now, calendar: calendar)
        let occurrences: [CourseOccurrenceDTO]
        switch resolution {
        case .nonTeaching:
            isNonTeachingDay = true
            occurrences = []
        case .teaching(let sourceDayOfWeek, _):
            isNonTeachingDay = false
            if let calculatedWeek,
               ScheduleRules.isTeachingWeekInSemester(calculatedWeek, semester: semester) {
                occurrences = ScheduleRules.occurrences(
                    forWeek: calculatedWeek,
                    courses: courses
                )
                .filter { $0.schedule.dayOfWeek == sourceDayOfWeek }
                .enumerated()
                .sorted(by: Self.stableOccurrenceOrder)
                .map(\.element)
            } else {
                occurrences = []
            }
        }
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
                isNext: index == nextIndex,
                timingProgress: Self.timingProgress(
                    occurrence: occurrence,
                    status: statuses[index],
                    semester: semester,
                    now: now,
                    calendar: calendar
                )
            )
        }

        if case .nonTeaching(let reason) = resolution,
           let calculatedWeek,
           ScheduleRules.isTeachingWeekInSemester(calculatedWeek, semester: semester) {
            emptyMessage = "\(reason)，今日不显示课程。"
        } else if let calculatedWeek,
           ScheduleRules.isTeachingWeekInSemester(calculatedWeek, semester: semester) {
            emptyMessage = "今天没有课程，享受空闲时间吧。"
        } else {
            emptyMessage = "当前日期不在这个学期内。"
        }
    }

    private static func stableOccurrenceOrder(
        _ left: (offset: Int, element: CourseOccurrenceDTO),
        _ right: (offset: Int, element: CourseOccurrenceDTO)
    ) -> Bool {
        if left.element.schedule.startPeriod != right.element.schedule.startPeriod {
            return left.element.schedule.startPeriod < right.element.schedule.startPeriod
        }
        if left.element.schedule.endPeriod != right.element.schedule.endPeriod {
            return left.element.schedule.endPeriod < right.element.schedule.endPeriod
        }
        let nameOrder = left.element.course.name.compare(
            right.element.course.name,
            locale: Locale(identifier: "zh_CN")
        )
        if nameOrder != .orderedSame { return nameOrder == .orderedAscending }
        return left.offset < right.offset
    }

    private static func timingProgress(
        occurrence: CourseOccurrenceDTO,
        status: CourseStatus,
        semester: SemesterDTO,
        now: Date,
        calendar: Calendar
    ) -> CourseTimingProgress? {
        guard status == .ongoing,
              let startPeriod = semester.periods.first(where: {
                  $0.number == occurrence.schedule.startPeriod
              }),
              let endPeriod = semester.periods.first(where: {
                  $0.number == occurrence.schedule.endPeriod
              }),
              let startMinutes = ScheduleRules.minutes(from: startPeriod.startTime),
              let endMinutes = ScheduleRules.minutes(from: endPeriod.endTime)
        else {
            return nil
        }

        let startOfDay = calendar.startOfDay(for: now)
        guard let startDate = calendar.date(
            byAdding: .second,
            value: startMinutes * 60,
            to: startOfDay
        ), var endDate = calendar.date(
            byAdding: .second,
            value: endMinutes * 60,
            to: startOfDay
        ) else {
            return nil
        }
        if endDate <= startDate {
            guard let followingDayEnd = calendar.date(byAdding: .day, value: 1, to: endDate) else {
                return nil
            }
            endDate = followingDayEnd
        }

        let duration = max(endDate.timeIntervalSince(startDate), 1)
        let elapsed = min(max(now.timeIntervalSince(startDate), 0), duration)
        let remaining = max(endDate.timeIntervalSince(now), 0)
        return CourseTimingProgress(
            elapsedSeconds: Int(elapsed.rounded(.down)),
            remainingSeconds: Int(remaining.rounded(.up)),
            fraction: Double(elapsed) / Double(duration)
        )
    }
}

struct WeekCourseItem: Equatable, Identifiable {
    let occurrence: CourseOccurrenceDTO
    let isConflicting: Bool
    let displayDayOfWeek: Int

    var id: String { "\(displayDayOfWeek)-\(occurrence.schedule.id)" }
}

struct WeekDayPresentation: Equatable, Identifiable {
    let dayOfWeek: Int
    let date: Date?
    let items: [WeekCourseItem]
    let isNonTeachingDay: Bool
    let scheduleSourceDayOfWeek: Int?

    var id: Int { dayOfWeek }
}

struct WeekMatrixItem: Equatable, Identifiable {
    let id: String
    let occurrence: CourseOccurrenceDTO
    let isConflicting: Bool
    let dayColumn: Int
    let startRow: Int
    let rowSpan: Int
    let lane: Int
    let laneCount: Int

}

struct WeekMatrixBreak: Equatable {
    let title: String
    let startTime: String
    let endTime: String
    let insertionRow: Int
}

struct WeekMatrixPresentation: Equatable {
    let periods: [PeriodDTO]
    let items: [WeekMatrixItem]
    let scheduleBreak: WeekMatrixBreak?

    init(
        semester: SemesterDTO,
        days: [WeekDayPresentation],
        academicCalendarSettings: AcademicCalendarSettings = .defaults
    ) {
        periods = semester.periods.sorted { $0.number < $1.number }
        scheduleBreak = Self.makeBreak(
            academicCalendarSettings.lunchBreak,
            periods: periods
        )
        let periodRows = Dictionary(uniqueKeysWithValues: periods.enumerated().map {
            ($0.element.number, $0.offset)
        })

        items = days
            .filter { (1...ScheduleDisplayText.weekdayNames.count).contains($0.dayOfWeek) }
            .flatMap { day in
                let drafts = day.items.compactMap { item -> Draft? in
                    guard
                        let startRow = periodRows[item.occurrence.schedule.startPeriod],
                        let endRow = periodRows[item.occurrence.schedule.endPeriod],
                        endRow >= startRow
                    else {
                        return nil
                    }
                    return Draft(
                        item: item,
                        dayColumn: day.dayOfWeek - 1,
                        startRow: startRow,
                        rowSpan: endRow - startRow + 1
                    )
                }
                return Self.laidOut(drafts)
            }
            .sorted {
                if $0.dayColumn != $1.dayColumn { return $0.dayColumn < $1.dayColumn }
                if $0.startRow != $1.startRow { return $0.startRow < $1.startRow }
                if $0.lane != $1.lane { return $0.lane < $1.lane }
                return $0.id < $1.id
            }
    }

    private static func makeBreak(
        _ settings: ScheduleBreakSettings,
        periods: [PeriodDTO]
    ) -> WeekMatrixBreak? {
        guard
            settings.isEnabled,
            let breakStart = ScheduleRules.minutes(from: settings.startTime),
            let breakEnd = ScheduleRules.minutes(from: settings.endTime),
            let insertionRow = periods.firstIndex(where: {
                guard let start = ScheduleRules.minutes(from: $0.startTime) else { return false }
                return start >= breakEnd
            }),
            insertionRow > 0,
            let previousEnd = ScheduleRules.minutes(from: periods[insertionRow - 1].endTime),
            previousEnd <= breakStart
        else {
            return nil
        }
        return WeekMatrixBreak(
            title: settings.title,
            startTime: settings.startTime,
            endTime: settings.endTime,
            insertionRow: insertionRow
        )
    }

    private struct Draft {
        let item: WeekCourseItem
        let dayColumn: Int
        let startRow: Int
        let rowSpan: Int

        var endRow: Int { startRow + rowSpan - 1 }
    }

    private static func laidOut(_ drafts: [Draft]) -> [WeekMatrixItem] {
        let sorted = drafts.sorted {
            if $0.startRow != $1.startRow { return $0.startRow < $1.startRow }
            if $0.endRow != $1.endRow { return $0.endRow < $1.endRow }
            return $0.item.id < $1.item.id
        }
        var result: [WeekMatrixItem] = []
        var component: [Draft] = []
        var componentEnd = -1

        for draft in sorted {
            if !component.isEmpty, draft.startRow > componentEnd {
                result.append(contentsOf: layoutComponent(component))
                component.removeAll(keepingCapacity: true)
                componentEnd = -1
            }
            component.append(draft)
            componentEnd = max(componentEnd, draft.endRow)
        }
        result.append(contentsOf: layoutComponent(component))
        return result
    }

    private static func layoutComponent(_ drafts: [Draft]) -> [WeekMatrixItem] {
        guard !drafts.isEmpty else { return [] }
        var laneEndRows: [Int] = []
        var placements: [(draft: Draft, lane: Int)] = []

        for draft in drafts {
            let lane: Int
            if let reusableLane = laneEndRows.firstIndex(where: { $0 < draft.startRow }) {
                lane = reusableLane
                laneEndRows[lane] = draft.endRow
            } else {
                lane = laneEndRows.count
                laneEndRows.append(draft.endRow)
            }
            placements.append((draft, lane))
        }

        let laneCount = laneEndRows.count
        return placements.map { placement in
            WeekMatrixItem(
                id: placement.draft.item.id,
                occurrence: placement.draft.item.occurrence,
                isConflicting: placement.draft.item.isConflicting,
                dayColumn: placement.draft.dayColumn,
                startRow: placement.draft.startRow,
                rowSpan: placement.draft.rowSpan,
                lane: placement.lane,
                laneCount: laneCount
            )
        }
    }
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
        academicCalendarSettings: AcademicCalendarSettings = .defaults,
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
        days = (1...ScheduleDisplayText.weekdayNames.count).map { dayOfWeek in
            let date = ScheduleRules.date(
                forTeachingWeek: resolvedWeek,
                dayOfWeek: dayOfWeek,
                semester: semester,
                calendar: calendar
            )
            let resolution = date.map {
                academicCalendarSettings.resolution(for: $0, calendar: calendar)
            }
            let sourceDayOfWeek: Int?
            let isNonTeachingDay: Bool
            switch resolution {
            case .teaching(let source, _):
                sourceDayOfWeek = source
                isNonTeachingDay = false
            case .nonTeaching, .none:
                sourceDayOfWeek = nil
                isNonTeachingDay = resolution != nil
            }
            let dayOccurrences = sourceDayOfWeek.map { source in
                occurrences
                    .filter { $0.schedule.dayOfWeek == source }
                    .sorted(by: Self.stableOccurrenceOrder)
            } ?? []
            let conflictIDs = Self.conflictingScheduleIDs(
                in: dayOccurrences,
                week: resolvedWeek
            )
            return WeekDayPresentation(
                dayOfWeek: dayOfWeek,
                date: date,
                items: dayOccurrences.map {
                    WeekCourseItem(
                        occurrence: $0,
                        isConflicting: conflictIDs.contains($0.schedule.id),
                        displayDayOfWeek: dayOfWeek
                    )
                },
                isNonTeachingDay: isNonTeachingDay,
                scheduleSourceDayOfWeek: sourceDayOfWeek
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
