import SwiftUI
import UIKit

struct CourseColorOption: Equatable, Sendable {
    let name: String
    let value: String
}

enum CourseColorPalette {
    static let presets: [CourseColorOption] = [
        .init(name: "青绿色", value: "#287B74"),
        .init(name: "珊瑚色", value: "#D96952"),
        .init(name: "靛蓝色", value: "#536FAF"),
        .init(name: "紫色", value: "#9A6AAF"),
        .init(name: "琥珀色", value: "#B87928"),
        .init(name: "绿色", value: "#46835A"),
    ]
}

enum QingKeVisualSpec {
    static let brandTitle = "QINGKE"
    static let brandSubtitle = "ACADEMIC TERMINAL"
    static let signalHex = "#FFD400"
    static let cyanHex = "#28B9D6"
    static let panelCornerRadius: CGFloat = 8
    static let floatingActionSize: CGFloat = 64
    static let gridSpacing: CGFloat = 24
}

enum QingKeTheme {
    static let ink = Color(red: 0.035, green: 0.065, blue: 0.073)
    static let paper = Color(red: 0.89, green: 0.92, blue: 0.92)
    static let signal = Color(courseHex: QingKeVisualSpec.signalHex)
    static let cyan = Color(courseHex: QingKeVisualSpec.cyanHex)
    static let muted = Color(red: 0.38, green: 0.44, blue: 0.45)
    static let danger = Color(red: 0.82, green: 0.19, blue: 0.15)
}

extension Font {
    static func terminal(
        _ size: CGFloat,
        weight: Font.Weight = .regular,
        relativeTo textStyle: Font.TextStyle = .body
    ) -> Font {
        .custom("Avenir Next Condensed", size: size, relativeTo: textStyle)
            .weight(weight)
    }
}

struct TerminalBackdrop: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                (colorScheme == .dark ? QingKeTheme.ink : QingKeTheme.paper)

                Canvas { context, size in
                    let gridColor = colorScheme == .dark
                        ? Color.white.opacity(0.055)
                        : QingKeTheme.ink.opacity(0.07)
                    var grid = Path()
                    var x: CGFloat = 0
                    while x <= size.width {
                        grid.move(to: CGPoint(x: x, y: 0))
                        grid.addLine(to: CGPoint(x: x, y: size.height))
                        x += QingKeVisualSpec.gridSpacing
                    }
                    var y: CGFloat = 0
                    while y <= size.height {
                        grid.move(to: CGPoint(x: 0, y: y))
                        grid.addLine(to: CGPoint(x: size.width, y: y))
                        y += QingKeVisualSpec.gridSpacing
                    }
                    context.stroke(grid, with: .color(gridColor), lineWidth: 0.5)

                    let diameter = max(size.width * 0.88, 320)
                    let origin = CGPoint(
                        x: size.width * 0.56,
                        y: size.height * 0.27
                    )
                    for inset in [CGFloat(0), 42, 90] {
                        let ring = Path(ellipseIn: CGRect(
                            x: origin.x - inset,
                            y: origin.y - inset,
                            width: diameter + inset * 2,
                            height: diameter + inset * 2
                        ))
                        context.stroke(
                            ring,
                            with: .color(gridColor.opacity(1.5)),
                            lineWidth: 1
                        )
                    }
                }

                LinearGradient(
                    colors: [
                        .clear,
                        colorScheme == .dark
                            ? QingKeTheme.cyan.opacity(0.06)
                            : Color.white.opacity(0.48),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

struct TerminalBrandHeader: View {
    let code: String

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .bottomTrailing) {
                Rectangle()
                    .fill(QingKeTheme.ink)
                    .frame(width: 42, height: 42)
                Text("Q")
                    .font(.terminal(28, weight: .black, relativeTo: .title))
                    .italic()
                    .foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                Rectangle()
                    .fill(QingKeTheme.cyan)
                    .frame(width: 15, height: 4)
            }

            VStack(alignment: .leading, spacing: -1) {
                Text(QingKeVisualSpec.brandTitle)
                    .font(.terminal(20, weight: .black, relativeTo: .headline))
                    .tracking(1.5)
                Text(QingKeVisualSpec.brandSubtitle)
                    .font(.terminal(9, weight: .bold, relativeTo: .caption2))
                    .tracking(1.2)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(code)
                .font(.terminal(10, weight: .bold, relativeTo: .caption))
                .tracking(1)
                .foregroundStyle(.secondary)
        }
        .padding(.bottom, 12)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.primary.opacity(0.24))
                .frame(height: 1)
        }
        .accessibilityElement(children: .combine)
    }
}

struct TerminalSectionHeader: View {
    let index: String
    let title: String
    var detail: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 9) {
            Text(index)
                .font(.terminal(13, weight: .black, relativeTo: .caption))
                .foregroundStyle(QingKeTheme.cyan)
            Text(title)
                .font(.title3.weight(.bold))
            Spacer()
            if let detail {
                Text(detail.uppercased())
                    .font(.terminal(8, weight: .bold, relativeTo: .caption2))
                    .tracking(1)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.bottom, 7)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.primary.opacity(0.35))
                .frame(height: 1)
        }
    }
}

struct TerminalStatusTag: View {
    let text: String
    var tint: Color = QingKeTheme.cyan

    var body: some View {
        Text(text.uppercased())
            .font(.terminal(9, weight: .black, relativeTo: .caption2))
            .tracking(0.9)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .foregroundStyle(QingKeTheme.ink)
            .background(tint)
    }
}

struct TerminalFloatingAction: View {
    let accessibilityIdentifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 1) {
                Image(systemName: "plus")
                    .font(.system(size: 25, weight: .light))
                Text("ADD")
                    .font(.terminal(8, weight: .black, relativeTo: .caption2))
                    .tracking(0.8)
            }
            .foregroundStyle(QingKeTheme.ink)
            .frame(
                width: QingKeVisualSpec.floatingActionSize,
                height: QingKeVisualSpec.floatingActionSize
            )
            .background(QingKeTheme.signal)
            .overlay(alignment: .topTrailing) {
                TriangleCorner()
                    .fill(Color.white.opacity(0.75))
                    .frame(width: 13, height: 13)
            }
            .overlay {
                Rectangle()
                    .stroke(Color.white.opacity(0.75), lineWidth: 1)
                    .padding(3)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("添加课程")
        .accessibilityIdentifier(accessibilityIdentifier)
    }
}

private struct TriangleCorner: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

private struct TerminalPanelModifier: ViewModifier {
    let accent: Color?

    func body(content: Content) -> some View {
        content
            .padding(14)
            .background(.thinMaterial)
            .overlay(alignment: .leading) {
                if let accent {
                    Rectangle()
                        .fill(accent)
                        .frame(width: 4)
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: QingKeVisualSpec.panelCornerRadius)
                    .stroke(Color.white.opacity(0.55), lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: QingKeVisualSpec.panelCornerRadius))
    }
}

extension View {
    func terminalPanel(accent: Color? = nil) -> some View {
        modifier(TerminalPanelModifier(accent: accent))
    }
}

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

    var courseHexValue: String? {
        let resolved = UIColor(self).resolvedColor(
            with: UITraitCollection(userInterfaceStyle: .light)
        )
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        guard resolved.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return nil
        }
        return String(
            format: "#%02X%02X%02X",
            Int(round(red * 255)),
            Int(round(green * 255)),
            Int(round(blue * 255))
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
