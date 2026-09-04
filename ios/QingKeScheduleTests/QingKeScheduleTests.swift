import Testing
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
}
