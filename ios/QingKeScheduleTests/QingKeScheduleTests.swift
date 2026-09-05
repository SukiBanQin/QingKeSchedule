import Testing
import SwiftUI
import UIKit
@testable import QingKeSchedule

struct QingKeScheduleTests {
    @Test("应用模块可被 Swift Testing target 加载")
    func appModuleLoads() {
        #expect(ProjectScaffold.productName == "QingKeSchedule")
    }

    @Test("终端视觉规格保持品牌色与触控尺寸")
    func terminalVisualSpec() {
        #expect(QingKeVisualSpec.brandTitle == "QINGKE")
        #expect(QingKeVisualSpec.brandSubtitle == "ACADEMIC TERMINAL")
        #expect(QingKeVisualSpec.signalHex == "#FFD400")
        #expect(QingKeVisualSpec.cyanHex == "#28B9D6")
        #expect(QingKeVisualSpec.floatingActionSize >= 44)
        #expect(QingKeVisualSpec.gridSpacing > 0)
        #expect(QingKeVisualSpec.brandBackingOpacity == 0)
        #expect(QingKeVisualSpec.panelCornerRadius == 0)
        #expect(QingKeVisualSpec.panelLightWashOpacity < 0.5)
        #expect(QingKeVisualSpec.panelDarkWashOpacity < 0.1)
    }

