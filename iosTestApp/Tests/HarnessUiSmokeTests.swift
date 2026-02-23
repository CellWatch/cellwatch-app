import XCTest
import sharedKit
@testable import iosTestApp

final class HarnessUiSmokeTests: XCTestCase {
    func testMapStartSharedSliceButton_tap_updatesStatusLabel() {
        let controller = HarnessViewController()
        controller.loadViewIfNeeded()

        guard let button = findView(
            in: controller.view,
            identifier: HarnessViewController.mapStartSharedSliceButtonIdentifier
        ) as? UIButton else {
            XCTFail("Missing map-start shared-slice button")
            return
        }
        guard let statusLabel = findView(
            in: controller.view,
            identifier: HarnessViewController.statusLabelIdentifier
        ) as? UILabel else {
            XCTFail("Missing status label")
            return
        }

        attachScreenshot(of: controller.view, named: "01-ready")

        let expectation = expectation(description: "status label updated")
        button.sendActions(for: .touchUpInside)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            let rendered = statusLabel.text ?? ""
            XCTAssertFalse(rendered.isEmpty)
            XCTAssertTrue(rendered.contains("smokeEnvelope scenario=map-start-sync"))
            self.attachScreenshot(of: controller.view, named: "02-after-map-start")
            expectation.fulfill()
        }

        waitForExpectations(timeout: 2.0)
    }

    func testMeasurementRunErrorCategory_parityMatrix_matchesSharedContract() {
        let controller = MeasurementRunViewController()

        let network = controller.onCompleted(
            group: nil,
            errorCode: nil,
            errorText: "network timeout during upload"
        )
        XCTAssertEqual(network.errorCategory, .network)

        let auth = controller.onCompleted(
            group: nil,
            errorCode: 401,
            errorText: "Unauthorized: invalid api key"
        )
        XCTAssertEqual(auth.errorCategory, .authConfig)

        let server = controller.onCompleted(
            group: nil,
            errorCode: 500,
            errorText: "Server protocol decode failure"
        )
        XCTAssertEqual(server.errorCategory, .server)

        let unknown = controller.onCompleted(
            group: nil,
            errorCode: nil,
            errorText: "unexpected boom"
        )
        XCTAssertEqual(unknown.errorCategory, .unknown)
    }

    func testMapboxTokenDistribution_matchesCellwatchProperties() throws {
        let bundled = try bundledRuntimeConfigValue("MAPBOX_ACCESS_TOKEN")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        XCTAssertFalse(bundled.isEmpty, "Bundled runtime token is empty")
        let sourceFile = try bundledRuntimeConfigValue("MAPBOX_TOKEN_SOURCE_FILE")
        XCTAssertTrue(sourceFile.hasSuffix("cellwatch.properties") || sourceFile.hasSuffix("cellwatch.local.properties"))
        let sourceKey = try bundledRuntimeConfigValue("MAPBOX_TOKEN_SOURCE_KEY")
        XCTAssertTrue(sourceKey == "MAPBOX_ACCESS_TOKEN" || sourceKey == "MAPBOX_DOWNLOADS_TOKEN")

        let resolved = RuntimeConfigSource.mapboxAccessToken()?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        XCTAssertNotNil(resolved, "RuntimeConfigSource.mapboxAccessToken() returned nil")
        XCTAssertEqual(
            resolved,
            bundled,
            "Resolved runtime Mapbox token does not match bundled runtime properties; token propagation is broken"
        )
    }

    func testMapHomeBuildPath_doesNotRenderTokenMissingState() {
        XCTAssertNoThrow(
            try bundledRuntimeConfigValue("MAPBOX_ACCESS_TOKEN"),
            "Missing bundled runtime token file"
        )
        let controller = HarnessViewController(displayMode: .mapHome, onboardingUiImplementation: .uikit)
        controller.loadViewIfNeeded()

        guard let renderStateLabel = findView(
            in: controller.view,
            identifier: HarnessViewController.mapHomeRenderStateIdentifier
        ) as? UILabel else {
            XCTFail("Missing map-home render state label")
            return
        }

        RunLoop.main.run(until: Date().addingTimeInterval(0.8))
        let state = renderStateLabel.text ?? ""
        XCTAssertNotEqual(
            state,
            "TOKEN_MISSING",
            "Map home rendered TOKEN_MISSING even though token exists in cellwatch.properties"
        )
    }

    private func bundledRuntimeConfigValue(_ key: String) throws -> String {
        guard let url = Bundle.main.url(forResource: "cellwatch.runtime", withExtension: "properties") else {
            XCTFail("Missing bundled resource: cellwatch.runtime.properties")
            return ""
        }
        let contents = try String(contentsOf: url, encoding: .utf8)
        if let value = parseProperty(key, from: contents), !value.isEmpty {
            return value
        }
        XCTFail("\(key) missing in bundled runtime properties")
        return ""
    }

    private func parseProperty(_ key: String, from contents: String) -> String? {
        for rawLine in contents.split(separator: "\n", omittingEmptySubsequences: false) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty || line.hasPrefix("#") { continue }
            let parts = line.split(separator: "=", maxSplits: 1).map(String.init)
            if parts.count != 2 { continue }
            let parsedKey = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            if parsedKey != key { continue }
            return parts[1]
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "\""))
        }
        return nil
    }

    private func attachScreenshot(of view: UIView, named name: String) {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        let renderer = UIGraphicsImageRenderer(bounds: view.bounds, format: format)
        let image = renderer.image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
        saveScreenshotToTemp(image: image, named: name)
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func saveScreenshotToTemp(image: UIImage, named name: String) {
        guard let data = image.pngData() else { return }
        let root = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
            .appendingPathComponent("ios", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            let url = root.appendingPathComponent("\(name).png")
            try data.write(to: url, options: [.atomic])
        } catch {
            // Keep smoke test assertions focused on behavior; screenshot export is best-effort.
        }
    }

    private func findView(in root: UIView, identifier: String) -> UIView? {
        if root.accessibilityIdentifier == identifier {
            return root
        }
        for child in root.subviews {
            if let found = findView(in: child, identifier: identifier) {
                return found
            }
        }
        return nil
    }
}
