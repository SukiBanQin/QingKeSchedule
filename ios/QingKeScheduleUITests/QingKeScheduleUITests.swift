import XCTest

final class QingKeScheduleUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testFirstLaunchCreatesSemesterAndOpensSettings() throws {
        let app = launchAndCreateSemester()

        XCTAssertTrue(app.tabBars.buttons["今日"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["today-empty"].exists)

        app.tabBars.buttons["设置"].tap()
        XCTAssertTrue(app.navigationBars["学期与节次"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["semester-name"].exists)
    }

    @MainActor
    func testCourseCreateConflictEditWeekAndDeleteFlow() throws {
        let app = launchAndCreateSemester()

        app.buttons["add-course-today-toolbar"].tap()
        enterCourseName("课程 A", in: app)
        app.buttons["course-save"].tap()
        XCTAssertTrue(app.staticTexts["课程 A"].firstMatch.waitForExistence(timeout: 5))

        app.buttons["add-course-today-toolbar"].tap()
        enterCourseName("课程 B", in: app)
        app.buttons["course-save"].tap()
        XCTAssertTrue(app.staticTexts["检测到课程冲突"].waitForExistence(timeout: 5))
        app.buttons["返回修改"].tap()
        XCTAssertTrue(app.textFields["course-name"].exists)
        app.buttons["course-save"].tap()
        XCTAssertTrue(app.buttons["仍然保存"].waitForExistence(timeout: 5))
        app.buttons["仍然保存"].tap()
        XCTAssertTrue(app.staticTexts["课程 B"].firstMatch.waitForExistence(timeout: 5))

        app.tabBars.buttons["课表"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["week-schedule"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["课程 A"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["冲突"].firstMatch.exists)

        app.tabBars.buttons["今日"].tap()
        app.staticTexts["课程 A"].firstMatch.tap()
        let nameField = app.textFields["course-name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        replaceText(in: nameField, with: "课程 A 已修改")
        app.buttons["course-save"].tap()
        XCTAssertTrue(app.buttons["仍然保存"].waitForExistence(timeout: 5))
        app.buttons["仍然保存"].tap()
        XCTAssertTrue(app.staticTexts["课程 A 已修改"].firstMatch.waitForExistence(timeout: 5))

        app.staticTexts["课程 B"].firstMatch.tap()
        XCTAssertTrue(app.buttons["course-delete-toolbar"].waitForExistence(timeout: 5))
        app.buttons["course-delete-toolbar"].tap()
        XCTAssertTrue(app.alerts["删除这门课程？"].waitForExistence(timeout: 5))
        app.alerts.buttons["确认删除"].tap()
        XCTAssertFalse(app.staticTexts["课程 B"].firstMatch.waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["课程 A 已修改"].firstMatch.exists)
    }

    @MainActor
    func testDarkModeLargeTextAndAccessibilityLabels() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "-AppleInterfaceStyle", "Dark",
            "-UIPreferredContentSizeCategoryName",
            "UICTContentSizeCategoryAccessibilityExtraExtraExtraLarge",
        ]
        app.launch()

        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["semester-save-toolbar"].isHittable)
        app.buttons["semester-save-toolbar"].tap()
        XCTAssertTrue(app.buttons["add-course-today-toolbar"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["add-course-today-toolbar"].label, "添加课程")
        app.buttons["add-course-today-toolbar"].tap()
        XCTAssertTrue(app.buttons["青绿色"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["course-weekday-0"].label.contains("星期"))
        XCTAssertTrue(app.buttons["course-cancel"].isHittable)
    }

    @MainActor
    private func launchAndCreateSemester() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()

        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["semester-name"].exists)
        app.buttons["semester-save-toolbar"].tap()
        XCTAssertTrue(app.tabBars.buttons["今日"].waitForExistence(timeout: 5))
        return app
    }

    @MainActor
    private func enterCourseName(_ name: String, in app: XCUIApplication) {
        let field = app.textFields["course-name"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(name)
    }

    @MainActor
    private func replaceText(in field: XCUIElement, with text: String) {
        let existing = (field.value as? String) ?? ""
        field.tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: existing.count))
        field.typeText(text)
    }
}
