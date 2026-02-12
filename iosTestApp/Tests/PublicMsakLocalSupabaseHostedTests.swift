import XCTest
import sharedKit

final class PublicMsakLocalSupabaseHostedTests: XCTestCase {
    private let smokeValidator = SyncSmokeInvariantValidator()

    func testHostedPublicMsak_withLocalSupabaseProfile_whenEnabled() throws {
        guard isSmokeMarkerPresent() else {
            throw XCTSkip("Run via :shared:verifyIosTestAppHostedPublicMsakLocalSupabaseSmoke to enable this test")
        }

        let snapshot = try RuntimeSyncMsakProfileBridge().resolvePublicMsakLocalSupabase(
            localSupabaseUrl: resolveValue("SUPABASE_LOCAL_URL"),
            localSupabaseApiKey: resolveValue("SUPABASE_LOCAL_API_KEY"),
            userAgent: "ios-test-app-phase3-public-msak-local-supabase"
        )

        XCTAssertEqual(snapshot.msakEnvironment, MsakLocateEnvironment.prod)
        XCTAssertTrue(snapshot.supabaseUrl.contains("127.0.0.1") || snapshot.supabaseUrl.contains("localhost"))

        let syncExpectation = expectation(description: "runHostedLocalSupabaseSync")
        IosLocalSupabaseSyncHarness().run(
            supabaseUrl: snapshot.supabaseUrl,
            supabaseApiKey: snapshot.supabaseApiKey
        ) { result, error in
            XCTAssertNil(error)
            XCTAssertNotNil(result)
            XCTAssertEqual(result?.mapStartMeasurementsUploaded, Int32(1))
            XCTAssertEqual(result?.mapStartSubmissionsUploaded, Int32(1))
            let invariantError = self.smokeValidator.validateSuccess(
                measurementUploadPersisted: result?.measurementUploadPersisted ?? false,
                submissionUploadPersisted: result?.submissionUploadPersisted ?? false,
                remoteMeasurementVerified: result?.remoteMeasurementVerified ?? false,
                measurementCompleteUploadTimeSet: result?.measurementCompleteUploadTimeSet ?? false
            )
            XCTAssertNil(invariantError)
            syncExpectation.fulfill()
        }
        waitForExpectations(timeout: 30)

        let sequenceExpectation = expectation(description: "runPublicMsakPhase3Sequence")
        var harness: MeasurementSequenceHarness? = MeasurementSequenceHarness(
            config: MsakLocateConfig(
                environment: snapshot.msakEnvironment,
                userAgent: snapshot.msakUserAgent,
                localServerHost: snapshot.msakLocalServerHost,
                localServerSecure: snapshot.msakLocalServerSecure
            )
        )
        harness?.runDefaultScenario { result, error in
            defer {
                harness?.close()
                harness = nil
            }
            XCTAssertNil(error)
            XCTAssertNotNil(result)
            XCTAssertEqual(result?.persistedMeasurements, Int32(3))
            XCTAssertTrue((result?.throughputMachine ?? "").isEmpty == false)
            XCTAssertTrue((result?.latencyMachine ?? "").isEmpty == false)
            XCTAssertTrue((result?.capabilitySummary ?? "").hasPrefix("capabilities("))
            sequenceExpectation.fulfill()
        }
        waitForExpectations(timeout: 120)
    }

    private func isSmokeMarkerPresent() -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-public-msak-local-supabase-smoke-required")
    }

    private func resolveValue(_ key: String) -> String? {
        let env = ProcessInfo.processInfo.environment
        if let value = env[key], !value.isEmpty {
            return value
        }
        return loadProperty(key)
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
}
