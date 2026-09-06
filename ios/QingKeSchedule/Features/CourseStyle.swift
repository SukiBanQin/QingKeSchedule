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
    static let panelCornerRadius: CGFloat = 0
    static let panelLightWashOpacity = 0.28
    static let panelDarkWashOpacity = 0.05
    static let brandBackingOpacity = 0.0
    static let floatingActionSize: CGFloat = 64
    static let gridSpacing: CGFloat = 24
}

enum QingKeTheme {
    static let canvas = dynamicColor(light: "#E3EBEB", dark: "#081113")
    static let surface = dynamicColor(light: "#F1F5F4", dark: "#111B1E")
    static let surfaceElevated = dynamicColor(light: "#FBFDFC", dark: "#182427")
    static let surfaceActive = dynamicColor(light: "#DCE4E3", dark: "#202D31")
    static let textPrimary = Color(uiColor: .label)
    static let textSecondary = dynamicSystemColor(
        .secondaryLabel,
        minimumLightAlpha: 0.78,
        minimumDarkAlpha: 0.60
    )
    static let textTertiary = Color(uiColor: .tertiaryLabel)
    static let textOnAccent = fixedColor("#071013")
    static let textOnInverse = dynamicColor(light: "#F1F5F4", dark: "#F1F5F4")
    static let inverseSurface = dynamicColor(light: "#091113", dark: "#182427")
    static let divider = Color(uiColor: .separator)
    static let border = dynamicColor(
        light: "#091113", lightAlpha: 0.20,
        dark: "#F1F5F4", darkAlpha: 0.22,
        highContrastLightAlpha: 0.32,
        highContrastDarkAlpha: 0.38
    )
    static let borderStrong = dynamicColor(
        light: "#091113", lightAlpha: 0.34,
        dark: "#F1F5F4", darkAlpha: 0.32,
        highContrastLightAlpha: 0.46,
        highContrastDarkAlpha: 0.48
    )
    static let panelEdge = dynamicColor(
        light: "#FFFFFF", lightAlpha: 0.68,
        dark: "#F1F5F4", darkAlpha: 0.22,
        highContrastLightAlpha: 0.82,
        highContrastDarkAlpha: 0.38
    )
    static let shadow = dynamicColor(light: "#000000", lightAlpha: 0.14, dark: "#000000", darkAlpha: 0.34)
    static let grid = dynamicColor(light: "#091113", lightAlpha: 0.07, dark: "#F1F5F4", darkAlpha: 0.055)
    static let ambientWash = dynamicColor(light: "#FFFFFF", lightAlpha: 0.48, dark: "#35C8E5", darkAlpha: 0.06)
    static let scrim = dynamicColor(light: "#000000", lightAlpha: 0.46, dark: "#000000", darkAlpha: 0.62)
    static let progressTrack = dynamicColor(light: "#091113", lightAlpha: 0.12, dark: "#F1F5F4", darkAlpha: 0.14)
    static let signal = dynamicColor(light: "#FFD400", dark: "#FFD400")
    static let cyan = dynamicColor(light: "#28B9D6", dark: "#35C8E5")
    static let danger = dynamicColor(light: "#E65A4F", dark: "#FF665C")

    private static func dynamicColor(
        light: String,
        lightAlpha: CGFloat = 1,
        dark: String,
        darkAlpha: CGFloat = 1,
        highContrastLightAlpha: CGFloat? = nil,
        highContrastDarkAlpha: CGFloat? = nil
    ) -> Color {
        Color(uiColor: UIColor { traits in
            let isDark = traits.userInterfaceStyle == .dark
            let isHighContrast = traits.accessibilityContrast == .high
            let hex = isDark ? dark : light
            let alpha: CGFloat
            if isDark {
                alpha = isHighContrast ? (highContrastDarkAlpha ?? darkAlpha) : darkAlpha
            } else {
                alpha = isHighContrast ? (highContrastLightAlpha ?? lightAlpha) : lightAlpha
            }
            return uiColor(hex).withAlphaComponent(alpha)
        })
    }

    private static func fixedColor(_ hex: String) -> Color {
        Color(uiColor: uiColor(hex))
    }

