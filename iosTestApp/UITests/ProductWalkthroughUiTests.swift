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
        app.launch()

        let measure = app.buttons["Measure"]
        XCTAssertTrue(measure.waitForExistence(timeout: 30), "map home never appeared")
        capture("01-map-home")

        measure.tap()

        let start = app.buttons["Start measurement"]
        XCTAssertTrue(start.waitForExistence(timeout: 15), "pre-flight never appeared")
        XCTAssertTrue(app.staticTexts["I am in a moving vehicle"].exists)
        capture("02-start-measurement")

        let inVehicle = app.switches.firstMatch
        if inVehicle.exists {
            inVehicle.tap()
            capture("03-in-vehicle")
            // Back to off, so the captured run reflects the ordinary case.
            inVehicle.tap()
        }

        start.tap()

        // The simulator is always on Wi-Fi, so the pre-flight confirmation is
        // expected here rather than incidental.
        let measureAnyway = app.buttons["Measure anyway"]
        if measureAnyway.waitForExistence(timeout: 5) {
            capture("04-wifi-confirmation")
            measureAnyway.tap()
        }

        let stop = app.buttons["Stop measurement"]
        XCTAssertTrue(stop.waitForExistence(timeout: 20), "run screen never appeared")
        capture("05-run-in-progress")

        let done = app.buttons["Done"]
        XCTAssertTrue(done.waitForExistence(timeout: 120), "run never completed")
        capture("06-results")

        done.tap()
        XCTAssertTrue(measure.waitForExistence(timeout: 20), "did not return to map home")
        capture("07-map-home-after")
    }

    private func capture(_ name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        try? data.write(to: root.appendingPathComponent("\(name).png"), options: [.atomic])
    }
}
