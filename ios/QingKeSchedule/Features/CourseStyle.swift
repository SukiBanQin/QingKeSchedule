import SwiftUI

extension Color {
    init(courseHex value: String) {
        let cleaned = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        guard cleaned.count == 6, let number = Int(cleaned, radix: 16) else {
            self = .accentColor
            return
        }
        self.init(
            red: Double((number >> 16) & 0xFF) / 255,
            green: Double((number >> 8) & 0xFF) / 255,
            blue: Double(number & 0xFF) / 255
        )
    }
}

extension CourseStatus {
    var displayName: String {
        switch self {
        case .finished: "已结束"
        case .ongoing: "进行中"
        case .upcoming: "未开始"
        }
    }

    var systemImage: String {
        switch self {
        case .finished: "checkmark.circle"
        case .ongoing: "play.circle.fill"
        case .upcoming: "clock"
        }
    }
}

enum ScheduleDisplayText {
    static let weekdayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    static func periodRange(_ schedule: CourseScheduleDTO) -> String {
        schedule.startPeriod == schedule.endPeriod
            ? "第 \(schedule.startPeriod) 节"
            : "第 \(schedule.startPeriod)–\(schedule.endPeriod) 节"
    }

    static func timeRange(_ schedule: CourseScheduleDTO, semester: SemesterDTO) -> String {
        let start = semester.periods.first { $0.number == schedule.startPeriod }?.startTime ?? "--:--"
        let end = semester.periods.first { $0.number == schedule.endPeriod }?.endTime ?? "--:--"
        return "\(start)–\(end)"
    }
}
