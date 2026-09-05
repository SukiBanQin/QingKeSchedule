import XCTest

final class QingKeScheduleUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testFirstLaunchCreatesSemesterAndOpensSettings() throws {
        let app = launchAndCreateSemester()

        XCTAssertTrue(app.buttons["today-tab"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["today-empty"].exists)

        app.buttons["settings-tab"].tap()
        XCTAssertTrue(app.buttons["semester-save-toolbar"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["settings-terminal-header"].exists)
        XCTAssertTrue(app.textFields["semester-name"].exists)
        let calendarSettings = app.descendants(matching: .any)["academic-calendar-settings"]
        scrollToElement(calendarSettings, in: app)
        XCTAssertTrue(calendarSettings.exists)
        XCTAssertTrue(app.switches["weekends-non-teaching-toggle"].exists)
        XCTAssertTrue(app.switches["lunch-break-toggle"].exists)

        let dateControl = app.buttons["calendar-exception-date"]
        let addButton = app.buttons["add-calendar-exception"]
        scrollToElement(addButton, in: app)
        XCTAssertTrue(dateControl.exists)
        XCTAssertTrue(addButton.exists)
        XCTAssertGreaterThanOrEqual(
            addButton.frame.minY - dateControl.frame.maxY,
            8
        )
        addButton.tap()
        XCTAssertTrue(
            app.staticTexts["停课日 / OFF"].waitForExistence(timeout: 5)
        )
    }

    @MainActor
    func testDailyPeriodsCollapseAndExpand() throws {
        let app = launchAndCreateSemester()
        app.buttons["settings-tab"].tap()

        let periodsToggle = app.buttons["daily-periods-toggle"]
        XCTAssertTrue(periodsToggle.waitForExistence(timeout: 5))
        let initiallyCollapsed = XCTNSPredicateExpectation(
            predicate: NSPredicate(
                format: "value CONTAINS %@ AND value CONTAINS %@",
                "10 节",
                "已收起"
            ),
            object: periodsToggle
        )
        XCTAssertEqual(XCTWaiter.wait(for: [initiallyCollapsed], timeout: 5), .completed)
        XCTAssertFalse(app.buttons["add-period"].exists)

        scrollToElement(periodsToggle, in: app)
        periodsToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let expandedToggle = app.buttons["daily-periods-toggle"]
        let addPeriod = app.buttons["add-period"]
        let expanded = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value CONTAINS %@", "已展开"),
            object: expandedToggle
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [expanded], timeout: 5),
            .completed
        )
        XCTAssertTrue(addPeriod.waitForExistence(timeout: 5))

        expandedToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let collapsedToggle = app.buttons["daily-periods-toggle"]
        let collapsedAgain = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value CONTAINS %@", "已收起"),
            object: collapsedToggle
        )
        XCTAssertEqual(XCTWaiter.wait(for: [collapsedAgain], timeout: 5), .completed)
        let addPeriodDisappeared = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: app.buttons["add-period"]
        )
        XCTAssertEqual(XCTWaiter.wait(for: [addPeriodDisappeared], timeout: 5), .completed)
    }

    @MainActor
    func testBothSemesterSaveActionsShowTerminalFeedback() throws {
        let app = launchAndCreateSemester()
        app.buttons["settings-tab"].tap()
        XCTAssertTrue(app.buttons["semester-save-toolbar"].waitForExistence(timeout: 5))

        let toast = app.descendants(matching: .any)["semester-save-success"]
        app.buttons["semester-save-toolbar"].tap()
        XCTAssertTrue(toast.waitForExistence(timeout: 5))
        XCTAssertTrue(toast.label.contains("学期与提醒设置已保存"))

        let toastDisappeared = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: toast
        )
        XCTAssertEqual(XCTWaiter.wait(for: [toastDisappeared], timeout: 5), .completed)

        let mainSaveButton = app.buttons["semester-save"]
        scrollToElement(mainSaveButton, in: app)
        mainSaveButton.tap()
        XCTAssertTrue(toast.waitForExistence(timeout: 5))
        XCTAssertTrue(toast.label.contains("SYSTEM"))
    }

    @MainActor
    func testCourseCreateConflictEditWeekAndDeleteFlow() throws {
        let app = launchAndCreateSemester()

        app.buttons["add-course-today-toolbar"].tap()
        enterCourseName("课程 A", in: app)
        app.buttons["course-save"].tap()
        let courseAddedToast = waitForCourseOperationToast("课程添加成功", in: app)
        XCTAssertLessThanOrEqual(
            courseAddedToast.frame.maxY,
            app.buttons["today-tab"].frame.minY
        )
        let courseAddedToastDisappeared = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: courseAddedToast
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [courseAddedToastDisappeared], timeout: 5),
            .completed
        )
        XCTAssertTrue(app.staticTexts["课程 A"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["today-course-sequence"].exists)

        app.buttons["add-course-today-toolbar"].tap()
        XCTAssertTrue(app.buttons["add-new-course"].waitForExistence(timeout: 5))
        let reuseCourse = app.buttons.matching(NSPredicate(
            format: "identifier BEGINSWITH %@ AND label CONTAINS %@",
            "reuse-course-",
            "课程 A"
        )).firstMatch
        XCTAssertTrue(reuseCourse.exists)
        reuseCourse.tap()
        XCTAssertTrue(app.descendants(matching: .any)["reused-course-profile"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.textFields["course-name"].exists)
        app.buttons["course-save"].tap()
        let duplicateError = app.staticTexts["course-validation-error"]
        XCTAssertTrue(duplicateError.waitForExistence(timeout: 5))
        XCTAssertTrue(duplicateError.label.contains("该上课安排已存在，请勿重复添加"))
        XCTAssertFalse(app.buttons["仍然保存"].exists)
        XCTAssertTrue(app.buttons["course-save"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["course-operation-success"].exists)
        let duplicateClassroom = app.textFields["course-classroom-1"]
        scrollToElement(duplicateClassroom, in: app)
        replaceText(in: duplicateClassroom, with: "A102")
        app.buttons["course-save"].tap()
        _ = waitForCourseOperationToast("添加上课安排成功", in: app)

        let courseACards = app.buttons.matching(NSPredicate(
            format: "identifier BEGINSWITH %@ AND label CONTAINS %@",
            "today-course-",
            "课程 A"
        ))
        XCTAssertTrue(courseACards.firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(courseACards.count, 2)

        app.buttons["add-course-today-toolbar"].tap()
        XCTAssertTrue(app.buttons["add-new-course"].waitForExistence(timeout: 5))
        app.buttons["add-new-course"].tap()
        enterCourseName("课程 B", in: app)
        app.buttons["course-save"].tap()
        XCTAssertTrue(app.staticTexts["检测到课程冲突"].waitForExistence(timeout: 5))
        app.buttons["返回修改"].tap()
        XCTAssertTrue(app.textFields["course-name"].exists)
        app.buttons["course-save"].tap()
        XCTAssertTrue(app.buttons["仍然保存"].waitForExistence(timeout: 5))
        app.buttons["仍然保存"].tap()
        _ = waitForCourseOperationToast("课程添加成功", in: app)
        XCTAssertTrue(app.staticTexts["课程 B"].firstMatch.waitForExistence(timeout: 5))

        app.buttons["schedule-tab"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["week-schedule"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["week-matrix"].waitForExistence(timeout: 5))
        scrollToElement(app.staticTexts["week-matrix-day-1"], in: app)
        for day in 1...7 {
            XCTAssertTrue(app.staticTexts["week-matrix-day-\(day)"].exists)
        }
        XCTAssertTrue(app.buttons["add-course-week-toolbar"].isHittable)
        let matrixCourse = app.buttons.matching(NSPredicate(
            format: "identifier BEGINSWITH %@ AND label CONTAINS %@",
            "week-matrix-course-",
            "课程 A"
        )).firstMatch
        scrollToElement(matrixCourse, in: app)
        XCTAssertTrue(matrixCourse.label.contains("存在冲突"))

        let selectedWeek = app.buttons["selected-week"]
        app.buttons["week-next"].tap()
        let nextWeekSelected = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label CONTAINS %@", "第 2 周"),
            object: selectedWeek
        )
        XCTAssertEqual(XCTWaiter.wait(for: [nextWeekSelected], timeout: 5), .completed)
        app.buttons["week-previous"].tap()
        let firstWeekSelected = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label CONTAINS %@", "第 1 周"),
            object: selectedWeek
        )
        XCTAssertEqual(XCTWaiter.wait(for: [firstWeekSelected], timeout: 5), .completed)

        app.buttons["today-tab"].tap()
        app.staticTexts["课程 A"].firstMatch.tap()
        let nameField = app.textFields["course-name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        replaceText(in: nameField, with: "课程 A 已修改")
        app.buttons["course-save"].tap()
        XCTAssertTrue(app.buttons["仍然保存"].waitForExistence(timeout: 5))
        app.buttons["仍然保存"].tap()
        _ = waitForCourseOperationToast("课程修改已保存", in: app)
        XCTAssertTrue(app.staticTexts["课程 A 已修改"].firstMatch.waitForExistence(timeout: 5))

        app.staticTexts["课程 B"].firstMatch.tap()
        XCTAssertFalse(app.buttons["course-delete-toolbar"].exists)
        let deleteButton = app.buttons["course-delete"]
        scrollToElement(deleteButton, in: app)
        XCTAssertEqual(app.buttons.matching(identifier: "course-delete").count, 1)
        deleteButton.tap()
        XCTAssertTrue(app.alerts["删除这门课程？"].waitForExistence(timeout: 5))
        app.alerts.buttons["确认删除"].tap()
        _ = waitForCourseOperationToast("课程删除成功", in: app)
        XCTAssertFalse(app.staticTexts["课程 B"].firstMatch.waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["课程 A 已修改"].firstMatch.exists)
    }

    @MainActor
    private func waitForCourseOperationToast(
        _ message: String,
        in app: XCUIApplication
    ) -> XCUIElement {
        let toast = app.descendants(matching: .any)["course-operation-success"]
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(
                format: "exists == true AND label CONTAINS %@",
                message
            ),
            object: toast
        )
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: 5), .completed)
        return toast
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
        XCTAssertTrue(app.descendants(matching: .any)["onboarding-terminal-header"].exists)
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
    func testDarkModeCoreScreensRemainUsable() throws {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "-AppleInterfaceStyle", "Dark"]
        app.launch()

        XCTAssertTrue(app.descendants(matching: .any)["onboarding-terminal-header"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["semester-save-toolbar"].isHittable)
        app.buttons["semester-save-toolbar"].tap()

        XCTAssertTrue(app.descendants(matching: .any)["today-empty"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["add-course-today-toolbar"].isHittable)
        XCTAssertTrue(app.buttons["today-tab"].isHittable)

        app.buttons["schedule-tab"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["week-schedule"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["week-matrix"].exists)
        XCTAssertTrue(app.buttons["add-course-week-toolbar"].isHittable)

        app.buttons["settings-tab"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["settings-terminal-header"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["semester-name"].exists)
        let calendarSettings = app.descendants(matching: .any)["academic-calendar-settings"]
        scrollToElement(calendarSettings, in: app)
        XCTAssertTrue(calendarSettings.exists)

        app.buttons["today-tab"].tap()
        app.buttons["add-course-today-toolbar"].tap()
        XCTAssertTrue(app.buttons["course-cancel"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["course-save"].exists)
        XCTAssertTrue(app.buttons["add-course-schedule"].exists)
        XCTAssertTrue(app.buttons["青绿色"].exists)
        app.buttons["course-cancel"].tap()
        XCTAssertTrue(app.buttons["today-tab"].isHittable)
    }

    @MainActor
    func testAppearancePreferenceAppliesImmediately() throws {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "-AppleInterfaceStyle", "Dark"]
        app.launch()

        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        app.buttons["semester-save-toolbar"].tap()
        XCTAssertTrue(app.buttons["settings-tab"].waitForExistence(timeout: 5))
        app.buttons["settings-tab"].tap()

        let appearanceSettings = app.descendants(matching: .any)["appearance-settings"]
        scrollToElement(appearanceSettings, in: app)

        let systemButton = app.buttons["appearance-system"]
        XCTAssertTrue(systemButton.exists)
        XCTAssertEqual(systemButton.value as? String, "已选择")
        XCTAssertEqual(
            app.descendants(matching: .any)["appearance-effective-style"].label,
            "当前显示：深色"
        )

        app.buttons["appearance-light"].tap()
        let lightAppearance = app.descendants(matching: .any)["appearance-effective-style"]
        let lightExpectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "当前显示：浅色"),
            object: lightAppearance
        )
        XCTAssertEqual(XCTWaiter.wait(for: [lightExpectation], timeout: 5), .completed)
        XCTAssertEqual(app.buttons["appearance-light"].value as? String, "已选择")

        app.buttons["appearance-dark"].tap()
        let darkAppearance = app.descendants(matching: .any)["appearance-effective-style"]
        let darkExpectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "当前显示：深色"),
            object: darkAppearance
        )
        XCTAssertEqual(XCTWaiter.wait(for: [darkExpectation], timeout: 5), .completed)
        XCTAssertEqual(app.buttons["appearance-dark"].value as? String, "已选择")

        app.buttons["appearance-system"].tap()
        let systemAppearance = app.descendants(matching: .any)["appearance-effective-style"]
        let systemExpectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label == %@", "当前显示：深色"),
            object: systemAppearance
        )
        XCTAssertEqual(XCTWaiter.wait(for: [systemExpectation], timeout: 5), .completed)
        XCTAssertEqual(app.buttons["appearance-system"].value as? String, "已选择")
    }

    @MainActor
    func testValidImportPreviewCancelConfirmAndExportEntry() throws {
        let app = launchForTransferTest(
            fixture: "Shared/fixtures/valid/web-export.json"
        )
        XCTAssertTrue(app.staticTexts["替换当前课表？"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(
            format: "label CONTAINS %@",
            "学期：2026 秋季学期"
        )).firstMatch.exists)
        app.buttons["取消"].tap()
        XCTAssertFalse(app.buttons["today-tab"].exists)

        let importButton = app.buttons["schedule-import"]
        scrollToElement(importButton, in: app)
        let importFormat = app.descendants(matching: .any)["schedule-import-format"]
        XCTAssertTrue(importFormat.exists)
        XCTAssertTrue(importFormat.label.contains("仅支持青课 JSON 备份文件"))
        XCTAssertTrue(importFormat.label.contains("暂不支持 Excel"))
        XCTAssertEqual(importButton.label, "从 JSON 文件导入课表")

        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["替换当前课表"].waitForExistence(timeout: 5))
        app.buttons["替换当前课表"].tap()
        XCTAssertTrue(app.buttons["today-tab"].waitForExistence(timeout: 5))

        app.buttons["settings-tab"].tap()
        let semesterName = app.textFields["semester-name"]
        XCTAssertTrue(semesterName.waitForExistence(timeout: 5))
        XCTAssertEqual(semesterName.value as? String, "2026 秋季学期")
        let periodsToggle = app.buttons["daily-periods-toggle"]
        XCTAssertTrue(periodsToggle.waitForExistence(timeout: 5))
        let importedPeriodsExpanded = XCTNSPredicateExpectation(
            predicate: NSPredicate(
                format: "value CONTAINS %@ AND value CONTAINS %@",
                "4 节",
                "已展开"
            ),
            object: periodsToggle
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [importedPeriodsExpanded], timeout: 5),
            .completed
        )
        XCTAssertTrue(app.buttons["add-period"].exists)
        let exportButton = app.buttons["schedule-export"]
        scrollToElement(exportButton, in: app)
        XCTAssertTrue(exportButton.exists)
        XCTAssertEqual(exportButton.label, "分享课表备份")
    }

    @MainActor
    func testInvalidImportShowsErrorAndPreservesOnboarding() throws {
        let app = launchForTransferTest(
            fixture: "Shared/fixtures/invalid/unknown-version.json"
        )
        XCTAssertTrue(app.staticTexts["无法导入课表"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(
            format: "label CONTAINS %@",
            "暂不支持版本 2"
        )).firstMatch.exists)
        app.buttons["好"].tap()
        XCTAssertFalse(app.buttons["today-tab"].exists)
        XCTAssertTrue(app.staticTexts["onboarding-title"].exists)
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
        XCTAssertTrue(app.buttons["today-tab"].waitForExistence(timeout: 5))

        app.buttons["settings-tab"].tap()
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

        app.buttons["today-tab"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["today-empty"].waitForExistence(timeout: 5))
        app.buttons["settings-tab"].tap()

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
    func testCustomReminderLeadTimeIsAvailable() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-reminders-enabled",
            "--ui-testing-custom-reminder",
        ]
        app.launch()

        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        app.buttons["semester-save-toolbar"].tap()
        XCTAssertTrue(app.buttons["settings-tab"].waitForExistence(timeout: 5))
        app.buttons["settings-tab"].tap()

        let picker = app.buttons["reminder-lead-minutes"]
        scrollToElement(picker, in: app)
        let customStepper = app.steppers["reminder-custom-lead-minutes"]
        XCTAssertTrue(customStepper.waitForExistence(timeout: 5))
        XCTAssertTrue(customStepper.label.contains("提前 15 分钟"))

        picker.tap()
        XCTAssertTrue(app.buttons["自定义…"].waitForExistence(timeout: 5))
        app.buttons["自定义…"].tap()
        XCTAssertTrue(customStepper.waitForExistence(timeout: 5))
    }

    @MainActor
    private func launchAndCreateSemester() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()

        XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["semester-name"].exists)
        app.buttons["semester-save-toolbar"].tap()
        XCTAssertTrue(app.buttons["today-tab"].waitForExistence(timeout: 5))
        return app
    }

    @MainActor
    private func launchForTransferTest(fixture: String) -> XCUIApplication {
        let testSourceURL = URL(fileURLWithPath: #filePath)
        let iosRootURL = testSourceURL
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let fixtureURL = iosRootURL.appendingPathComponent(fixture)
        let contents = try! String(contentsOf: fixtureURL, encoding: .utf8)
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-auto-import"]
        app.launchEnvironment["UI_TEST_IMPORT_JSON"] = contents
        app.launch()
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
