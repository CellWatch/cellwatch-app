import XCTest
import UIKit
@testable import iosTestApp

final class Phase3SequenceButtonHostedTests: XCTestCase {
    func testHostedPhase3SequenceButtonTap_whenEnabled() throws {
        guard isSmokeMarkerPresent() else {
            throw XCTSkip("Run via :shared:verifyIosTestAppHostedPhase3ButtonSmoke to enable iOS hosted Phase3 button smoke test")
        }

        let controller = HarnessViewController()
        controller.loadViewIfNeeded()

        let runButton = try requireButton(
            in: controller.view,
            title: "Run Phase3 Sequence (Shared Orchestrator)",
        )
        let statusLabel = try requireStatusLabel(in: controller.view)
        captureScreenshot(of: controller.view, named: "01-ready")

        let done = expectation(description: "phase3 status rendered")
        var finalStatus = ""
        pollStatus(label: statusLabel, timeout: 120) { text in
            finalStatus = text
            if text.contains("smokeEnvelope scenario=phase3-sequence-sync") ||
                text.contains("smokeEnvelope scenario=phase3-preflight") {
                done.fulfill()
                return true
            }
            return false
        }

        runButton.sendActions(for: .touchUpInside)
        wait(for: [done], timeout: 125)
        captureScreenshot(of: controller.view, named: "02-after-phase3")

        XCTAssertTrue(
            finalStatus.contains("smokeEnvelope scenario=phase3-sequence-sync") ||
                finalStatus.contains("smokeEnvelope scenario=phase3-preflight"),
            "Expected phase3 sequence or preflight envelope after button tap, got: \(finalStatus)",
        )
        XCTAssertTrue(
            finalStatus.contains("status=SUCCESS"),
            "Expected success status for phase3 button smoke, got: \(finalStatus)"
        )
        XCTAssertFalse(
            finalStatus.contains("status=FAILURE"),
            "Phase3 button smoke reported failure envelope: \(finalStatus)"
        )
        XCTAssertFalse(
            finalStatus.contains("Ready. Remote target is blocked unless explicitly enabled."),
            "Status should not stay at initial ready message after phase3 tap",
        )
    }

    private func isSmokeMarkerPresent() -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-phase3-button-smoke-required")
    }

    private func captureScreenshot(of view: UIView, named name: String) {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        let renderer = UIGraphicsImageRenderer(bounds: view.bounds, format: format)
        let image = renderer.image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
        guard let data = image.pngData() else { return }
        let root = URL(fileURLWithPath: "/tmp", isDirectory: true)
            .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
            .appendingPathComponent("ios", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            let url = root.appendingPathComponent("\(name).png")
            try data.write(to: url, options: [.atomic])
        } catch {
            // Best-effort artifact only.
        }
    }

    private func requireButton(in view: UIView, title: String) throws -> UIButton {
        guard let button = findButton(in: view, title: title) else {
            XCTFail("Unable to find button with title: \(title)")
            throw NSError(domain: "Phase3SequenceButtonHostedTests", code: 1)
        }
        return button
    }

    private func findButton(in view: UIView, title: String) -> UIButton? {
        if let button = view as? UIButton, button.currentTitle == title {
            return button
        }
        for subview in view.subviews {
            if let found = findButton(in: subview, title: title) {
                return found
            }
        }
        return nil
    }

    private func requireStatusLabel(in view: UIView) throws -> UILabel {
        guard let label = findStatusLabel(in: view) else {
            XCTFail("Unable to find harness status label")
            throw NSError(domain: "Phase3SequenceButtonHostedTests", code: 2)
        }
        return label
    }

    private func findStatusLabel(in view: UIView) -> UILabel? {
        if let label = view as? UILabel,
           label.accessibilityIdentifier == HarnessViewController.statusLabelIdentifier {
            return label
        }
        for subview in view.subviews {
            if let found = findStatusLabel(in: subview) {
                return found
            }
        }
        return nil
    }

    private func pollStatus(
        label: UILabel,
        timeout: TimeInterval,
        onText: @escaping (String) -> Bool,
    ) {
        let deadline = Date().addingTimeInterval(timeout)
        func tick() {
            let text = label.text ?? ""
            if onText(text) {
                return
            }
            if Date() >= deadline {
                return
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: tick)
        }
        DispatchQueue.main.async(execute: tick)
    }
}
