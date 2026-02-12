import UIKit
import sharedKit

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
    private lazy var runtimeSnapshot: RuntimeSyncMsakProfileSnapshot = {
        RuntimeSelection.resolvePublicMsakLocalSupabaseSnapshot()
    }()

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
        mapStartButton.setTitle("Run Map-Start Shared Slice", for: .normal)
        mapStartButton.addTarget(self, action: #selector(runMapStart), for: .touchUpInside)
        mapStartButton.translatesAutoresizingMaskIntoConstraints = false

        let completeButton = UIButton(type: .system)
        completeButton.setTitle("Run Measurement-Complete Shared Slice", for: .normal)
        completeButton.addTarget(self, action: #selector(runMeasurementComplete), for: .touchUpInside)
        completeButton.translatesAutoresizingMaskIntoConstraints = false

        let selectServersButton = UIButton(type: .system)
        selectServersButton.setTitle("Select MSAK Servers (Shared Selector)", for: .normal)
        selectServersButton.addTarget(self, action: #selector(runServerSelection), for: .touchUpInside)
        selectServersButton.translatesAutoresizingMaskIntoConstraints = false

        let runPhase3SequenceButton = UIButton(type: .system)
        runPhase3SequenceButton.setTitle("Run Phase3 Sequence (Shared Orchestrator)", for: .normal)
        runPhase3SequenceButton.addTarget(self, action: #selector(runPhase3Sequence), for: .touchUpInside)
        runPhase3SequenceButton.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.text = "Ready. Remote target is blocked unless explicitly enabled."
        statusLabel.numberOfLines = 0
        statusLabel.font = UIFont.preferredFont(forTextStyle: .body)
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [title, localEnvButton, mapStartButton, completeButton, selectServersButton, runPhase3SequenceButton, statusLabel])
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
        let env = runtimeSnapshot
        statusLabel.text = "Local environment resolved:\nurl=\(env.supabaseUrl)\napiKeyPrefix=\(env.supabaseApiKey.prefix(12))..."
    }

    @objc private func runMapStart() {
        let groupId = UUID().uuidString
        lastGroupId = groupId
        UploadTriggerParityHarness().runDefaultScenario { result, error in
            if let error = error {
                self.statusLabel.text = "map-start failed: \(error.localizedDescription)"
                return
            }
            guard let value = result else {
                self.statusLabel.text = "map-start failed: no result"
                return
            }
            self.statusLabel.text =
                "group=\(groupId)\n" +
                "measurements uploaded=\(value.measurementsUploaded), marked=\(value.measurementsMarkedUploaded)\n" +
                "submissions uploaded=\(value.submissionsUploaded), blocked=\(value.submissionsBlockedBeforeUpload)"
        }
    }

    @objc private func runMeasurementComplete() {
        let groupId = lastGroupId ?? UUID().uuidString
        lastGroupId = groupId
        UploadTriggerParityHarness().runDefaultScenario { result, error in
            if let error = error {
                self.statusLabel.text = "measurement-complete failed: \(error.localizedDescription)"
                return
            }
            let uploadMs = result?.uploadTimeEpochMs?.int64Value ?? -1
            self.statusLabel.text = "measurement-complete group=\(groupId)\nuploadTimeEpochMs=\(uploadMs)"
        }
    }

    @objc private func runServerSelection() {
        let config = MsakLocateConfig(
            environment: runtimeSnapshot.msakEnvironment,
            userAgent: "ios-test-app-harness",
            localServerHost: runtimeSnapshot.msakLocalServerHost,
            localServerSecure: runtimeSnapshot.msakLocalServerSecure
        )
        let harness = MsakServerSelectionHarness(config: config)
        harness.runDefaultScenario { result, error in
            if let error = error {
                self.statusLabel.text = "server-select failed: \(error)"
                return
            }
            guard let value = result else {
                self.statusLabel.text = "server-select failed: no result"
                return
            }
            self.statusLabel.text =
                "server-select throughput=\(value.throughputMachine)\n" +
                "latency=\(value.latencyMachine)\n" +
                "fallback=\(value.fallbackUsed)"
        }
    }

    @objc private func runPhase3Sequence() {
        let config = MsakLocateConfig(
            environment: runtimeSnapshot.msakEnvironment,
            userAgent: "ios-test-app-phase3",
            localServerHost: runtimeSnapshot.msakLocalServerHost,
            localServerSecure: runtimeSnapshot.msakLocalServerSecure
        )
        let harness = MeasurementSequenceHarness(config: config)
        harness.runDefaultScenario { result, error in
            if let error = error {
                self.statusLabel.text = "phase3 sequence failed: \(error)"
                harness.close()
                return
            }
            guard let value = result else {
                self.statusLabel.text = "phase3 sequence failed: no result"
                harness.close()
                return
            }
            self.statusLabel.text =
                "phase3 sequence group=\(value.groupId)\n" +
                "throughput=\(value.throughputMachine)\n" +
                "latency=\(value.latencyMachine)\n" +
                "submissionCreated=\(value.submissionCreated)\n" +
                "persistedMeasurements=\(value.persistedMeasurements), persistedSubmissions=\(value.persistedSubmissions)"
            harness.close()
        }
    }
}

private enum RuntimeSelection {
    static func resolvePublicMsakLocalSupabaseSnapshot() -> RuntimeSyncMsakProfileSnapshot {
        return RuntimeSyncMsakProfileBridge().resolvePublicMsakLocalSupabase(
            localSupabaseUrl: readConfig("SUPABASE_LOCAL_URL"),
            localSupabaseApiKey: readConfig("SUPABASE_LOCAL_API_KEY"),
            userAgent: "ios-test-app-public-msak-local-supabase"
        )
    }

    private static func readConfig(_ key: String) -> String? {
        let env = ProcessInfo.processInfo.environment
        if let envValue = env[key], !envValue.isEmpty {
            return envValue
        }
        return loadProperty(key)
    }

    private static func loadProperty(_ key: String) -> String? {
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
