import XCTest
import sharedKit
@testable import iosTestApp

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
    let allowLocalFallbackDefaults: Bool

    func resolve(_ target: SupabaseTarget = .local) throws -> SupabaseEnvironment {
        do {
            let resolved = try UploadTriggerParityHarness().resolveSupabaseConfigForRuntime(
                allowRemote: allowRemote,
                localUrl: properties["SUPABASE_LOCAL_URL"] ?? "",
                localApiKey: properties["SUPABASE_LOCAL_API_KEY"] ?? "",
                remoteUrl: properties["SUPABASE_URL"] ?? "",
                remoteApiKey: properties["SUPABASE_API_KEY"] ?? "",
                useRemote: target == .remote,
                allowLocalFallbackDefaults: allowLocalFallbackDefaults
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
    private let smokeEnvelopeBuilder = SyncSmokeEnvelopeBuilder()
    private let smokeValidator = SyncSmokeInvariantValidator()

    func testEnvironmentDefaultsToLocal() throws {
        let provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            properties: [:],
            allowRemote: false,
            allowLocalFallbackDefaults: true
        )

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
            allowRemote: false,
            allowLocalFallbackDefaults: true
        )

        XCTAssertThrowsError(try provider.resolve(.remote))
    }

    func testEnvironmentStrictLocalConfig_whenMissingValues_throws() {
        let provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            properties: [:],
            allowRemote: false,
            allowLocalFallbackDefaults: false
        )

        XCTAssertThrowsError(try provider.resolve(.local))
    }

    func testSharedUploadTriggerParityHarness_returnsExpectedContract() {
        let expectation = expectation(description: "runDefaultScenario")
        let artifactFactory = UploadTriggerParityArtifactFactory()

        UploadTriggerParityHarness().runDefaultScenario { result, error in
            XCTAssertNil(error)
            XCTAssertNotNil(result)
            XCTAssertEqual(result?.measurementsUploaded, Int32(1))
            XCTAssertEqual(result?.measurementsMarkedUploaded, Int32(1))
            XCTAssertEqual(result?.submissionsUploaded, Int32(1))
            XCTAssertEqual(result?.submissionsBlockedBeforeUpload, true)
            XCTAssertEqual(result?.uploadTimeEpochMs?.int64Value, 1_710_000_009_000)
            if let result {
                let artifactJson = artifactFactory.buildJson(platform: "ios", result: result)
                let parsedArtifact = artifactFactory.parseJson(jsonText: artifactJson)
                print("uploadTriggerParityArtifact=\(artifactJson)")
                XCTAssertEqual(parsedArtifact.schemaVersion, 1)
                XCTAssertEqual(parsedArtifact.platform, "ios")
            } else {
                XCTFail("Expected non-nil parity result for artifact emission")
            }
            expectation.fulfill()
        }

        waitForExpectations(timeout: 5)
    }

    func testHostedLocalSupabaseSync_endToEnd() throws {
        guard let localUrl = RuntimeConfigSource.localSupabaseUrlForIos(),
              let localApiKey = RuntimeConfigSource.localSupabaseApiKeyPreferServiceRoleJwt() else {
            throw XCTSkip("Local supabase runtime config is unavailable for hosted sync test")
        }
        let expectation = expectation(description: "runHostedLocalSupabaseSync")

        IosLocalSupabaseSyncHarness().run(
            supabaseUrl: localUrl,
            supabaseApiKey: localApiKey
        ) { result, error in
            XCTAssertNil(error)
            XCTAssertNotNil(result)
            XCTAssertEqual(result?.mapStartMeasurementsAttempted, Int32(1))
            XCTAssertEqual(result?.mapStartMeasurementsUploaded, Int32(1))
            XCTAssertEqual(result?.mapStartSubmissionsAttempted, Int32(1))
            XCTAssertEqual(result?.mapStartSubmissionsUploaded, Int32(1))
            XCTAssertEqual(result?.capabilitySupportPersisted, true)
            XCTAssertEqual(result?.capabilityNotesPersisted, true)
            let invariantError = self.smokeValidator.validateSuccess(
                measurementUploadPersisted: result?.measurementUploadPersisted ?? false,
                submissionUploadPersisted: result?.submissionUploadPersisted ?? false,
                remoteMeasurementVerified: result?.remoteMeasurementVerified ?? false,
                measurementCompleteUploadTimeSet: result?.measurementCompleteUploadTimeSet ?? false
            )
            XCTAssertNil(invariantError)
            expectation.fulfill()
        }

        waitForExpectations(timeout: 20)
    }

    func testHostedFailureSurface_forInvalidSupabaseCredentials_whenEnabled() throws {
        guard isFailureSmokeMarkerPresent() else {
            throw XCTSkip("Run via :shared:verifyIosTestAppHostedFailureStatusSmoke to enable this test")
        }

        guard let localUrl = RuntimeConfigSource.localSupabaseUrlForIos() else {
            throw XCTSkip("Local supabase URL is unavailable for failure smoke test")
        }
        let expectation = expectation(description: "runHostedLocalSupabaseSyncFailure")

        IosLocalSupabaseSyncHarness().run(
            supabaseUrl: localUrl,
            supabaseApiKey: "invalid-local-key"
        ) { result, error in
            XCTAssertNil(result)
            XCTAssertNotNil(error)
            let invariantError = self.smokeValidator.validateFailure(
                errorMessage: error?.localizedDescription
            )
            XCTAssertNil(invariantError)
            expectation.fulfill()
        }

        waitForExpectations(timeout: 20)
    }

    func testSmokeEnvelopeTextShape_successAndFailure_containsCanonicalFields() {
        let success = SyncSmokeResultFormatter().format(
            envelope: smokeEnvelopeBuilder.measurementComplete(
                uploadTimeSet: true,
                errorMessage: nil
            )
        )
        XCTAssertTrue(success.contains("smokeEnvelope scenario="))
        XCTAssertTrue(success.contains("status=SUCCESS"))
        XCTAssertTrue(success.contains("invariants="))
        XCTAssertTrue(success.contains("message="))

        let failure = SyncSmokeResultFormatter().format(
            envelope: smokeEnvelopeBuilder.failure(
                scenario: "measurement-complete-sync",
                errorMessage: "synthetic failure"
            )
        )
        XCTAssertTrue(failure.contains("smokeEnvelope scenario=measurement-complete-sync"))
        XCTAssertTrue(failure.contains("status=FAILURE"))
        XCTAssertTrue(failure.contains("invariants="))
        XCTAssertTrue(failure.contains("message=synthetic failure"))
    }

    private func isFailureSmokeMarkerPresent() -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-failure-status-smoke-required")
    }
}
