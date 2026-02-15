import XCTest

final class OnboardingFlowUiTests: XCTestCase {
    private let screenshotRoot = URL(fileURLWithPath: "/tmp", isDirectory: true)
        .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
        .appendingPathComponent("ios", isDirectory: true)
        .appendingPathComponent("onboarding-profile-entry-xcuitest", isDirectory: true)
    private let measurementStartScreenshotRoot = URL(fileURLWithPath: "/tmp", isDirectory: true)
        .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
        .appendingPathComponent("ios", isDirectory: true)
        .appendingPathComponent("measurement-start-preflight-xcuitest", isDirectory: true)
    private let phase3SequenceScreenshotRoot = URL(fileURLWithPath: "/tmp", isDirectory: true)
        .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
        .appendingPathComponent("ios", isDirectory: true)
        .appendingPathComponent("phase3-sequence-button", isDirectory: true)
    private let pendingSyncScreenshotRoot = URL(fileURLWithPath: "/tmp", isDirectory: true)
        .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
        .appendingPathComponent("ios", isDirectory: true)
        .appendingPathComponent("pending-sync-retry-xcuitest", isDirectory: true)
    private let measurementRunScreenshotRoot = URL(fileURLWithPath: "/tmp", isDirectory: true)
        .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
        .appendingPathComponent("ios", isDirectory: true)
        .appendingPathComponent("measurement-run-flow-xcuitest", isDirectory: true)

