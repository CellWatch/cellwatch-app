import XCTest
import sharedKit
@testable import iosTestApp

final class LocalMsakPhase3HostedTests: XCTestCase {
    func testHostedLocalMsakPhase3Sequence_whenEnabled() throws {
        guard isSmokeMarkerPresent() else {
            throw XCTSkip("Run via :shared:verifyIosTestAppHostedLocalMsakSmoke to enable iOS local MSAK hosted smoke test")
        }

        let localHost = resolveLocalHost()
        let localSecure = resolveLocalSecure()
        let allowTransientSkip =
            ProcessInfo.processInfo.environment["CELLWATCH_ALLOW_LOCAL_MSAK_TRANSIENT_SKIP"] == "1" ||
            FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-local-msak-transient-skip-allowed")
        guard isServerReachable(localHost) else {
            if allowTransientSkip {
                throw XCTSkip("Local MSAK server unreachable at \(localHost), bypassed via CELLWATCH_ALLOW_LOCAL_MSAK_TRANSIENT_SKIP=1")
            }
            XCTFail("Local MSAK server not reachable at \(localHost)")
            return
        }

        let expectation = expectation(description: "runPhase3SequenceLocal")
        var skippedReason: String?
        var failureReason: String?
        var harness: MeasurementSequenceHarness? = MeasurementSequenceHarness(
            config: MsakLocateConfig(
                environment: MsakLocateEnvironment.local,
                userAgent: "ios-test-app-phase3-local-smoke",
                localServerHost: localHost,
                localServerSecure: localSecure
            ),
            capabilityProvider: IosCapabilityProviderFactoryKt.createIosPlatformCapabilityProvider()
        )

        harness?.runDefaultScenario { result, error in
            defer {
                harness?.close()
                harness = nil
            }

            if let error {
                let errorText = String(describing: error)
                if allowTransientSkip && self.isTransientLocalMsakFailure(errorText) {
                    skippedReason = "Local MSAK transient failure bypassed via CELLWATCH_ALLOW_LOCAL_MSAK_TRANSIENT_SKIP=1: \(error)"
                } else {
                    failureReason = "Local MSAK smoke failed: \(error)"
                }
                expectation.fulfill()
                return
            }

            XCTAssertNotNil(result)
            XCTAssertEqual(result?.persistedMeasurements, Int32(3))
            XCTAssertEqual(result?.persistedSubmissions, Int32(1))
            XCTAssertEqual(result?.persistedMeasurementsWithCapabilitySupport, result?.persistedMeasurements)
            XCTAssertNotNil(result?.persistedMeasurementsWithCapabilityNotes)
            XCTAssertTrue((result?.capabilityPersistenceSummary ?? "").hasPrefix("capabilityPersistence("))
            XCTAssertTrue((result?.throughputMachine ?? "").isEmpty == false)
            XCTAssertTrue((result?.latencyMachine ?? "").isEmpty == false)
            XCTAssertTrue((result?.capabilitySummary ?? "").hasPrefix("capabilities("))
            if let result {
                let artifactFactory = Phase3SequenceParityArtifactFactory()
                let artifactJson = artifactFactory.buildJson(platform: "ios", result: result)
                let parsedArtifact = artifactFactory.parseJson(jsonText: artifactJson)
                print("phase3SequenceParityArtifact=\(artifactJson)")
                XCTAssertEqual(parsedArtifact.schemaVersion, 1)
                XCTAssertEqual(parsedArtifact.platform, "ios")
            } else {
                XCTFail("Expected non-nil phase3 result for artifact emission")
            }
            expectation.fulfill()
        }

        waitForExpectations(timeout: 90)
        if let skippedReason {
            throw XCTSkip(skippedReason)
        }
        if let failureReason {
            XCTFail(failureReason)
        }
    }

    private func isSmokeMarkerPresent() -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-local-msak-smoke-required")
    }

    private func resolveLocalHost() -> String {
        RuntimeConfigSource.localMsakHostForIos(msakModeRaw: "LOCAL") ?? "127.0.0.1:8080"
    }

    private func resolveLocalSecure() -> Bool {
        RuntimeConfigSource.bool("MSAK_LOCAL_SERVER_SECURE")
    }

    private func isServerReachable(_ host: String) -> Bool {
        guard let url = URL(string: "http://\(host)/") else {
            return false
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = 2

        let semaphore = DispatchSemaphore(value: 0)
        var reachable = false

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let error = error as NSError? {
                reachable = error.code != NSURLErrorCannotConnectToHost && error.code != NSURLErrorTimedOut
            } else {
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                reachable = status > 0
            }
            semaphore.signal()
        }.resume()

        _ = semaphore.wait(timeout: .now() + 3)
        return reachable
    }

    private func isTransientLocalMsakFailure(_ message: String) -> Bool {
        let text = message.lowercased()
        return text.contains("no latency result") ||
            text.contains("authorizefailure") ||
            text.contains("timed out") ||
            text.contains("connection refused") ||
            text.contains("socket is not connected") ||
            text.contains("cannot connect")
    }
}