    private static func dynamicSystemColor(
        _ color: UIColor,
        minimumLightAlpha: CGFloat,
        minimumDarkAlpha: CGFloat
    ) -> Color {
        Color(uiColor: UIColor { traits in
            let resolved = color.resolvedColor(with: traits)
            let minimumAlpha = traits.userInterfaceStyle == .dark
                ? minimumDarkAlpha
                : minimumLightAlpha
            return resolved.withAlphaComponent(max(resolved.cgColor.alpha, minimumAlpha))
        })
    }

    private static func uiColor(_ hex: String) -> UIColor {
        let value = UInt64(hex.dropFirst(), radix: 16) ?? 0
        return UIColor(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: 1
        )
    }

    static func courseContentColor(for hex: String) -> Color {
        courseUsesDarkContent(for: hex) ? textOnAccent : textOnInverse
    }

    static func courseUsesDarkContent(for hex: String) -> Bool {
        let color = uiColor(hex)
        let components = color.cgColor.components ?? [0, 0, 0]
        let red = components[0]
        let green = components.count > 1 ? components[1] : red
        let blue = components.count > 2 ? components[2] : red
        let backgroundLuminance = relativeLuminance(red: red, green: green, blue: blue)
        let accentContrast = contrastRatio(
            foregroundLuminance: relativeLuminance(red: 0.027, green: 0.063, blue: 0.075),
            backgroundLuminance: backgroundLuminance
        )
        let inverseContrast = contrastRatio(
            foregroundLuminance: relativeLuminance(red: 0.945, green: 0.961, blue: 0.937),
            backgroundLuminance: backgroundLuminance
        )
        return accentContrast >= inverseContrast
    }

    private static func relativeLuminance(red: CGFloat, green: CGFloat, blue: CGFloat) -> CGFloat {
        func linear(_ channel: CGFloat) -> CGFloat {
            channel <= 0.04045 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    private static func contrastRatio(
        foregroundLuminance: CGFloat,
        backgroundLuminance: CGFloat
    ) -> CGFloat {
        (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }
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
    var body: some View {
        GeometryReader { proxy in
            ZStack {
                QingKeTheme.canvas

                Canvas { context, size in
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
                    context.stroke(grid, with: .color(QingKeTheme.grid), lineWidth: 0.5)

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
                            with: .color(QingKeTheme.grid.opacity(1.5)),
                            lineWidth: 1
                        )
                    }
                }

                LinearGradient(
                    colors: [
                        .clear,
                        QingKeTheme.ambientWash,
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
            Image("QingKeLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 154, height: 54, alignment: .leading)
                .padding(.horizontal, 5)
                .accessibilityHidden(true)

            Spacer()

            Text(code)
                .font(.terminal(10, weight: .bold, relativeTo: .caption))
                .tracking(1)
                .foregroundStyle(QingKeTheme.textSecondary)
        }
        .padding(.bottom, 12)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(QingKeTheme.divider)
                .frame(height: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(QingKeVisualSpec.brandTitle)，青课，\(QingKeVisualSpec.brandSubtitle)，\(code)"
        )
    }
}

enum TerminalSurfaceLevel {
    case standard
    case elevated
}

struct TerminalAcrylicSurface: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    var level: TerminalSurfaceLevel = .standard

    var body: some View {
        Group {
            if colorScheme == .light && !reduceTransparency {
                Rectangle()
                    .fill(.ultraThinMaterial)
                    .overlay(lightWash)
            } else {
                Rectangle()
                    .fill(level == .elevated ? QingKeTheme.surfaceElevated : QingKeTheme.surface)
                    .overlay(darkWash)
            }
        }
    }

    private var lightWash: some View {
        LinearGradient(
            colors: [
                Color.white.opacity(QingKeVisualSpec.panelLightWashOpacity),
                Color.white.opacity(0.02),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var darkWash: some View {
        LinearGradient(
            colors: [
                QingKeTheme.surfaceElevated.opacity(level == .elevated ? 0.34 : 0.18),
                .clear,
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

struct TerminalFormSection<Content: View>: View {
    let index: String
    let title: String
    var detail: String? = nil
    var footer: String? = nil
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            TerminalSectionHeader(index: index, title: title, detail: detail)
            VStack(alignment: .leading, spacing: 0) {
                content()
            }
            .padding(.horizontal, 14)
            .background { TerminalAcrylicSurface() }
            .overlay(alignment: .leading) {
                Rectangle().fill(QingKeTheme.cyan).frame(width: 3)
            }
            .overlay { Rectangle().stroke(QingKeTheme.panelEdge, lineWidth: 1) }

            if let footer {
                Text(footer)
                    .font(.caption)
                    .foregroundStyle(QingKeTheme.textSecondary)
                    .padding(.horizontal, 3)
            }
        }
    }
}

struct TerminalFormDivider: View {
    var body: some View {
        Rectangle()
            .fill(QingKeTheme.divider)
            .frame(height: 1)
    }
}

private struct TerminalControlModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
            .contentShape(Rectangle())
    }
}

extension View {
    func terminalControl() -> some View {
        modifier(TerminalControlModifier())
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
                    .foregroundStyle(QingKeTheme.textSecondary)
            }
        }
        .padding(.bottom, 7)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(QingKeTheme.borderStrong)
                .frame(height: 1)
        }
    }
}

struct TerminalStatusTag: View {
    let text: String
    var tint: Color = QingKeTheme.cyan
    var contentColor: Color = QingKeTheme.textOnAccent

    var body: some View {
        Text(text.uppercased())
            .font(.terminal(9, weight: .black, relativeTo: .caption2))
            .tracking(0.9)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .foregroundStyle(contentColor)
            .background(tint)
    }
}

struct TerminalToast: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(QingKeTheme.signal)
                .frame(width: 7, height: 7)

            Text(message)
                .font(.terminal(11, weight: .bold, relativeTo: .caption))
                .tracking(0.7)

            Spacer(minLength: 0)

            Image(systemName: "checkmark")
                .font(.caption.bold())
                .foregroundStyle(QingKeTheme.signal)
        }
        .foregroundStyle(QingKeTheme.textPrimary)
        .padding(.horizontal, 14)
        .frame(minHeight: 46)
        .background { TerminalAcrylicSurface(level: .elevated) }
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(QingKeTheme.signal)
                .frame(width: 4)
        }
        .overlay {
            Rectangle()
                .stroke(QingKeTheme.border, lineWidth: 1)
        }
        .shadow(color: QingKeTheme.shadow, radius: 14, y: 7)
        .accessibilityElement(children: .combine)
    }
}