    override func setUpWithError() throws {
        continueAfterFailure = false
        try FileManager.default.createDirectory(at: screenshotRoot, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: measurementStartScreenshotRoot, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: phase3SequenceScreenshotRoot, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: pendingSyncScreenshotRoot, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: measurementRunScreenshotRoot, withIntermediateDirectories: true)
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

    func testMeasurementStartPreflight_flowShowsConfirmGateForUnknownPath() throws {
        let app = XCUIApplication()
        app.launchEnvironment["CELLWATCH_UI_MODE"] = "measurement-start-flow"
        app.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_COLLECTION_MODE"] = "fcc_challenge"
        app.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_LOCATION_PERMISSION"] = "1"
        app.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_RUNTIME_PROFILE"] = "1"
        app.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_NETWORK_PATH"] = "wifi"
        app.launch()

        let inVehicleSwitch = app.switches["harness.measurementStart.inVehicle"]
        let evaluateButton = app.buttons["harness.measurementStart.evaluate"]
        let output = app.staticTexts["harness.measurementStart.output"]

        XCTAssertTrue(inVehicleSwitch.waitForExistence(timeout: 8))
        XCTAssertTrue(evaluateButton.waitForExistence(timeout: 8))
        XCTAssertTrue(output.waitForExistence(timeout: 8))
        captureMeasurementStartScreenshot(named: "01-ready")

        evaluateButton.tap()
        captureMeasurementStartScreenshot(named: "02-wifi-warning-dialog")
        XCTAssertTrue(app.alerts.element.waitForExistence(timeout: 5))
        app.alerts.buttons["Measure anyway"].tap()
        XCTAssertTrue(output.waitForExistence(timeout: 5))
        captureMeasurementStartScreenshot(named: "03-wifi-confirmed-allowed")
        XCTAssertTrue(output.label.contains("Preflight passed. You can start measuring."))

        app.terminate()

        let unknown = XCUIApplication()
        unknown.launchEnvironment["CELLWATCH_UI_MODE"] = "measurement-start-flow"
        unknown.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_COLLECTION_MODE"] = "fcc_challenge"
        unknown.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_LOCATION_PERMISSION"] = "1"
        unknown.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_RUNTIME_PROFILE"] = "1"
        unknown.launchEnvironment["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_NETWORK_PATH"] = "unknown"
        unknown.launch()
        let unknownEvaluate = unknown.buttons["harness.measurementStart.evaluate"]
        let unknownOutput = unknown.staticTexts["harness.measurementStart.output"]
        XCTAssertTrue(unknownEvaluate.waitForExistence(timeout: 8))
        XCTAssertTrue(unknownOutput.waitForExistence(timeout: 8))
        unknownEvaluate.tap()
        XCTAssertTrue(unknown.alerts.element.waitForExistence(timeout: 5))
        captureMeasurementStartScreenshot(named: "04-unknown-warning-dialog")
        unknown.alerts.buttons["Cancel"].tap()
        XCTAssertTrue(unknownOutput.label.contains("Wi-Fi detected. Choose Measure anyway or Cancel."))
        unknown.terminate()
    }

    func testPhase3SequenceButton_flowRunsWithTrueUiTap() throws {
        let app = XCUIApplication()
        app.launchEnvironment["CELLWATCH_UI_MODE"] = "full-harness"
        app.launchEnvironment["CELLWATCH_CLEAR_ONBOARDING"] = "1"
        assignOptionalRuntimeValue("SUPABASE_LOCAL_URL", to: app)
        assignOptionalRuntimeValue("SUPABASE_LOCAL_SERVICE_KEY", to: app)
        assignOptionalRuntimeValue("MSAK_LOCAL_SERVER_HOST", to: app)
        app.launch()

        let runButton = app.buttons["harness.phase3.sequenceButton"]
        let output = app.textViews["harness.outputTextView"]

        XCTAssertTrue(runButton.waitForExistence(timeout: 10))
        XCTAssertTrue(output.waitForExistence(timeout: 10))
        capturePhase3SequenceScreenshot(named: "01-ready")

        runButton.tap()
        XCTAssertTrue(
            waitForOutputText(
                app: app,
                containsAny: [
                    "smokeEnvelope scenario=phase3-sequence-sync",
                    "smokeEnvelope scenario=phase3-preflight",
                    "smokeEnvelope scenario=measurement-start-preflight"
                ],
                timeout: 150
            ),
            "Expected phase3 smoke envelope in output text view."
        )

        capturePhase3SequenceScreenshot(named: "02-after-phase3")
        let outputText = output.value as? String ?? ""
        XCTAssertTrue(
            outputText.contains("status=SUCCESS"),
            "Expected SUCCESS envelope after true UI tap; output was: \(outputText)"
        )
        XCTAssertFalse(
            outputText.contains("status=FAILURE"),
            "Expected no FAILURE envelope after true UI tap; output was: \(outputText)"
        )
        app.terminate()
    }

    func testPendingSyncRetry_flowShowsCountsAndRetryStatus() throws {
        let app = XCUIApplication()
        app.launchEnvironment["CELLWATCH_UI_MODE"] = "pending-sync-flow"
        app.launchEnvironment["CELLWATCH_CLEAR_ONBOARDING"] = "1"
        assignOptionalRuntimeValue("SUPABASE_LOCAL_URL", to: app)
        assignOptionalRuntimeValue("SUPABASE_LOCAL_SERVICE_KEY", to: app)
        app.launch()

        let countsButton = app.buttons["harness.pendingSync.countsButton"]
        let retryButton = app.buttons["harness.pendingSync.retryButton"]
        let summary = app.staticTexts["harness.pendingSync.summary"]
        let detail = app.staticTexts["harness.pendingSync.detail"]

        XCTAssertTrue(countsButton.waitForExistence(timeout: 10))
        XCTAssertTrue(retryButton.waitForExistence(timeout: 10))
        XCTAssertTrue(summary.waitForExistence(timeout: 10))
        XCTAssertTrue(detail.waitForExistence(timeout: 10))
        capturePendingSyncScreenshot(named: "01-ready")

        countsButton.tap()
        XCTAssertTrue(waitForText(element: summary, contains: "Pending uploads:", timeout: 60))
        XCTAssertTrue(summary.label.contains("Pending uploads:"))
        capturePendingSyncScreenshot(named: "02-counts")

        retryButton.tap()
        XCTAssertTrue(
            waitForAnyText(
                element: detail,
                containsAny: [
                    "Sync complete",
                    "Sync partially complete",
                    "Sync failed",
                    "Sync still pending",
                    "No pending uploads"
                ],
                timeout: 90
            )
        )
        XCTAssertTrue(
            summary.label.contains("Sync") || summary.label.contains("No pending uploads"),
            "Expected user-facing pending sync summary, got: \(summary.label)"
        )
        capturePendingSyncScreenshot(named: "03-retry")
        app.terminate()
    }

    func testMeasurementRunFlow_showsLiveProgressAndCompletionState() throws {
        let app = XCUIApplication()
        app.launchEnvironment["CELLWATCH_UI_MODE"] = "measurement-run-flow"
        app.launchEnvironment["CELLWATCH_CLEAR_ONBOARDING"] = "1"
        assignOptionalRuntimeValue("SUPABASE_LOCAL_URL", to: app)
        assignOptionalRuntimeValue("SUPABASE_LOCAL_SERVICE_KEY", to: app)
        assignOptionalRuntimeValue("MSAK_LOCAL_SERVER_HOST", to: app)
        app.launch()

        let startButton = app.buttons["harness.measurementRun.start"]
        let header = app.staticTexts["harness.measurementRun.header"]
        let detail = app.staticTexts["harness.measurementRun.detail"]
        let progress = app.progressIndicators["harness.measurementRun.progress"]

        XCTAssertTrue(startButton.waitForExistence(timeout: 10))
        XCTAssertTrue(header.waitForExistence(timeout: 10))
        XCTAssertTrue(detail.waitForExistence(timeout: 10))
        XCTAssertTrue(progress.waitForExistence(timeout: 10))
        captureMeasurementRunScreenshot(named: "01-ready")

        startButton.tap()
        captureMeasurementRunScreenshot(named: "02-after-start-tap")

        let blockedMessages = [
            "Runtime profile unavailable",
            "Configuration issue:",
            "Unable to start measurement right now"
        ]
        if blockedMessages.contains(where: { detail.label.contains($0) }) {
            captureMeasurementRunScreenshot(named: "02-start-blocked")
            XCTFail("Measurement run blocked. header=\(header.label) detail=\(detail.label)")
        }

        XCTAssertTrue(
            waitForProgressState(progress, contains: "LOCATE", timeout: 120),
            "Expected LOCATE state. state=\(progress.value as? String ?? "") header=\(header.label) detail=\(detail.label)"
        )
        captureMeasurementRunScreenshot(named: "03-finding-server")

        XCTAssertTrue(
            waitForProgressState(progress, contains: "LATENCY", timeout: 120),
            "Expected LATENCY state. state=\(progress.value as? String ?? "") header=\(header.label) detail=\(detail.label)"
        )
        captureMeasurementRunScreenshot(named: "04-running-latency")

        XCTAssertTrue(
            waitForAnyProgressState(
                progress,
                containsAny: ["DOWNLOAD", "UPLOAD"],
                timeout: 120
            ),
            "Expected DOWNLOAD or UPLOAD state. state=\(progress.value as? String ?? "") header=\(header.label) detail=\(detail.label)"
        )
        captureMeasurementRunScreenshot(named: "05-running-throughput")

        XCTAssertTrue(
            waitForAnyProgressState(
                progress,
                containsAny: ["END", "ERROR"],
                timeout: 180
            ),
            "Expected END or ERROR state. state=\(progress.value as? String ?? "") header=\(header.label) detail=\(detail.label)"
        )
        captureMeasurementRunScreenshot(named: "06-after-run")
        app.terminate()
    }

    private func waitForProgressState(_ element: XCUIElement, contains: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "value CONTAINS %@", contains)
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
    }

