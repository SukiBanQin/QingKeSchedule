import XCTest

final class QingKeScheduleUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testLaunchShowsScaffold() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["app-title"].waitForExistence(timeout: 5))
    }
}