enum TerminalDialogTone {
    case info
    case warning
    case danger

    var color: Color {
        switch self {
        case .info: QingKeTheme.cyan
        case .warning: QingKeTheme.signal
        case .danger: QingKeTheme.danger
        }
    }
}

struct TerminalDialog: View {
    let code: String
    let tag: String
    let title: String
    let message: String
    let tone: TerminalDialogTone
    let accessibilityIdentifier: String
    let primaryActionTitle: String
    let primaryActionIdentifier: String
    let primaryAction: () -> Void
    let secondaryActionTitle: String?
    let secondaryActionIdentifier: String?
    let secondaryAction: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @AccessibilityFocusState private var titleIsFocused: Bool

    init(
        code: String,
        tag: String,
        title: String,
        message: String,
        tone: TerminalDialogTone,
        accessibilityIdentifier: String,
        primaryActionTitle: String,
        primaryActionIdentifier: String,
        primaryAction: @escaping () -> Void,
        secondaryActionTitle: String? = nil,
        secondaryActionIdentifier: String? = nil,
        secondaryAction: (() -> Void)? = nil
    ) {
        self.code = code
        self.tag = tag
        self.title = title
        self.message = message
        self.tone = tone
        self.accessibilityIdentifier = accessibilityIdentifier
        self.primaryActionTitle = primaryActionTitle
        self.primaryActionIdentifier = primaryActionIdentifier
        self.primaryAction = primaryAction
        self.secondaryActionTitle = secondaryActionTitle
        self.secondaryActionIdentifier = secondaryActionIdentifier
        self.secondaryAction = secondaryAction
    }

