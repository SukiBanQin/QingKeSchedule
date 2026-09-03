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
        XCTAssertFalse(app.buttons["course-delete-toolbar"].exists)
        let deleteButton = app.buttons["course-delete"]
        scrollToElement(deleteButton, in: app)
        XCTAssertEqual(app.buttons.matching(identifier: "course-delete").count, 1)
        deleteButton.tap()
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
    func testValidImportPreviewCancelConfirmAndExportEntry() throws {
        let app = launchForTransferTest(
            fixture: "shared/fixtures/valid/web-export.json"
        )
        let testImportButton = app.buttons["schedule-import-test-file"]
        scrollToElement(testImportButton, in: app)

        XCTAssertTrue(app.buttons["schedule-import"].exists)
        testImportButton.tap()
        XCTAssertTrue(app.staticTexts["替换当前课表？"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(
            format: "label CONTAINS %@",
            "学期：2026 秋季学期"
        )).firstMatch.exists)
        app.alerts.buttons["取消"].tap()
        XCTAssertFalse(app.tabBars.buttons["今日"].exists)
        XCTAssertTrue(testImportButton.exists)

        testImportButton.tap()
        XCTAssertTrue(app.buttons["替换当前课表"].waitForExistence(timeout: 5))
        app.alerts.buttons["替换当前课表"].tap()
        XCTAssertTrue(app.tabBars.buttons["今日"].waitForExistence(timeout: 5))

        app.tabBars.buttons["设置"].tap()
        let semesterName = app.textFields["semester-name"]
        XCTAssertTrue(semesterName.waitForExistence(timeout: 5))
        XCTAssertEqual(semesterName.value as? String, "2026 秋季学期")
        let exportButton = app.buttons["schedule-export"]
        scrollToElement(exportButton, in: app)
        XCTAssertTrue(exportButton.exists)
        XCTAssertEqual(exportButton.label, "分享课表备份")
    }

    @MainActor
    func testInvalidImportShowsErrorAndPreservesOnboarding() throws {
        let app = launchForTransferTest(
            fixture: "shared/fixtures/invalid/unknown-version.json"
        )
        let testImportButton = app.buttons["schedule-import-test-file"]
        scrollToElement(testImportButton, in: app)

        testImportButton.tap()
        XCTAssertTrue(app.alerts["无法导入课表"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts.staticTexts.matching(NSPredicate(
            format: "label CONTAINS %@",
            "暂不支持版本 2"
        )).firstMatch.exists)
        app.alerts.buttons["好"].tap()
        XCTAssertFalse(app.tabBars.buttons["今日"].exists)
        XCTAssertTrue(testImportButton.exists)
    }

    @MainActor
    func testDeniedNotificationsCanBeDisabledWithoutBlockingApp() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-notifications-denied",
            "--ui-testing-reminders-enabled",
        ]
        app.launch()

        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        app.buttons["semester-save-toolbar"].tap()
        XCTAssertTrue(app.tabBars.buttons["今日"].waitForExistence(timeout: 5))

        app.tabBars.buttons["设置"].tap()
        let reminderToggle = app.switches["reminders-toggle"]
        scrollToElement(reminderToggle, in: app)
        XCTAssertEqual(reminderToggle.value as? String, "1")

        let deniedStatus = app.staticTexts.matching(NSPredicate(
            format: "identifier == %@ AND label CONTAINS %@",
            "reminders-status",
            "系统通知权限已关闭"
        )).firstMatch
        XCTAssertTrue(deniedStatus.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["system-notification-settings"].waitForExistence(timeout: 5))

        app.tabBars.buttons["今日"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["today-empty"].waitForExistence(timeout: 5))
        app.tabBars.buttons["设置"].tap()

        let reminderToggleAgain = app.switches["reminders-toggle"]
        scrollToElement(reminderToggleAgain, in: app)
        reminderToggleAgain.coordinate(
            withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)
        ).tap()
        let disabledToggle = app.switches.matching(NSPredicate(
            format: "identifier == %@ AND value == %@",
            "reminders-toggle",
            "0"
        )).firstMatch
        XCTAssertTrue(disabledToggle.waitForExistence(timeout: 5))
        let disabledStatus = app.staticTexts.matching(NSPredicate(
            format: "identifier == %@",
            "reminders-status"
        )).firstMatch
        XCTAssertTrue(disabledStatus.waitForExistence(timeout: 5))
        XCTAssertEqual(disabledStatus.label, "提醒已关闭")
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
    private func launchForTransferTest(fixture: String) -> XCUIApplication {
        let testSourceURL = URL(fileURLWithPath: #filePath)
        let repositoryURL = testSourceURL
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let fixtureURL = repositoryURL.appendingPathComponent(fixture)
        let contents = try! String(contentsOf: fixtureURL, encoding: .utf8)
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-transfer-controls"]
        app.launchEnvironment["UI_TEST_IMPORT_JSON"] = contents
        app.launch()
        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        return app
    }

    @MainActor
    private func scrollToElement(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<8 where !element.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(element.waitForExistence(timeout: 5))
        XCTAssertTrue(element.isHittable)
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
