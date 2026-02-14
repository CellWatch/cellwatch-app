import XCTest
import UIKit
@testable import iosTestApp

final class OnboardingRuntimeHostedTests: XCTestCase {
    func testHostedRuntimeOnboardingModeCycle_whenEnabled() throws {
        guard isSmokeMarkerPresent() else {
            throw XCTSkip("Run via :shared:verifyIosTestAppHostedOnboardingRuntimeSmoke to enable iOS hosted onboarding runtime smoke test")
        }
        HarnessViewController.clearPersistedOnboardingForTests()

        let controller = HarnessViewController(
            displayMode: .onboardingFlow,
            onboardingUiImplementation: .uikit
        )
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
        let statusLabel: UILabel = try requireView(
            in: controller.view,
            identifier: HarnessViewController.statusLabelIdentifier
        )

        captureScreenshot(of: controller.view, named: "01-ready")
        nameField.text = OnboardingUiScenario.name
        nameField.sendActions(for: .editingChanged)
        captureScreenshot(of: controller.view, named: "02-after-name")
        phoneField.text = OnboardingUiScenario.phone
        phoneField.sendActions(for: .editingChanged)
        captureScreenshot(of: controller.view, named: "03-after-phone")
        emailField.text = OnboardingUiScenario.email
        emailField.sendActions(for: .editingChanged)
        captureScreenshot(of: controller.view, named: "04-after-email")
        ackSwitch.setOn(true, animated: false)
        ackSwitch.sendActions(for: .valueChanged)
        captureScreenshot(of: controller.view, named: "05-after-ack")
        submitButton.sendActions(for: .touchUpInside)
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        captureScreenshot(of: controller.view, named: "06-after-submit")

        let rendered = statusLabel.text ?? ""
        XCTAssertTrue(
            rendered.contains("Onboarding submit=SUCCESS"),
            "Expected onboarding success output, got: \(rendered)"
        )
        XCTAssertTrue(
            rendered.contains("phone=404-555-1212") && rendered.contains("onboardingComplete=true"),
            "Expected normalized onboarding details in output, got: \(rendered)"
        )

        let reopened = HarnessViewController(
            displayMode: .onboardingFlow,
            onboardingUiImplementation: .uikit
        )
        reopened.loadViewIfNeeded()
        let reopenedNameField: UITextField = try requireView(
            in: reopened.view,
            identifier: HarnessViewController.onboardingNameFieldIdentifier
        )
        let reopenedPhoneField: UITextField = try requireView(
            in: reopened.view,
            identifier: HarnessViewController.onboardingPhoneFieldIdentifier
        )
        let reopenedEmailField: UITextField = try requireView(
            in: reopened.view,
            identifier: HarnessViewController.onboardingEmailFieldIdentifier
        )
        let reopenedAckSwitch: UISwitch = try requireView(
            in: reopened.view,
            identifier: HarnessViewController.onboardingAckSwitchIdentifier
        )
        let reopenedSubmitButton: UIButton = try requireView(
            in: reopened.view,
            identifier: HarnessViewController.onboardingSubmitButtonIdentifier
        )
        let reopenedStatusLabel: UILabel = try requireView(
            in: reopened.view,
            identifier: HarnessViewController.statusLabelIdentifier
        )

        captureScreenshot(of: reopened.view, named: "07-reopen-prefilled")
        XCTAssertEqual(OnboardingUiScenario.name, reopenedNameField.text ?? "")
        XCTAssertEqual("404-555-1212", reopenedPhoneField.text ?? "")
        XCTAssertEqual(OnboardingUiScenario.email, reopenedEmailField.text ?? "")
        XCTAssertTrue(reopenedAckSwitch.isOn)

        reopenedPhoneField.text = OnboardingUiScenario.updatedPhone
        reopenedPhoneField.sendActions(for: .editingChanged)
        reopenedEmailField.text = OnboardingUiScenario.updatedEmail
        reopenedEmailField.sendActions(for: .editingChanged)
        captureScreenshot(of: reopened.view, named: "08-after-edit")
        reopenedSubmitButton.sendActions(for: .touchUpInside)
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        captureScreenshot(of: reopened.view, named: "09-after-edit-submit")

        let editRendered = reopenedStatusLabel.text ?? ""
        XCTAssertTrue(
            editRendered.contains("Onboarding submit=SUCCESS"),
            "Expected onboarding edit success output, got: \(editRendered)"
        )
        XCTAssertTrue(
            editRendered.contains("phone=404-111-2222") && editRendered.contains("email=\(OnboardingUiScenario.updatedEmail)"),
            "Expected updated onboarding details in output, got: \(editRendered)"
        )

        let verifyUpdated = HarnessViewController(
            displayMode: .onboardingFlow,
            onboardingUiImplementation: .uikit
        )
        verifyUpdated.loadViewIfNeeded()
        let verifyPhoneField: UITextField = try requireView(
            in: verifyUpdated.view,
            identifier: HarnessViewController.onboardingPhoneFieldIdentifier
        )
        let verifyEmailField: UITextField = try requireView(
            in: verifyUpdated.view,
            identifier: HarnessViewController.onboardingEmailFieldIdentifier
        )
        captureScreenshot(of: verifyUpdated.view, named: "10-reopen-after-edit")
        XCTAssertEqual("404-111-2222", verifyPhoneField.text ?? "")
        XCTAssertEqual(OnboardingUiScenario.updatedEmail, verifyEmailField.text ?? "")
    }

