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

final class HarnessViewController: UIViewController {
    static let mapStartSharedSliceButtonIdentifier = "harness.mapStartSharedSliceButton"
    static let statusLabelIdentifier = "harness.statusLabel"

    private let statusLabel = UILabel()
    private let smokeEnvelopeBuilder = SyncSmokeEnvelopeBuilder()
    private let smokeFormatter = SyncSmokeResultFormatter()
    private var lastGroupId: String?
    private let msakModeButton = UIButton(type: .system)
    private let supabaseModeButton = UIButton(type: .system)
    private var selectedMsakMode: RuntimeMsakMode = .local
    private var selectedSupabaseMode: RuntimeSupabaseMode = .local
    private var runtimeSnapshot: RuntimeSyncMsakProfileSnapshot?

    private func setStatus(_ text: String) {
        if Thread.isMainThread {
            statusLabel.text = text
        } else {
            DispatchQueue.main.async {
                self.statusLabel.text = text
            }
        }
    }

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

        msakModeButton.addTarget(self, action: #selector(cycleMsakMode), for: .touchUpInside)
        msakModeButton.translatesAutoresizingMaskIntoConstraints = false

        supabaseModeButton.addTarget(self, action: #selector(cycleSupabaseMode), for: .touchUpInside)
        supabaseModeButton.translatesAutoresizingMaskIntoConstraints = false

        let mapStartButton = UIButton(type: .system)
        mapStartButton.setTitle("Run Map-Start Shared Slice", for: .normal)
        mapStartButton.addTarget(self, action: #selector(runMapStart), for: .touchUpInside)
        mapStartButton.translatesAutoresizingMaskIntoConstraints = false
        mapStartButton.accessibilityIdentifier = Self.mapStartSharedSliceButtonIdentifier

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
        statusLabel.accessibilityIdentifier = Self.statusLabelIdentifier

        let stack = UIStackView(arrangedSubviews: [title, localEnvButton, msakModeButton, supabaseModeButton, mapStartButton, completeButton, selectServersButton, runPhase3SequenceButton, statusLabel])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20)
        ])
        applyRuntimeModeChange()
    }

    @objc private func cycleMsakMode() {
        switch selectedMsakMode {
        case .public_: selectedMsakMode = .staging
        case .staging: selectedMsakMode = .local
        case .local: selectedMsakMode = .public_
        default: selectedMsakMode = .public_
        }
        applyRuntimeModeChange()
    }

    @objc private func cycleSupabaseMode() {
        switch selectedSupabaseMode {
        case .local: selectedSupabaseMode = .testing
        case .testing: selectedSupabaseMode = .live
        case .live: selectedSupabaseMode = .local
        default: selectedSupabaseMode = .local
        }
        applyRuntimeModeChange()
    }

    private func applyRuntimeModeChange() {
        do {
            runtimeSnapshot = try RuntimeSelection.resolveSnapshot(
                msakMode: selectedMsakMode,
                supabaseMode: selectedSupabaseMode
            )
            if let snapshot = runtimeSnapshot {
                NSLog(
                    "[iosTestApp] runtime mode updated msak=%@ supabase=%@ msakEnv=%@ msakLocalHost=%@ supabaseUrl=%@ keyPresent=%@",
                    selectedMsakMode.displayName,
                    selectedSupabaseMode.displayName,
                    "\(snapshot.msakEnvironment)",
                    snapshot.msakLocalServerHost ?? "n/a",
                    snapshot.supabaseUrl,
                    snapshot.supabaseApiKey.isEmpty ? "false" : "true"
                )
            }
            msakModeButton.setTitle("MSAK Mode: \(selectedMsakMode.displayName) (tap to cycle)", for: .normal)
            supabaseModeButton.setTitle("Supabase Mode: \(selectedSupabaseMode.displayName) (tap to cycle)", for: .normal)
        } catch {
            runtimeSnapshot = nil
            msakModeButton.setTitle("MSAK Mode: \(selectedMsakMode.displayName) (tap to cycle)", for: .normal)
            supabaseModeButton.setTitle("Supabase Mode: \(selectedSupabaseMode.displayName) (tap to cycle)", for: .normal)
            NSLog("[iosTestApp] runtime mode invalid: %@", error.localizedDescription)
            statusLabel.text = "Runtime mode invalid: \(error.localizedDescription)"
        }
    }

    @objc private func resolveLocalEnvironment() {
        guard let env = runtimeSnapshot else {
            setStatus("Runtime profile unavailable.")
            return
        }
        setStatus("Local environment resolved:\nurl=\(env.supabaseUrl)\napiKeyPrefix=\(env.supabaseApiKey.prefix(12))...")
    }

    @objc private func runMapStart() {
        let groupId = UUID().uuidString
        lastGroupId = groupId
        UploadTriggerParityHarness().runDefaultScenario { result, error in
            if let error = error {
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "map-start-sync",
                    errorMessage: error.localizedDescription
                )
                self.setStatus(self.smokeFormatter.format(envelope: envelope))
                return
            }
            let envelope = self.smokeEnvelopeBuilder.mapStart(
                hasReport: result != nil,
                errorMessage: nil
            )
            var message = self.smokeFormatter.format(envelope: envelope) + "\ngroup=\(groupId)"
            if let value = result {
                message +=
                    "\nmeasurements uploaded=\(value.measurementsUploaded), marked=\(value.measurementsMarkedUploaded)\n" +
                    "submissions uploaded=\(value.submissionsUploaded), blocked=\(value.submissionsBlockedBeforeUpload)"
            }
            self.setStatus(message)
        }
    }

    @objc private func runMeasurementComplete() {
        let groupId = lastGroupId ?? UUID().uuidString
        lastGroupId = groupId
        UploadTriggerParityHarness().runDefaultScenario { result, error in
            if let error = error {
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "measurement-complete-sync",
                    errorMessage: error.localizedDescription
                )
                self.setStatus(self.smokeFormatter.format(envelope: envelope))
                return
            }
            let uploadMs = result?.uploadTimeEpochMs?.int64Value ?? -1
            let envelope = self.smokeEnvelopeBuilder.measurementComplete(
                uploadTimeSet: uploadMs >= 0,
                errorMessage: nil
            )
            self.setStatus(
                self.smokeFormatter.format(envelope: envelope) +
                "\ngroup=\(groupId)\nuploadTimeEpochMs=\(uploadMs)"
            )
        }
    }

    @objc private func runServerSelection() {
        guard let runtimeSnapshot else {
            setStatus("Runtime profile unavailable.")
            return
        }
        let config = MsakLocateConfig(
            environment: runtimeSnapshot.msakEnvironment,
            userAgent: "ios-test-app-harness",
            localServerHost: runtimeSnapshot.msakLocalServerHost,
            localServerSecure: runtimeSnapshot.msakLocalServerSecure
        )
        let harness = MsakServerSelectionHarness(config: config)
        harness.runDefaultScenario { result, error in
            if let error = error {
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "server-select",
                    errorMessage: "\(error)"
                )
                self.setStatus(self.smokeFormatter.format(envelope: envelope))
                return
            }
            guard let value = result else {
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "server-select",
                    errorMessage: "no result"
                )
                self.setStatus(self.smokeFormatter.format(envelope: envelope))
                return
            }
            self.setStatus(
                "server-select throughput=\(value.throughputMachine)\n" +
                "latency=\(value.latencyMachine)\n" +
                "fallback=\(value.fallbackUsed)"
            )
        }
    }

    @objc private func runPhase3Sequence() {
        guard let runtimeSnapshot else {
            setStatus("Runtime profile unavailable.")
            return
        }
        if let preflightError = phase3PreflightError(snapshot: runtimeSnapshot) {
            NSLog("[iosTestApp] Phase3 preflight failed: %@", preflightError)
            let envelope = smokeEnvelopeBuilder.failure(
                scenario: "phase3-preflight",
                errorMessage: preflightError
            )
            setStatus(smokeFormatter.format(envelope: envelope))
            return
        }
        NSLog(
            "[iosTestApp] Phase3 preflight passed msakMode=%@ supabaseMode=%@ msakEnv=%@ msakLocalHost=%@ supabaseUrl=%@ keyPresent=%@",
            selectedMsakMode.displayName,
            selectedSupabaseMode.displayName,
            "\(runtimeSnapshot.msakEnvironment)",
            runtimeSnapshot.msakLocalServerHost ?? "n/a",
            runtimeSnapshot.supabaseUrl,
            runtimeSnapshot.supabaseApiKey.isEmpty ? "false" : "true"
        )
        let config = MsakLocateConfig(
            environment: runtimeSnapshot.msakEnvironment,
            userAgent: "ios-test-app-phase3",
            localServerHost: runtimeSnapshot.msakLocalServerHost,
            localServerSecure: runtimeSnapshot.msakLocalServerSecure
        )
        IosPhase3SequenceSyncHarness().runAsync(
            msakConfig: config,
            supabaseUrl: runtimeSnapshot.supabaseUrl,
            supabaseApiKey: runtimeSnapshot.supabaseApiKey
        ) { result, error in
            if let error = error {
                NSLog("[iosTestApp] Phase3 sequence failed: %@", String(describing: error))
                let hinted = self.withProtocolHint("\(error)")
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "phase3-sequence-sync",
                    errorMessage: hinted
                )
                self.setStatus(self.smokeFormatter.format(envelope: envelope))
                return
            }
            guard let value = result else {
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "phase3-sequence-sync",
                    errorMessage: "no result"
                )
                self.setStatus(self.smokeFormatter.format(envelope: envelope))
                return
            }
            let envelope = self.smokeEnvelopeBuilder.phase3Sequence(
                measurementCompleteUploadTimeSet: value.measurementCompleteUploadTimeSet,
                persistedMeasurements: Int32(value.persistedMeasurements),
                persistedSubmissions: Int32(value.persistedSubmissions),
                errorMessage: value.measurementCompleteUploadTimeSet
                    ? nil
                    : "measurement-complete upload time missing; \(value.measurementCompleteReportSummary)"
            )
                self.setStatus(
                    Phase3UiSliceFormatter().format(
                    envelopeText: self.smokeFormatter.format(envelope: envelope),
                    result: Phase3UiSliceResult(
                        groupId: value.groupId,
                        throughputMachine: value.throughputMachine,
                        latencyMachine: value.latencyMachine,
                        submissionCreated: value.submissionCreated,
                        mapStartMeasurementsUploaded: Int32(value.mapStartMeasurementsUploaded),
                        mapStartSubmissionsUploaded: Int32(value.mapStartSubmissionsUploaded),
                        measurementCompleteUploadTimeSet: value.measurementCompleteUploadTimeSet,
                        persistedMeasurements: Int32(value.persistedMeasurements),
                        persistedSubmissions: Int32(value.persistedSubmissions),
                        capabilityPersistenceSummary: value.capabilityPersistenceSummary,
                        capabilitySummary: value.capabilitySummary
                    )
                )
                )
        }
    }

    private func phase3PreflightError(snapshot: RuntimeSyncMsakProfileSnapshot) -> String? {
        var issues: [String] = []
        if selectedMsakMode == .local {
            if snapshot.msakLocalServerHost?.isEmpty != false {
                issues.append("MSAK LOCAL requires local server host")
            }
        }
        if snapshot.supabaseUrl.isEmpty {
            issues.append("Supabase URL is blank")
        } else if selectedSupabaseMode == .local {
            let url = snapshot.supabaseUrl
            let allowed = url.contains("127.0.0.1") || url.contains("localhost")
            if !allowed {
                issues.append("Supabase LOCAL URL should target localhost (got \(url))")
            }
        }
        if snapshot.supabaseApiKey.isEmpty {
            issues.append("Supabase API key is blank")
        }
        if let localMsakIssue = localMsakReachabilityIssue(snapshot: snapshot) {
            issues.append(localMsakIssue)
        }
        return issues.isEmpty ? nil : issues.joined(separator: "; ")
    }

    private func localMsakReachabilityIssue(snapshot: RuntimeSyncMsakProfileSnapshot) -> String? {
        guard selectedMsakMode == .local else { return nil }
        guard let host = snapshot.msakLocalServerHost, !host.isEmpty else {
            return "MSAK LOCAL requires local server host"
        }
        guard let url = URL(string: "http://\(host)/") else {
            return "MSAK local host is invalid: \(host)"
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 1.5
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

        _ = semaphore.wait(timeout: .now() + 2)
        return reachable ? nil : "MSAK local server is unreachable at \(host)"
    }

    private func withProtocolHint(_ raw: String) -> String {
        let lower = raw.lowercased()
        if lower.contains("missingfieldexception") && lower.contains("bytessent") {
            return "MSAK protocol mismatch: client expects Application.BytesSent/BytesReceived but server payload differs. " +
                "Use a matching local msak-server build for this msak-client-kmp version. details=\(raw)"
        }
        return raw
    }
}

