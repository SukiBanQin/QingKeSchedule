import Testing
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
}
