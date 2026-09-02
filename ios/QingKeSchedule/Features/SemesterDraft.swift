import Foundation

struct PeriodDraft: Identifiable, Equatable {
    let id: UUID
    var number: Int
    var startTime: Date
    var endTime: Date

    init(
        id: UUID = UUID(),
        number: Int,
        startTime: Date,
        endTime: Date
    ) {
        self.id = id
        self.number = number
        self.startTime = startTime
        self.endTime = endTime
    }
}

struct SemesterDraft {
    var id: String
    var name: String
    var startDate: Date
    var totalWeeks: Int
    var periods: [PeriodDraft]

    init(
        semester: SemesterDTO? = nil,
        now: Date = Date(),
        calendar: Calendar = ScheduleRules.gregorianCalendar()
    ) {
        if let semester {
            id = semester.id
            name = semester.name
            startDate = ScheduleRules.localDate(
                from: semester.startDate,
                calendar: calendar
            ) ?? now
            totalWeeks = semester.totalWeeks
            periods = semester.periods.map {
                PeriodDraft(
                    number: $0.number,
                    startTime: Self.timeDate(from: $0.startTime, calendar: calendar),
                    endTime: Self.timeDate(from: $0.endTime, calendar: calendar)
                )
            }
        } else {
            let year = calendar.component(.year, from: now)
            let season = calendar.component(.month, from: now) >= 7 ? "秋季" : "春季"
            id = UUID().uuidString
            name = "\(year) \(season)学期"
            startDate = calendar.startOfDay(for: now)
            totalWeeks = 18
            periods = Self.defaultPeriodValues.enumerated().map { index, times in
                PeriodDraft(
                    number: index + 1,
                    startTime: Self.timeDate(from: times.0, calendar: calendar),
                    endTime: Self.timeDate(from: times.1, calendar: calendar)
                )
            }
        }
    }

    func semester(calendar: Calendar = ScheduleRules.gregorianCalendar()) -> SemesterDTO {
        SemesterDTO(
            id: id,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            startDate: Self.dateString(from: startDate, calendar: calendar),
            totalWeeks: totalWeeks,
            periods: periods.enumerated().map { index, period in
                PeriodDTO(
                    number: index + 1,
                    startTime: Self.timeString(from: period.startTime, calendar: calendar),
                    endTime: Self.timeString(from: period.endTime, calendar: calendar)
                )
            }
        )
    }

    func validationIssues(
        calendar: Calendar = ScheduleRules.gregorianCalendar()
    ) -> [ScheduleValidationIssue] {
        ScheduleValidator.validate(
            ScheduleDataDTO(
                semester: semester(calendar: calendar),
                courses: [],
                updatedAt: "1970-01-01T00:00:00.000Z"
            ),
            calendar: calendar
        )
    }

    mutating func addPeriod(calendar: Calendar = ScheduleRules.gregorianCalendar()) {
        let previousEnd = periods.last?.endTime
            ?? Self.timeDate(from: "07:50", calendar: calendar)
        let start = calendar.date(byAdding: .minute, value: 10, to: previousEnd) ?? previousEnd
        let end = calendar.date(byAdding: .minute, value: 45, to: start) ?? start
        periods.append(
            PeriodDraft(
                number: periods.count + 1,
                startTime: start,
                endTime: end
            )
        )
    }

    mutating func removePeriod(id: UUID) {
        guard periods.count > 1 else { return }
        periods.removeAll { $0.id == id }
        renumberPeriods()
    }

    mutating func renumberPeriods() {
        for index in periods.indices {
            periods[index].number = index + 1
        }
    }

    private static let defaultPeriodValues: [(String, String)] = [
        ("08:00", "08:45"),
        ("08:55", "09:40"),
        ("10:00", "10:45"),
        ("10:55", "11:40"),
        ("14:00", "14:45"),
        ("14:55", "15:40"),
        ("16:00", "16:45"),
        ("16:55", "17:40"),
        ("19:00", "19:45"),
        ("19:55", "20:40"),
    ]

    private static func dateString(from date: Date, calendar: Calendar) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(
            format: "%04d-%02d-%02d",
            components.year ?? 0,
            components.month ?? 0,
            components.day ?? 0
        )
    }

    private static func timeString(from date: Date, calendar: Calendar) -> String {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", components.hour ?? 0, components.minute ?? 0)
    }

    private static func timeDate(from value: String, calendar: Calendar) -> Date {
        let minutes = ScheduleRules.minutes(from: value) ?? 0
        var components = DateComponents()
        components.calendar = calendar
        components.timeZone = calendar.timeZone
        components.year = 2001
        components.month = 1
        components.day = 1
        components.hour = minutes / 60
        components.minute = minutes % 60
        return calendar.date(from: components) ?? Date(timeIntervalSinceReferenceDate: 0)
    }
}