    private func isSmokeMarkerPresent() -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-onboarding-runtime-smoke-required")
    }

    private func captureScreenshot(of view: UIView, named name: String) {
        guard ProcessInfo.processInfo.environment["CELLWATCH_HOSTED_SCREENSHOTS"] == "1" else {
            return
        }
        let root = URL(fileURLWithPath: "/tmp", isDirectory: true)
            .appendingPathComponent("cellwatch-ui-flow", isDirectory: true)
            .appendingPathComponent("ios", isDirectory: true)
            .appendingPathComponent("onboarding-profile-entry-hosted", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            let url = root.appendingPathComponent("\(name).png")
            let format = UIGraphicsImageRendererFormat()
            format.scale = UIScreen.main.scale
            let renderer = UIGraphicsImageRenderer(bounds: view.bounds, format: format)
            let fullImage = renderer.image { _ in
                view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
            }
            let cropRect = screenshotCropRect(in: view)
            let image = croppedImage(from: fullImage, cropRect: cropRect) ?? fullImage
            guard let data = image.pngData() else { return }
            try data.write(to: url, options: [.atomic])
        } catch {
            // Best-effort artifact only.
        }
    }

    private func screenshotCropRect(in rootView: UIView) -> CGRect {
        guard let container = findView(
            in: rootView,
            identifier: HarnessViewController.onboardingRootContainerIdentifier
        ) else {
            return rootView.bounds
        }
        let localRect = container.convert(container.bounds, to: rootView)
        let paddedRect = localRect.insetBy(dx: -8, dy: -8)
        let bounded = paddedRect.intersection(rootView.bounds)
        return bounded.isNull ? rootView.bounds : bounded
    }

    private func croppedImage(from image: UIImage, cropRect: CGRect) -> UIImage? {
        guard cropRect.width > 0, cropRect.height > 0 else { return nil }
        guard let cgImage = image.cgImage else { return nil }
        let scaled = CGRect(
            x: cropRect.origin.x * image.scale,
            y: cropRect.origin.y * image.scale,
            width: cropRect.width * image.scale,
            height: cropRect.height * image.scale
        ).integral
        guard let cropped = cgImage.cropping(to: scaled) else { return nil }
        return UIImage(cgImage: cropped, scale: image.scale, orientation: image.imageOrientation)
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