    @Test("课程快捷色保持可选且互不重复")
    func courseColorPresets() {
        #expect(CourseColorPalette.presets.count == 6)
        #expect(Set(CourseColorPalette.presets.map(\.value)).count == 6)
        #expect(CourseColorPalette.presets.allSatisfy {
            $0.value.wholeMatch(of: ScheduleValidator.courseColorPattern) != nil
        })
    }

    @Test("透明品牌 Logo 已编入 iOS 资源")
    func brandLogoIsBundled() {
        #expect(UIImage(named: "QingKeLogo") != nil)
    }

    @Test("语义色板在深浅模式下保持层级与可读性")
    func semanticThemePalette() {
        let lightTraits = UITraitCollection(userInterfaceStyle: .light)
        let darkTraits = UITraitCollection(userInterfaceStyle: .dark)

        let lightCanvas = resolved(QingKeTheme.canvas, with: lightTraits)
        let darkCanvas = resolved(QingKeTheme.canvas, with: darkTraits)
        let darkSurface = resolved(QingKeTheme.surface, with: darkTraits)
        let darkElevatedSurface = resolved(QingKeTheme.surfaceElevated, with: darkTraits)

        #expect(!colorsMatch(lightCanvas, darkCanvas))
        #expect(relativeLuminance(darkCanvas) < relativeLuminance(darkSurface))
        #expect(relativeLuminance(darkSurface) < relativeLuminance(darkElevatedSurface))

        for traits in [lightTraits, darkTraits] {
            let canvas = resolved(QingKeTheme.canvas, with: traits)
            #expect(contrastRatio(resolved(QingKeTheme.textPrimary, with: traits), against: canvas) >= 4.5)
            #expect(contrastRatio(resolved(QingKeTheme.textSecondary, with: traits), against: canvas) >= 4.5)
        }

        for accent in [QingKeTheme.signal, QingKeTheme.cyan] {
            #expect(
                contrastRatio(
                    resolved(QingKeTheme.textOnAccent, with: darkTraits),
                    against: resolved(accent, with: darkTraits)
                ) >= 4.5
            )
        }

        let standardBorder = resolved(QingKeTheme.border, with: darkTraits)
        let highContrastTraits = UITraitCollection(traitsFrom: [
            darkTraits,
            UITraitCollection(accessibilityContrast: .high),
        ])
        let highContrastBorder = resolved(QingKeTheme.border, with: highContrastTraits)
        #expect(
            contrastRatio(highContrastBorder, against: darkCanvas) >
                contrastRatio(standardBorder, against: darkCanvas)
        )
    }

    @Test("品牌 Logo 会按外观加载深浅资源")
    func brandLogoUsesAppearanceSpecificAssets() throws {
        let lightTraits = UITraitCollection(userInterfaceStyle: .light)
        let darkTraits = UITraitCollection(userInterfaceStyle: .dark)
        let bundle = Bundle.main
        let lightLogo = try #require(
            UIImage(named: "QingKeLogo", in: bundle, compatibleWith: lightTraits)
        )
        let darkLogo = try #require(
            UIImage(named: "QingKeLogo", in: bundle, compatibleWith: darkTraits)
        )

        #expect(lightLogo.size == darkLogo.size)
        #expect(lightLogo.pngData() != darkLogo.pngData())
        #expect(hasAlphaChannel(darkLogo))
        #expect(averageVisibleLuminance(darkLogo) > averageVisibleLuminance(lightLogo) + 0.35)
    }

    private func resolved(_ color: Color, with traits: UITraitCollection) -> UIColor {
        UIColor(color).resolvedColor(with: traits)
    }

    private func colorsMatch(_ lhs: UIColor, _ rhs: UIColor) -> Bool {
        let left = rgbaComponents(lhs)
        let right = rgbaComponents(rhs)
        return abs(left.0 - right.0) < 0.001 &&
            abs(left.1 - right.1) < 0.001 &&
            abs(left.2 - right.2) < 0.001 &&
            abs(left.3 - right.3) < 0.001
    }

    private func contrastRatio(_ foreground: UIColor, against background: UIColor) -> CGFloat {
        let composited = composite(foreground, over: background)
        let lightness = relativeLuminance(composited)
        let backgroundLightness = relativeLuminance(background)
        return (max(lightness, backgroundLightness) + 0.05) /
            (min(lightness, backgroundLightness) + 0.05)
    }

    private func composite(_ foreground: UIColor, over background: UIColor) -> UIColor {
        let foregroundComponents = rgbaComponents(foreground)
        let backgroundComponents = rgbaComponents(background)
        let alpha = foregroundComponents.3
        return UIColor(
            red: foregroundComponents.0 * alpha + backgroundComponents.0 * (1 - alpha),
            green: foregroundComponents.1 * alpha + backgroundComponents.1 * (1 - alpha),
            blue: foregroundComponents.2 * alpha + backgroundComponents.2 * (1 - alpha),
            alpha: 1
        )
    }

    private func relativeLuminance(_ color: UIColor) -> CGFloat {
        let components = rgbaComponents(color)
        let channels = [components.0, components.1, components.2].map { channel in
            channel <= 0.04045 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
        }
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
    }

    private func rgbaComponents(_ color: UIColor) -> (CGFloat, CGFloat, CGFloat, CGFloat) {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        guard color.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return (0, 0, 0, 1)
        }
        return (red, green, blue, alpha)
    }

    private func hasAlphaChannel(_ image: UIImage) -> Bool {
        guard let alphaInfo = image.cgImage?.alphaInfo else { return false }
        return alphaInfo == .first || alphaInfo == .last ||
            alphaInfo == .premultipliedFirst || alphaInfo == .premultipliedLast
    }

    private func averageVisibleLuminance(_ image: UIImage) -> CGFloat {
        guard let cgImage = image.cgImage else { return 0 }
        let width = cgImage.width
        let height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: bitmapInfo
        ) else {
            return 0
        }
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var luminanceTotal: CGFloat = 0
        var visiblePixelCount: CGFloat = 0
        for offset in stride(from: 0, to: pixels.count, by: 4) {
            let alpha = CGFloat(pixels[offset + 3]) / 255
            guard alpha > 0.1 else { continue }
            let color = UIColor(
                red: CGFloat(pixels[offset]) / max(alpha * 255, 1),
                green: CGFloat(pixels[offset + 1]) / max(alpha * 255, 1),
                blue: CGFloat(pixels[offset + 2]) / max(alpha * 255, 1),
                alpha: 1
            )
            luminanceTotal += relativeLuminance(color)
            visiblePixelCount += 1
        }
        return luminanceTotal / max(visiblePixelCount, 1)
    }
}
