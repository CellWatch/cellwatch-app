import XCTest
import sharedKit
@testable import iosTestApp

final class TestingSupabaseHostedTests: XCTestCase {
    private let smokeValidator = SyncSmokeInvariantValidator()

    func testHostedTestingSupabaseProfile_whenEnabled() throws {
        guard isSmokeMarkerPresent() else {
            throw XCTSkip("Run via :shared:verifyIosTestAppHostedTestingSupabaseSmoke to enable this test")
        }

        let testingUrl = try XCTUnwrap(
            RuntimeConfigSource.value("SUPABASE_TESTING_URL"),
            "Expected SUPABASE_TESTING_URL for hosted testing Supabase smoke"
        )
        let testingApiKey = try XCTUnwrap(
            RuntimeConfigSource.value("SUPABASE_TESTING_API_KEY"),
            "Expected SUPABASE_TESTING_API_KEY for hosted testing Supabase smoke"
        )

        let snapshot = try RuntimeSyncMsakProfileBridge().resolveFromModes(
            msakMode: .public_,
            supabaseMode: RuntimeSupabaseMode.testing,
            localSupabaseUrl: RuntimeConfigSource.localSupabaseUrlForIos(),
            localSupabaseApiKey: RuntimeConfigSource.localSupabaseApiKeyPreferServiceRoleJwt(),
            testingSupabaseUrl: testingUrl,
            testingSupabaseApiKey: testingApiKey,
            liveSupabaseUrl: RuntimeConfigSource.value("SUPABASE_URL"),
            liveSupabaseApiKey: RuntimeConfigSource.value("SUPABASE_API_KEY"),
            allowRemoteSupabase: true,
            strictSupabaseConfig: true,
            localMsakHost: nil,
            localMsakSecure: false,
            userAgent: "ios-test-app-testing-supabase-smoke"
        )

        XCTAssertEqual(snapshot.msakEnvironment, MsakLocateEnvironment.prod)
        XCTAssertEqual(snapshot.supabaseMode, RuntimeSupabaseMode.testing)
        XCTAssertEqual(snapshot.syncTarget, SyncTransportTarget.remote)
        XCTAssertEqual(snapshot.supabaseUrl, testingUrl)
        XCTAssertEqual(snapshot.supabaseApiKey, testingApiKey)
        XCTAssertFalse(snapshot.supabaseUrl.contains("127.0.0.1"))
        XCTAssertFalse(snapshot.supabaseUrl.contains("localhost"))

        let syncExpectation = expectation(description: "runHostedTestingSupabaseSync")
        IosLocalSupabaseSyncHarness().runRemote(
            supabaseUrl: snapshot.supabaseUrl,
            supabaseApiKey: snapshot.supabaseApiKey
        ) { result, error in
            XCTAssertNil(error)
            XCTAssertNotNil(result)
            XCTAssertEqual(result?.mapStartMeasurementsAttempted, Int32(1))
            XCTAssertEqual(result?.mapStartMeasurementsUploaded, Int32(1))
            XCTAssertEqual(result?.mapStartSubmissionsAttempted, Int32(1))
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

        waitForExpectations(timeout: 45)
    }

    private func isSmokeMarkerPresent() -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/cellwatch-ios-testing-supabase-smoke-required")
    }
}
