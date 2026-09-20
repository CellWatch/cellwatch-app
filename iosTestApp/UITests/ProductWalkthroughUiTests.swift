import XCTest

/// Drives the product shell through the vertical slice and captures evidence.
///
/// XCUITest rather than a `simctl` script because the simulator has no
/// scriptable tap: `simctl` can launch and screenshot but not interact, which
/// is why the original flow reports were XCUITest-based too.
///
/// Elements are found by their visible labels rather than accessibility
/// identifiers. The product screens carry no identifiers yet, and using the
/// labels a user actually reads keeps the walkthrough honest - if the wording
/// changes, this fails rather than quietly documenting a stale flow.
final class ProductWalkthroughUiTests: XCTestCase {

    private let root = URL(fileURLWithPath: "/tmp", isDirectory: true)
        .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
        .appendingPathComponent("ios", isDirectory: true)
        .appendingPathComponent("product-walkthrough", isDirectory: true)

    override func setUpWithError() throws {
        continueAfterFailure = false
        try? FileManager.default.removeItem(at: root)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    func testVerticalSlice_launchToResults() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-CellWatchProductShell"]
        // Cleared so the walkthrough always starts where a new user does.
        // Without it the run begins wherever the last one left the simulator,
        // and the first page documents a different app depending on the day.
        app.launchEnvironment["CELLWATCH_CLEAR_ONBOARDING"] = "1"
        app.launch()

        // Consent comes before anything is collected.
        XCTAssertTrue(app.staticTexts["Data Use"].waitForExistence(timeout: 30), "data use never appeared")
        capture("01-data-use")
        app.buttons["Continue"].tap()

        XCTAssertTrue(
            app.staticTexts["Collection Mode"].waitForExistence(timeout: 15),
            "collection mode never appeared"
        )
        // Challenge mode is preselected, so the acknowledgement gates Continue.
        app.switches.element(boundBy: 1).tap()
        capture("02-collection-mode")
        app.buttons["Continue"].tap()

        let nameField = app.textFields["Full name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 15), "profile never appeared")
        capture("03-profile-empty")

        type(nameField, "Jeff Wilson")
        type(app.textFields["Phone (###-###-####)"], "404-555-0142")
        type(app.textFields["Email"], "jw199@gatech.edu")
        capture("04-profile-complete")

        let save = app.buttons["Save profile"]
        XCTAssertTrue(save.isHittable, "Save profile is not reachable - is the keyboard covering it?")
        save.tap()

        let measure = app.buttons["Measure"]
        if !measure.waitForExistence(timeout: 30) {
            // Captured before failing, so the reason is visible in the artifacts
            // rather than needing the run to be repeated by hand.
            capture("99-save-failed")
            XCTFail("map home never appeared after saving the profile")
        }
        capture("05-map-home")

        measure.tap()

        let start = app.buttons["Start measurement"]
        XCTAssertTrue(start.waitForExistence(timeout: 15), "pre-flight never appeared")
        XCTAssertTrue(app.staticTexts["I am in a moving vehicle"].exists)
        capture("06-start-measurement")

        let inVehicle = app.switches.firstMatch
        if inVehicle.exists {
            inVehicle.tap()
            capture("07-in-vehicle")
            // Back to off, so the captured run reflects the ordinary case.
            inVehicle.tap()
        }

        start.tap()

        // The simulator is always on Wi-Fi, so the pre-flight confirmation is
        // expected here rather than incidental.
        let measureAnyway = app.buttons["Measure anyway"]
        if measureAnyway.waitForExistence(timeout: 5) {
            capture("08-wifi-confirmation")
            measureAnyway.tap()
        }

        let stop = app.buttons["Stop measurement"]
        XCTAssertTrue(stop.waitForExistence(timeout: 20), "run screen never appeared")
        capture("09-run-in-progress")

        let done = app.buttons["Done"]
        XCTAssertTrue(done.waitForExistence(timeout: 120), "run never completed")
        capture("10-results")

        done.tap()
        XCTAssertTrue(measure.waitForExistence(timeout: 20), "did not return to map home")
        capture("11-map-home-after")

        let history = app.buttons["History & sync"]
        XCTAssertTrue(history.waitForExistence(timeout: 10))
        history.tap()
        XCTAssertTrue(app.buttons["Back to map"].waitForExistence(timeout: 15), "history never appeared")
        capture("12-history")

        let export = app.buttons["Export data"]
        XCTAssertTrue(export.waitForExistence(timeout: 10))
        export.tap()
        XCTAssertTrue(app.buttons["Export full data"].waitForExistence(timeout: 10), "export never appeared")
        capture("13-export")

        app.buttons["Back to map"].tap()
        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 15))
        settings.tap()
        XCTAssertTrue(app.buttons["Save settings"].waitForExistence(timeout: 10), "settings never appeared")
        capture("14-settings")
    }

    /// Taps in before typing; a field that is not first responder swallows the text.
    private func type(_ field: XCUIElement, _ text: String) {
        XCTAssertTrue(field.waitForExistence(timeout: 10), "missing field for \(text)")
        field.tap()
        field.typeText(text)
    }

    private func capture(_ name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        try? data.write(to: root.appendingPathComponent("\(name).png"), options: [.atomic])
    }
}