private enum RuntimeSelection {
    static func resolveSnapshot(
        msakMode: RuntimeMsakMode,
        supabaseMode: RuntimeSupabaseMode
    ) throws -> RuntimeSyncMsakProfileSnapshot {
        let configuredLocalMsakHost = readConfig("MSAK_LOCAL_SERVER_HOST")
        let localMsakHost: String?
        if msakMode == .local {
            localMsakHost = (configuredLocalMsakHost?.isEmpty == false) ? configuredLocalMsakHost : "127.0.0.1"
        } else {
            localMsakHost = configuredLocalMsakHost
        }
        return try RuntimeSyncMsakProfileBridge().resolveFromModes(
            msakMode: msakMode,
            supabaseMode: supabaseMode,
            localSupabaseUrl: readConfig("SUPABASE_LOCAL_URL"),
            localSupabaseApiKey: readConfig("SUPABASE_LOCAL_API_KEY"),
            testingSupabaseUrl: readConfig("SUPABASE_TESTING_URL"),
            testingSupabaseApiKey: readConfig("SUPABASE_TESTING_API_KEY"),
            liveSupabaseUrl: readConfig("SUPABASE_URL"),
            liveSupabaseApiKey: readConfig("SUPABASE_API_KEY"),
            allowRemoteSupabase: ProcessInfo.processInfo.environment["CELLWATCH_ALLOW_REMOTE_SUPABASE"] == "true",
            localMsakHost: localMsakHost,
            localMsakSecure: parseBool(readConfig("MSAK_LOCAL_SERVER_SECURE")),
            userAgent: "ios-test-app-runtime-profile"
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

    private static func parseBool(_ raw: String?) -> Bool {
        guard let value = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return false
        }
        return value == "true" || value == "1" || value == "yes" || value == "y"
    }
}

private extension RuntimeMsakMode {
    var displayName: String {
        switch self {
        case .public_: return "PUBLIC"
        case .staging: return "STAGING"
        case .local: return "LOCAL"
        default: return "\(self)"
        }
    }
}

private extension RuntimeSupabaseMode {
    var displayName: String {
        switch self {
        case .local: return "LOCAL"
        case .testing: return "TESTING"
        case .live: return "LIVE"
        default: return "\(self)"
        }
    }
}
