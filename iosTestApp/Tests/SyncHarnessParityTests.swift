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
        switch target {
        case .local:
            let rawUrl = properties["SUPABASE_LOCAL_URL"] ?? "http://127.0.0.1:54321"
            let normalizedUrl = rawUrl
                .replacingOccurrences(of: "\"", with: "")
                .replacingOccurrences(of: "10.0.2.2", with: "127.0.0.1")
            let key = (properties["SUPABASE_LOCAL_API_KEY"] ?? "local-default-key")
                .replacingOccurrences(of: "\"", with: "")
            return SupabaseEnvironment(target: .local, url: normalizedUrl, apiKey: key)
        case .remote:
            if !allowRemote {
                throw NSError(domain: "iosTestApp", code: 1, userInfo: [NSLocalizedDescriptionKey: "remote supabase target is blocked"])
            }
            let url = (properties["SUPABASE_URL"] ?? "").replacingOccurrences(of: "\"", with: "")
            let key = (properties["SUPABASE_API_KEY"] ?? "").replacingOccurrences(of: "\"", with: "")
            guard !url.isEmpty, !key.isEmpty else {
                throw NSError(domain: "iosTestApp", code: 2, userInfo: [NSLocalizedDescriptionKey: "missing remote supabase properties"])
            }
            guard !url.contains("127.0.0.1"), !url.contains("localhost") else {
                throw NSError(domain: "iosTestApp", code: 3, userInfo: [NSLocalizedDescriptionKey: "remote target cannot point to localhost"])
            }
            return SupabaseEnvironment(target: .remote, url: url, apiKey: key)
        }
    }
}

final class SyncHarnessParityTests: XCTestCase {
    func testEnvironmentDefaultsToLocal() throws {
        let provider = CellwatchPropertiesSupabaseEnvironmentProvider(properties: [:], allowRemote: false)

        let env = try provider.resolve(.local)

        XCTAssertEqual(env.url, "http://127.0.0.1:54321")
        XCTAssertEqual(env.target, .local)
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
}
