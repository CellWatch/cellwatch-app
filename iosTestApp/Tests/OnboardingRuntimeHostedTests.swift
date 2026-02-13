import XCTest
import UIKit
@testable import iosTestApp

final class OnboardingRuntimeHostedTests: XCTestCase {
    func testHostedRuntimeOnboardingModeCycle_whenEnabled() throws {
        guard isSmokeMarkerPresent() else {
            throw XCTSkip("Run via :shared:verifyIosTestAppHostedOnboardingRuntimeSmoke to enable iOS hosted onboarding runtime smoke test")
        }

        let controller = HarnessViewController()
        controller.loadViewIfNeeded()

        let nameField: UITextField = try requireView(
            in: controller.view,
            identifier: HarnessViewController.onboardingNameFieldIdentifier
        )
        let phoneField: UITextField = try requireView(
            in: controller.view,
            identifier: HarnessViewController.onboardingPhoneFieldIdentifier
        )
        let emailField: UITextField = try requireView(
            in: controller.view,
            identifier: HarnessViewController.onboardingEmailFieldIdentifier
        )
        let ackSwitch: UISwitch = try requireView(
            in: controller.view,
            identifier: HarnessViewController.onboardingAckSwitchIdentifier
        )
        let submitButton: UIButton = try requireView(
            in: controller.view,
            identifier: HarnessViewController.onboardingSubmitButtonIdentifier
        )
        let outputTextView: UITextView = try requireView(
            in: controller.view,
            identifier: HarnessViewController.outputTextViewIdentifier
        )

        captureScreenshot(of: controller.view, named: "01-ready")
        nameField.text = "Jane Doe"
        nameField.sendActions(for: .editingChanged)
        captureScreenshot(of: controller.view, named: "02-after-name")
        phoneField.text = "4045551212"
        phoneField.sendActions(for: .editingChanged)
        captureScreenshot(of: controller.view, named: "03-after-phone")
        emailField.text = "jane@example.com"
        emailField.sendActions(for: .editingChanged)
        captureScreenshot(of: controller.view, named: "04-after-email")
        ackSwitch.setOn(true, animated: false)
        ackSwitch.sendActions(for: .valueChanged)
        captureScreenshot(of: controller.view, named: "05-after-ack")
        submitButton.sendActions(for: .touchUpInside)
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        captureScreenshot(of: controller.view, named: "06-after-submit")

        let rendered = outputTextView.text ?? ""
        XCTAssertTrue(
            rendered.contains("Onboarding submit=SUCCESS"),
            "Expected onboarding success output, got: \(rendered)"
        )
        XCTAssertTrue(
            rendered.contains("phone=404-555-1212") && rendered.contains("onboardingComplete=true"),
            "Expected normalized onboarding details in output, got: \(rendered)"
        )
    }

    private func isSmokeMarkerPresent() -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-onboarding-runtime-smoke-required")
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
            .appendingPathComponent("onboarding-profile-entry", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            let url = root.appendingPathComponent("\(name).png")
            try data.write(to: url, options: [.atomic])
        } catch {
            // Best-effort artifact only.
        }
    }

    private func requireView<T: UIView>(in root: UIView, identifier: String) throws -> T {
        guard let view = findView(in: root, identifier: identifier) as? T else {
            XCTFail("Unable to find view with identifier: \(identifier)")
            throw NSError(domain: "OnboardingRuntimeHostedTests", code: 1)
        }
        return view
    }

    private func findView(in view: UIView, identifier: String) -> UIView? {
        if view.accessibilityIdentifier == identifier {
            return view
        }
        for subview in view.subviews {
            if let found = findView(in: subview, identifier: identifier) {
                return found
            }
        }
        return nil
    }
}