    private func waitForAnyProgressState(
        _ element: XCUIElement,
        containsAny: [String],
        timeout: TimeInterval
    ) -> Bool {
        let patterns = containsAny.joined(separator: "|")
        let predicate = NSPredicate(format: "value MATCHES %@", ".*(\(patterns)).*")
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
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
        if let clearButton = field.buttons.allElementsBoundByIndex.first(where: { $0.label == "Clear text" }), clearButton.exists {
            clearButton.tap()
        } else if let current = field.value as? String,
                  !current.isEmpty,
                  !current.contains("#"),
                  !current.lowercased().contains("name"),
                  !current.lowercased().contains("phone"),
                  !current.lowercased().contains("email") {
            field.press(forDuration: 0.8)
            let selectAll = XCUIApplication().menuItems["Select All"]
            if selectAll.waitForExistence(timeout: 1) {
                selectAll.tap()
                field.typeText(XCUIKeyboardKey.delete.rawValue)
            } else {
                field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
            }
        }
        field.typeText(text)
    }

    private func captureScreenshot(named name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        let url = screenshotRoot.appendingPathComponent("\(name).png")
        try? data.write(to: url, options: [.atomic])
    }

    private func captureMeasurementStartScreenshot(named name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        let url = measurementStartScreenshotRoot.appendingPathComponent("\(name).png")
        try? data.write(to: url, options: [.atomic])
    }

    private func capturePhase3SequenceScreenshot(named name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        let url = phase3SequenceScreenshotRoot.appendingPathComponent("\(name).png")
        try? data.write(to: url, options: [.atomic])
    }

    private func capturePendingSyncScreenshot(named name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        let url = pendingSyncScreenshotRoot.appendingPathComponent("\(name).png")
        try? data.write(to: url, options: [.atomic])
    }

    private func captureMeasurementRunScreenshot(named name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        let url = measurementRunScreenshotRoot.appendingPathComponent("\(name).png")
        try? data.write(to: url, options: [.atomic])
    }

    private func waitForOutputText(app: XCUIApplication, containsAny needles: [String], timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let text = app.textViews["harness.outputTextView"].value as? String ?? ""
            if needles.contains(where: { text.contains($0) }) {
                return true
            }
            RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.5))
        }
        return false
    }

    private func waitForText(element: XCUIElement, contains needle: String, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.label.contains(needle) {
                return true
            }
            RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.5))
        }
        return false
    }

    private func waitForAnyText(element: XCUIElement, containsAny needles: [String], timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let label = element.label
            if needles.contains(where: { label.contains($0) }) {
                return true
            }
            RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.5))
        }
        return false
    }

    private func assignOptionalRuntimeValue(_ key: String, to app: XCUIApplication) {
        guard let value = ProcessInfo.processInfo.environment[key], !value.isEmpty else {
            return
        }
        app.launchEnvironment[key] = value
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
