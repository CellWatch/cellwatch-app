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

        let expectation = expectation(description: "status label updated")
        button.sendActions(for: .touchUpInside)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            let rendered = statusLabel.text ?? ""
            XCTAssertFalse(rendered.isEmpty)
            XCTAssertTrue(rendered.contains("smokeEnvelope scenario=map-start-sync"))
            expectation.fulfill()
        }

        waitForExpectations(timeout: 2.0)
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
