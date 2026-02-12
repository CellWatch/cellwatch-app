import XCTest
import sharedKit

private enum SupabaseTarget {
    case local
    case remote
}

private struct SupabaseEnvironment {
    let target: SupabaseTarget
    let url: String
    let apiKey: String
}

private struct CellwatchPropertiesSupabaseEnvironmentProvider {
    let properties: [String: String]
    let allowRemote: Bool

    func resolve(_ target: SupabaseTarget = .local) throws -> SupabaseEnvironment {
        do {
            let resolved = try UploadTriggerParityHarness().resolveSupabaseConfigForRuntime(
                allowRemote: allowRemote,
                localUrl: properties["SUPABASE_LOCAL_URL"] ?? "",
                localApiKey: properties["SUPABASE_LOCAL_API_KEY"] ?? "",
                remoteUrl: properties["SUPABASE_URL"] ?? "",
                remoteApiKey: properties["SUPABASE_API_KEY"] ?? "",
                useRemote: target == .remote
            )
            return SupabaseEnvironment(target: target, url: resolved.url, apiKey: resolved.apiKey)
        } catch {
            throw NSError(
                domain: "iosTestApp",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "\(error)"]
            )
        }
    }
}

final class SyncHarnessParityTests: XCTestCase {
    func testEnvironmentDefaultsToLocal() throws {
        let provider = CellwatchPropertiesSupabaseEnvironmentProvider(properties: [:], allowRemote: false)

        let env = try provider.resolve(.local)

        XCTAssertEqual(env.url, "http://127.0.0.1:54321")
        XCTAssertEqual(env.target, .local)
        XCTAssertTrue(env.apiKey.starts(with: "eyJ"))
    }

    func testEnvironmentBlocksRemoteByDefault() {
        let provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            properties: [
                "SUPABASE_URL": "https://example.supabase.co",
                "SUPABASE_API_KEY": "remote-key"
            ],
            allowRemote: false
        )

        XCTAssertThrowsError(try provider.resolve(.remote))
    }

    func testSharedUploadTriggerParityHarness_returnsExpectedContract() {
        let expectation = expectation(description: "runDefaultScenario")

        UploadTriggerParityHarness().runDefaultScenario { result, error in
            XCTAssertNil(error)
            XCTAssertNotNil(result)
            XCTAssertEqual(result?.measurementsUploaded, Int32(1))
            XCTAssertEqual(result?.measurementsMarkedUploaded, Int32(1))
            XCTAssertEqual(result?.submissionsUploaded, Int32(1))
            XCTAssertEqual(result?.submissionsBlockedBeforeUpload, true)
            XCTAssertEqual(result?.uploadTimeEpochMs?.int64Value, 1_710_000_009_000)
            expectation.fulfill()
        }

        waitForExpectations(timeout: 5)
    }

    func testHostedLocalSupabaseSync_endToEnd() throws {
        let provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            properties: ProcessInfo.processInfo.environment,
            allowRemote: false
        )
        let local = try provider.resolve(.local)
        let expectation = expectation(description: "runHostedLocalSupabaseSync")

        IosLocalSupabaseSyncHarness().run(
            supabaseUrl: local.url,
            supabaseApiKey: local.apiKey
        ) { result, error in
            XCTAssertNil(error)
            XCTAssertNotNil(result)
            XCTAssertEqual(result?.mapStartMeasurementsAttempted, Int32(1))
            XCTAssertEqual(result?.mapStartMeasurementsUploaded, Int32(1))
            XCTAssertEqual(result?.mapStartSubmissionsAttempted, Int32(1))
            XCTAssertEqual(result?.mapStartSubmissionsUploaded, Int32(1))
            XCTAssertEqual(result?.measurementCompleteUploadTimeSet, true)
            XCTAssertEqual(result?.measurementUploadPersisted, true)
            XCTAssertEqual(result?.submissionUploadPersisted, true)
            XCTAssertEqual(result?.remoteMeasurementVerified, true)
            expectation.fulfill()
        }

        waitForExpectations(timeout: 20)
    }
}
