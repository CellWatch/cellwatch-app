import XCTest

final class OnboardingFlowUiTests: XCTestCase {
    private let screenshotRoot = URL(fileURLWithPath: "/tmp", isDirectory: true)
        .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
        .appendingPathComponent("ios", isDirectory: true)
        .appendingPathComponent("onboarding-profile-entry-xcuitest", isDirectory: true)

    override func setUpWithError() throws {
        continueAfterFailure = false
        try FileManager.default.createDirectory(at: screenshotRoot, withIntermediateDirectories: true)
    }

    func testOnboardingFlow_roundTripPersistenceScreenshots() throws {
        // Pass 1: first-run entry and save.
        let first = launchOnboarding(clearProfile: true)
        let firstNameField = first.textFields["harness.onboarding.name"]
        let firstPhoneField = first.textFields["harness.onboarding.phone"]
        let firstEmailField = first.textFields["harness.onboarding.email"]
        let firstAckSwitch = first.switches["harness.onboarding.ack"]
        let firstSaveButton = first.buttons["harness.onboarding.submit"]

        captureScreenshot(named: OnboardingUiScenario.screenshots[0])
        clearAndType(firstNameField, text: OnboardingUiScenario.name)
        captureScreenshot(named: OnboardingUiScenario.screenshots[1])
        clearAndType(firstPhoneField, text: OnboardingUiScenario.phone)
        captureScreenshot(named: OnboardingUiScenario.screenshots[2])
        clearAndType(firstEmailField, text: OnboardingUiScenario.email)
        captureScreenshot(named: OnboardingUiScenario.screenshots[3])
        dismissKeyboardIfPresent(first)
        if (firstAckSwitch.value as? String) != "1" {
            firstAckSwitch.tap()
        }
        captureScreenshot(named: OnboardingUiScenario.screenshots[4])
        dismissKeyboardIfPresent(first)
        firstSaveButton.tap()
        XCTAssertTrue(first.staticTexts["Profile saved."].waitForExistence(timeout: 5))
        captureScreenshot(named: OnboardingUiScenario.screenshots[5])
        XCTAssertEqual(firstPhoneField.value as? String, "404-555-1212")
        first.terminate()

        // Pass 2: relaunch and verify prefill, then edit + save.
        let reopened = launchOnboarding(clearProfile: false)
        let reopenedNameField = reopened.textFields["harness.onboarding.name"]
        let reopenedPhoneField = reopened.textFields["harness.onboarding.phone"]
        let reopenedEmailField = reopened.textFields["harness.onboarding.email"]
        let reopenedAckSwitch = reopened.switches["harness.onboarding.ack"]
        let reopenedSaveButton = reopened.buttons["harness.onboarding.submit"]
        captureScreenshot(named: OnboardingUiScenario.screenshots[6])

        XCTAssertEqual(reopenedNameField.value as? String, OnboardingUiScenario.name)
        XCTAssertEqual(reopenedPhoneField.value as? String, "404-555-1212")
        XCTAssertEqual(reopenedEmailField.value as? String, OnboardingUiScenario.email)
        XCTAssertEqual(reopenedAckSwitch.value as? String, "1")

        clearAndType(reopenedPhoneField, text: OnboardingUiScenario.updatedPhone)
        clearAndType(reopenedEmailField, text: OnboardingUiScenario.updatedEmail)
        captureScreenshot(named: OnboardingUiScenario.screenshots[7])
        dismissKeyboardIfPresent(reopened)
        reopenedSaveButton.tap()
        XCTAssertTrue(reopened.staticTexts["Profile saved."].waitForExistence(timeout: 5))
        captureScreenshot(named: OnboardingUiScenario.screenshots[8])
        XCTAssertEqual(reopenedPhoneField.value as? String, "404-111-2222")
        XCTAssertEqual(reopenedEmailField.value as? String, OnboardingUiScenario.updatedEmail)
        reopened.terminate()

        // Pass 3: final relaunch verifies persisted edit round-trip.
        let verifyUpdated = launchOnboarding(clearProfile: false)
        let verifyPhoneField = verifyUpdated.textFields["harness.onboarding.phone"]
        let verifyEmailField = verifyUpdated.textFields["harness.onboarding.email"]
        captureScreenshot(named: OnboardingUiScenario.screenshots[9])
        XCTAssertEqual(verifyPhoneField.value as? String, "404-111-2222")
        XCTAssertEqual(verifyEmailField.value as? String, OnboardingUiScenario.updatedEmail)
        verifyUpdated.terminate()
    }

    func testOnboardingFlow_validationFailureFeedback() throws {
        let app = launchOnboarding(clearProfile: true)
        clearAndType(app.textFields["harness.onboarding.name"], text: "Jane Doe")
        clearAndType(app.textFields["harness.onboarding.phone"], text: "404")
        clearAndType(app.textFields["harness.onboarding.email"], text: "bad")
        dismissKeyboardIfPresent(app)
        app.buttons["harness.onboarding.submit"].tap()

        XCTAssertTrue(app.staticTexts["Fix validation errors and try again."].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Profile saved."].exists)
        app.terminate()
    }

    private func launchOnboarding(clearProfile: Bool) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment["CELLWATCH_UI_MODE"] = "onboarding-flow"
        if clearProfile {
            app.launchEnvironment["CELLWATCH_CLEAR_ONBOARDING"] = "1"
        }
        app.launch()

        // Explicit waits keep interaction deterministic on fresh simulator boots.
        XCTAssertTrue(app.textFields["harness.onboarding.name"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.textFields["harness.onboarding.phone"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.textFields["harness.onboarding.email"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.switches["harness.onboarding.ack"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.buttons["harness.onboarding.submit"].waitForExistence(timeout: 8))
        return app
    }

    private func clearAndType(_ field: XCUIElement, text: String) {
        field.tap()
        if let current = field.value as? String, !current.isEmpty, !current.contains("#"), !current.lowercased().contains("name"), !current.lowercased().contains("phone"), !current.lowercased().contains("email") {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        field.typeText(text)
    }

    private func captureScreenshot(named name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        let url = screenshotRoot.appendingPathComponent("\(name).png")
        try? data.write(to: url, options: [.atomic])
    }

    private func dismissKeyboardIfPresent(_ app: XCUIApplication) {
        guard app.keyboards.count > 0 else { return }
        let doneToolbar = app.toolbars.buttons["Done"]
        if doneToolbar.exists {
            doneToolbar.tap()
            return
        }
        let doneKeyboard = app.keyboards.buttons["Done"]
        if doneKeyboard.exists {
            doneKeyboard.tap()
            return
        }
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
    }

}
