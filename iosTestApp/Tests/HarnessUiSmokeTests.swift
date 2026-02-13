import XCTest
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
