import XCTest

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

private struct SyncReport {
    let attempted: Int
    let uploaded: Int
    let markedUploaded: Int
    let networkErrors: Int
    let unexpectedErrors: Int
    let blockedBeforeUpload: Bool

    init(
        attempted: Int = 0,
        uploaded: Int = 0,
        markedUploaded: Int = 0,
        networkErrors: Int = 0,
        unexpectedErrors: Int = 0,
        blockedBeforeUpload: Bool = false
    ) {
        self.attempted = attempted
        self.uploaded = uploaded
        self.markedUploaded = markedUploaded
        self.networkErrors = networkErrors
        self.unexpectedErrors = unexpectedErrors
        self.blockedBeforeUpload = blockedBeforeUpload
    }
}

private struct SyncAllReport {
    let measurements: SyncReport
    let submissions: SyncReport
}

private final class SyncDriver {
    struct State {
        var lastReport: SyncAllReport?
        var lastError: String?
    }

    private(set) var state = State(lastReport: nil, lastError: nil)
    private let syncAll: () throws -> SyncAllReport

    init(syncAll: @escaping () throws -> SyncAllReport) {
        self.syncAll = syncAll
    }

    @discardableResult
    func runMapStartSync() -> SyncAllReport? {
        do {
            let report = try syncAll()
            state.lastReport = report
            state.lastError = nil
            return report
        } catch {
            state.lastError = error.localizedDescription
            return nil
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

    func testDriverCapturesPartialAndBlockedSignals() {
        let driver = SyncDriver {
            SyncAllReport(
                measurements: SyncReport(attempted: 3, uploaded: 1, markedUploaded: 1, networkErrors: 1),
                submissions: SyncReport(attempted: 2, uploaded: 1, unexpectedErrors: 1, blockedBeforeUpload: true)
            )
        }

        let report = driver.runMapStartSync()

        XCTAssertNotNil(report)
        XCTAssertEqual(report?.measurements.markedUploaded, 1)
        XCTAssertEqual(report?.submissions.blockedBeforeUpload, true)
        XCTAssertNil(driver.state.lastError)
    }

    func testDriverCapturesFailureMessage() {
        let driver = SyncDriver {
            throw NSError(domain: "iosTestApp", code: 44, userInfo: [NSLocalizedDescriptionKey: "network down"])
        }

        let report = driver.runMapStartSync()

        XCTAssertNil(report)
        XCTAssertEqual(driver.state.lastError, "network down")
    }
}
