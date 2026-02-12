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
            environment: MsakLocateEnvironment.prod,
            userAgent: "ios-test-app-harness",
            localServerHost: nil,
            localServerSecure: false
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
            environment: MsakLocateEnvironment.prod,
            userAgent: "ios-test-app-phase3",
            localServerHost: nil,
            localServerSecure: false
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
            let useRemote = target == .remote
            let resolved = try UploadTriggerParityHarness().resolveSupabaseConfigForRuntime(
                allowRemote: allowRemote,
                localUrl: properties["SUPABASE_LOCAL_URL"] ?? "",
                localApiKey: properties["SUPABASE_LOCAL_API_KEY"] ?? "",
                remoteUrl: properties["SUPABASE_URL"] ?? "",
                remoteApiKey: properties["SUPABASE_API_KEY"] ?? "",
                useRemote: useRemote
            )
            return SupabaseEnvironment(
                target: target,
                url: resolved.url,
                apiKey: resolved.apiKey
            )
        } catch {
            throw NSError(
                domain: "iosTestApp",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "\(error)"]
            )
        }
    }
}
