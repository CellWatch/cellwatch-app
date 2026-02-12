import XCTest
import sharedKit

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
        let env = ProcessInfo.processInfo.environment
        if let direct = env["MSAK_LOCAL_SERVER_HOST"], !direct.isEmpty {
            return normalizedHost(direct)
        }
        if let fromProperties = loadProperty("MSAK_LOCAL_SERVER_HOST") {
            return normalizedHost(fromProperties)
        }
        return "127.0.0.1:8080"
    }

    private func resolveLocalSecure() -> Bool {
        let env = ProcessInfo.processInfo.environment
        if let parsed = parseBool(env["MSAK_LOCAL_SERVER_SECURE"]) {
            return parsed
        }
        if let parsed = parseBool(loadProperty("MSAK_LOCAL_SERVER_SECURE")) {
            return parsed
        }
        return false
    }

    private func loadProperty(_ key: String) -> String? {
        let candidates = [
            URL(fileURLWithPath: "cellwatch.properties"),
            URL(fileURLWithPath: "../cellwatch.properties"),
            URL(fileURLWithPath: "../../cellwatch.properties")
        ]
        for candidate in candidates {
            guard let contents = try? String(contentsOf: candidate, encoding: .utf8) else {
                continue
            }
            for rawLine in contents.split(separator: "\n", omittingEmptySubsequences: false) {
                let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
                if line.isEmpty || line.hasPrefix("#") {
                    continue
                }
                let parts = line.split(separator: "=", maxSplits: 1).map(String.init)
                if parts.count == 2 && parts[0].trimmingCharacters(in: .whitespacesAndNewlines) == key {
                    return parts[1].trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
                }
            }
        }
        return nil
    }

    private func parseBool(_ raw: String?) -> Bool? {
        guard let value = raw?.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\"")) else {
            return nil
        }
        switch value.lowercased() {
        case "true", "1", "yes", "y": return true
        case "false", "0", "no", "n": return false
        default: return nil
        }
    }

    private func normalizedHost(_ raw: String) -> String {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
        switch value {
        case "10.0.2.2": return "127.0.0.1"
        case "10.0.3.2": return "127.0.0.1"
        default:
            if value.hasPrefix("10.0.2.2:") {
                return "127.0.0.1:" + value.dropFirst("10.0.2.2:".count)
            }
            if value.hasPrefix("10.0.3.2:") {
                return "127.0.0.1:" + value.dropFirst("10.0.3.2:".count)
            }
            return value
        }
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
