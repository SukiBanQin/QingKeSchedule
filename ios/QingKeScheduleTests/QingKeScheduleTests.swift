import Testing
@testable import QingKeSchedule

struct QingKeScheduleTests {
    @Test("应用模块可被 Swift Testing target 加载")
    func appModuleLoads() {
        #expect(ProjectScaffold.productName == "QingKeSchedule")
    }
}
