import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        let controller = HarnessViewController()
        window.rootViewController = controller
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}

private final class HarnessViewController: UIViewController {
    private let statusLabel = UILabel()
    private var lastGroupId: String?
    private var driver = SyncHarnessDriver()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        buildUi()
    }

    private func buildUi() {
        let title = UILabel()
        title.text = "iOS Test App Harness"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.translatesAutoresizingMaskIntoConstraints = false

        let localEnvButton = UIButton(type: .system)
        localEnvButton.setTitle("Resolve Local Environment", for: .normal)
        localEnvButton.addTarget(self, action: #selector(resolveLocalEnvironment), for: .touchUpInside)
        localEnvButton.translatesAutoresizingMaskIntoConstraints = false

        let mapStartButton = UIButton(type: .system)
        mapStartButton.setTitle("Run Map-Start Sync Simulation", for: .normal)
        mapStartButton.addTarget(self, action: #selector(runMapStart), for: .touchUpInside)
        mapStartButton.translatesAutoresizingMaskIntoConstraints = false

        let completeButton = UIButton(type: .system)
        completeButton.setTitle("Run Measurement-Complete Simulation", for: .normal)
        completeButton.addTarget(self, action: #selector(runMeasurementComplete), for: .touchUpInside)
        completeButton.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.text = "Ready. Remote target is blocked unless explicitly enabled."
        statusLabel.numberOfLines = 0
        statusLabel.font = UIFont.preferredFont(forTextStyle: .body)
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [title, localEnvButton, mapStartButton, completeButton, statusLabel])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20)
        ])
    }

    @objc private func resolveLocalEnvironment() {
        let provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            properties: ProcessInfo.processInfo.environment,
            allowRemote: false
        )
        do {
            let env = try provider.resolve(.local)
            statusLabel.text = "Local environment resolved:\nurl=\(env.url)\napiKeyPrefix=\(env.apiKey.prefix(12))..."
        } catch {
            statusLabel.text = "Environment resolve failed: \(error.localizedDescription)"
        }
    }

    @objc private func runMapStart() {
        let groupId = UUID().uuidString
        lastGroupId = groupId
        let report = driver.runMapStartSync()
        statusLabel.text =
            "group=\(groupId)\n" +
            "measurements uploaded=\(report.measurements.uploaded), marked=\(report.measurements.markedUploaded)\n" +
            "submissions uploaded=\(report.submissions.uploaded), blocked=\(report.submissions.blockedBeforeUpload)"
    }

    @objc private func runMeasurementComplete() {
        let groupId = lastGroupId ?? UUID().uuidString
        lastGroupId = groupId
        let uploadTime = driver.runMeasurementCompleteSync(groupId: groupId)
        statusLabel.text = "measurement-complete group=\(groupId)\nuploadTime=\(uploadTime)"
    }
}

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

private final class SyncHarnessDriver {
    func runMapStartSync() -> SyncAllReport {
        SyncAllReport(
            measurements: SyncReport(attempted: 3, uploaded: 1, markedUploaded: 1, networkErrors: 1),
            submissions: SyncReport(attempted: 2, uploaded: 1, unexpectedErrors: 1, blockedBeforeUpload: true)
        )
    }

    func runMeasurementCompleteSync(groupId: String) -> String {
        _ = groupId
        return ISO8601DateFormatter().string(from: Date())
    }
}