    var body: some View {
        ZStack {
            QingKeTheme.scrim
                .ignoresSafeArea()
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline) {
                    Text(code)
                        .font(.terminal(10, weight: .black, relativeTo: .caption))
                        .tracking(1)
                        .foregroundStyle(tone.color)
                    Spacer()
                    Circle()
                        .fill(tone.color)
                        .frame(width: 8, height: 8)
                        .accessibilityHidden(true)
                }

                TerminalStatusTag(text: tag, tint: tone.color)

                Text(title)
                    .font(.title3.weight(.black))
                    .foregroundStyle(QingKeTheme.textPrimary)
                    .accessibilityFocused($titleIsFocused)

                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(QingKeTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                actions
            }
            .padding(20)
            .frame(maxWidth: 420, alignment: .leading)
            .background { TerminalAcrylicSurface(level: .elevated) }
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(tone.color)
                    .frame(width: 4)
            }
            .overlay {
                Rectangle().stroke(QingKeTheme.panelEdge, lineWidth: 1)
            }
            .shadow(color: QingKeTheme.shadow, radius: 16, y: 8)
            .padding(.horizontal, 20)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityIdentifier(accessibilityIdentifier)
            .accessibilityAction(.escape) {
                (secondaryAction ?? primaryAction)()
            }
            .transition(dialogTransition)
        }
        .onAppear { titleIsFocused = true }
    }

    @ViewBuilder
    private var actions: some View {
        if let secondaryActionTitle, let secondaryActionIdentifier, let secondaryAction {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(spacing: 10) {
                    secondaryButton(
                        title: secondaryActionTitle,
                        accessibilityIdentifier: secondaryActionIdentifier,
                        action: secondaryAction
                    )
                    primaryButton
                }
            } else {
                HStack(spacing: 10) {
                    secondaryButton(
                        title: secondaryActionTitle,
                        accessibilityIdentifier: secondaryActionIdentifier,
                        action: secondaryAction
                    )
                    primaryButton
                }
            }
        } else {
            primaryButton
        }
    }

    private var primaryButton: some View {
        Button(action: primaryAction) {
            Text(primaryActionTitle)
                .font(.headline)
                .foregroundStyle(QingKeTheme.textOnAccent)
                .frame(maxWidth: .infinity, minHeight: 54)
                .background(tone.color)
                .overlay {
                    Rectangle().stroke(QingKeTheme.textOnInverse.opacity(0.72), lineWidth: 1)
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(primaryActionIdentifier)
    }

    private func secondaryButton(
        title: String,
        accessibilityIdentifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.headline)
                .foregroundStyle(QingKeTheme.textPrimary)
                .frame(maxWidth: .infinity, minHeight: 54)
                .background(QingKeTheme.surfaceActive)
                .overlay {
                    Rectangle().stroke(QingKeTheme.border, lineWidth: 1)
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private var dialogTransition: AnyTransition {
        reduceMotion
            ? .opacity
            : .opacity.combined(with: .scale(scale: 0.96))
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
            .foregroundStyle(QingKeTheme.textOnAccent)
            .frame(
                width: QingKeVisualSpec.floatingActionSize,
                height: QingKeVisualSpec.floatingActionSize
            )
            .background(QingKeTheme.signal)
            .overlay(alignment: .topTrailing) {
                TriangleCorner()
                    .fill(QingKeTheme.textOnInverse.opacity(0.75))
                    .frame(width: 13, height: 13)
            }
            .overlay {
                Rectangle()
                    .stroke(QingKeTheme.textOnInverse.opacity(0.75), lineWidth: 1)
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
            .background { TerminalAcrylicSurface() }
            .overlay(alignment: .leading) {
                if let accent {
                    Rectangle()
                        .fill(accent)
                        .frame(width: 4)
                }
            }
            .overlay {
                Rectangle().stroke(QingKeTheme.panelEdge, lineWidth: 1)
            }
            .shadow(color: QingKeTheme.shadow, radius: 10, y: 5)
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

    static func weekMatrixSummary(periodCount: Int) -> String {
        "MON–SUN / \(periodCount) PERIODS"
    }

    static func compactCourseDetails(course: CourseDTO, schedule: CourseScheduleDTO) -> String {
        [schedule.classroom, course.teacher]
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }
}
