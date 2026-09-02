import XCTest

final class QingKeScheduleUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testFirstLaunchCreatesSemesterAndOpensSettings() throws {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()

        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["semester-name"].exists)

        app.buttons["semester-save-toolbar"].tap()

        XCTAssertTrue(app.tabBars.buttons["今日"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["添加第一门课程"].exists)

        app.tabBars.buttons["设置"].tap()
        XCTAssertTrue(app.navigationBars["学期与节次"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["semester-name"].exists)
    }
}
