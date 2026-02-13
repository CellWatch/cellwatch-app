import UIKit
import sharedKit

enum RuntimeConfigSource {
    private static let defaultLocalServiceRoleJwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0." +
        "EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU"

    static func value(_ key: String) -> String? {
        let env = ProcessInfo.processInfo.environment
        if let envValue = env[key], !envValue.isEmpty {
            return envValue
        }
        return property(key)
    }

    static func bool(_ key: String, default defaultValue: Bool = false) -> Bool {
        guard let raw = value(key)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased() else {
            return defaultValue
        }
        return raw == "true" || raw == "1" || raw == "yes" || raw == "y"
    }

    static func localSupabaseUrlForIos() -> String? {
        let raw = value("SUPABASE_LOCAL_URL")
        let normalized = normalizeIosLoopback(raw)
        if let normalized, !normalized.isEmpty {
            return normalized
        }
        return "http://127.0.0.1:54321"
    }

    static func localSupabaseApiKeyPreferServiceRoleJwt() -> String? {
        for key in [
            "SUPABASE_LOCAL_SERVICE_ROLE_KEY",
            "SERVICE_ROLE_KEY",
            "SUPABASE_LOCAL_SERVICE_KEY",
            "SUPABASE_LOCAL_API_KEY"
        ] {
            if let candidate = value(key), candidate.hasPrefix("eyJ") {
                return candidate
            }
        }
        return defaultLocalServiceRoleJwt
    }

    static func localMsakHostForIos(msakModeRaw: String) -> String? {
        let configured = normalizeIosLoopback(value("MSAK_LOCAL_SERVER_HOST"))
        if msakModeRaw.uppercased() == "LOCAL" {
            return (configured?.isEmpty == false) ? configured : "127.0.0.1:8080"
        }
        return configured
    }

    private static func property(_ key: String) -> String? {
        let candidates = [
            URL(fileURLWithPath: "iosTestApp/cellwatch.local.properties"),
            URL(fileURLWithPath: "cellwatch.local.properties"),
            URL(fileURLWithPath: "iosTestApp/cellwatch.properties"),
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
                    return parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
                        .trimmingCharacters(in: CharacterSet(charactersIn: "\""))
                }
            }
        }
        return nil
    }

    private static func normalizeIosLoopback(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\""))
        if value.isEmpty {
            return nil
        }
        return value
            .replacingOccurrences(of: "10.0.2.2", with: "127.0.0.1")
            .replacingOccurrences(of: "10.0.3.2", with: "127.0.0.1")
    }
}

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
    static let outputTextViewIdentifier = "harness.outputTextView"
    static let msakModeButtonIdentifier = "harness.msakModeButton"
    static let supabaseModeButtonIdentifier = "harness.supabaseModeButton"
    static let onboardingNameFieldIdentifier = "harness.onboarding.name"
    static let onboardingPhoneFieldIdentifier = "harness.onboarding.phone"
    static let onboardingEmailFieldIdentifier = "harness.onboarding.email"
    static let onboardingAckSwitchIdentifier = "harness.onboarding.ack"
    static let onboardingSubmitButtonIdentifier = "harness.onboarding.submit"

    private let statusLabel = UILabel()
    private let outputTextView = UITextView()
    private let smokeEnvelopeBuilder = SyncSmokeEnvelopeBuilder()
    private let smokeFormatter = SyncSmokeResultFormatter()
    private let runtimeModeBridge = RuntimeModeUiBridge()
    private let onboardingValidationUseCase = OnboardingValidationUseCase()
    private var lastGroupId: String?
    private let msakModeButton = UIButton(type: .system)
    private let supabaseModeButton = UIButton(type: .system)
    private var selectedMsakMode: RuntimeMsakMode = .local
    private var selectedSupabaseMode: RuntimeSupabaseMode = .local
    private var runtimeSnapshot: RuntimeSyncMsakProfileSnapshot?
    private var diagnosticsSummary: String = "unconfigured"
    private var outputHistory: String = ""
    private let maxOutputChars = 80_000
    private let timestampFormatter = ISO8601DateFormatter()
    private let onboardingNameField = UITextField()
    private let onboardingPhoneField = UITextField()
    private let onboardingEmailField = UITextField()
    private let onboardingAckSwitch = UISwitch()

    private func setStatus(_ text: String) {
        let timestamp = timestampFormatter.string(from: Date())
        let entry = "[\(timestamp)]\n\(text)"
        outputHistory = appendAndTrim(existing: outputHistory, next: entry)
        let compactForLog = text.replacingOccurrences(of: "\n", with: " | ")
        NSLog("[iosTestApp][status] %@", compactForLog)
        if Thread.isMainThread {
            statusLabel.text = text
            outputTextView.text = outputHistory
            scrollOutputToTop()
        } else {
            DispatchQueue.main.async {
                self.statusLabel.text = text
                self.outputTextView.text = self.outputHistory
                self.scrollOutputToTop()
            }
        }
    }

    private func appendAndTrim(existing: String, next: String) -> String {
        let merged = existing.isEmpty ? next : (next + "\n\n" + existing)
        guard merged.count > maxOutputChars else { return merged }
        let keep = merged.prefix(maxOutputChars)
        return String(keep) + "\n\n[truncated older output]"
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureSyncDiagnostics()
        view.backgroundColor = .systemGroupedBackground
        buildUi()
    }

    private func buildUi() {
        let title = UILabel()
        title.text = "iOS Test App Harness"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label
        title.translatesAutoresizingMaskIntoConstraints = false

        let subtitle = UILabel()
        subtitle.text = "CellWatch runtime onboarding + phase 3 actions"
        subtitle.font = UIFont.preferredFont(forTextStyle: .subheadline)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0
        subtitle.translatesAutoresizingMaskIntoConstraints = false

        let localEnvButton = UIButton(type: .system)
        localEnvButton.setTitle("Resolve Local Environment", for: .normal)
        applyButtonStyle(localEnvButton, role: .secondary)
        localEnvButton.addTarget(self, action: #selector(resolveLocalEnvironment), for: .touchUpInside)
        localEnvButton.translatesAutoresizingMaskIntoConstraints = false

        let copyOutputButton = UIButton(type: .system)
        copyOutputButton.setTitle("Copy Output", for: .normal)
        applyButtonStyle(copyOutputButton, role: .secondary)
        copyOutputButton.addTarget(self, action: #selector(copyOutput), for: .touchUpInside)
        copyOutputButton.translatesAutoresizingMaskIntoConstraints = false

        applyButtonStyle(msakModeButton, role: .mode)
        msakModeButton.accessibilityIdentifier = Self.msakModeButtonIdentifier
        msakModeButton.addTarget(self, action: #selector(cycleMsakMode), for: .touchUpInside)
        msakModeButton.translatesAutoresizingMaskIntoConstraints = false

        applyButtonStyle(supabaseModeButton, role: .mode)
        supabaseModeButton.accessibilityIdentifier = Self.supabaseModeButtonIdentifier
        supabaseModeButton.addTarget(self, action: #selector(cycleSupabaseMode), for: .touchUpInside)
        supabaseModeButton.translatesAutoresizingMaskIntoConstraints = false

        let mapStartButton = UIButton(type: .system)
        mapStartButton.setTitle("Run Map-Start Shared Slice", for: .normal)
        applyButtonStyle(mapStartButton, role: .secondary)
        mapStartButton.addTarget(self, action: #selector(runMapStart), for: .touchUpInside)
        mapStartButton.translatesAutoresizingMaskIntoConstraints = false
        mapStartButton.accessibilityIdentifier = Self.mapStartSharedSliceButtonIdentifier

        let completeButton = UIButton(type: .system)
        completeButton.setTitle("Run Measurement-Complete Shared Slice", for: .normal)
        applyButtonStyle(completeButton, role: .secondary)
        completeButton.addTarget(self, action: #selector(runMeasurementComplete), for: .touchUpInside)
        completeButton.translatesAutoresizingMaskIntoConstraints = false

        let selectServersButton = UIButton(type: .system)
        selectServersButton.setTitle("Select MSAK Servers (Shared Selector)", for: .normal)
        applyButtonStyle(selectServersButton, role: .secondary)
        selectServersButton.addTarget(self, action: #selector(runServerSelection), for: .touchUpInside)
        selectServersButton.translatesAutoresizingMaskIntoConstraints = false

        let runPhase3SequenceButton = UIButton(type: .system)
        runPhase3SequenceButton.setTitle("Run Phase3 Sequence (Shared Orchestrator)", for: .normal)
        applyButtonStyle(runPhase3SequenceButton, role: .primary)
        runPhase3SequenceButton.addTarget(self, action: #selector(runPhase3Sequence), for: .touchUpInside)
        runPhase3SequenceButton.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.text = "Ready. Remote target is blocked unless explicitly enabled."
        statusLabel.numberOfLines = 1
        statusLabel.font = UIFont.preferredFont(forTextStyle: .body)
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.accessibilityIdentifier = Self.statusLabelIdentifier
        statusLabel.isHidden = true

        outputTextView.text = statusLabel.text
        outputTextView.font = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)
        outputTextView.isEditable = false
        outputTextView.isSelectable = true
        outputTextView.alwaysBounceVertical = true
        outputTextView.backgroundColor = .secondarySystemBackground
        outputTextView.layer.cornerRadius = 8
        outputTextView.textContainerInset = UIEdgeInsets(top: 12, left: 10, bottom: 12, right: 10)
        outputTextView.translatesAutoresizingMaskIntoConstraints = false
        outputTextView.accessibilityIdentifier = Self.outputTextViewIdentifier

        let runtimeHeader = sectionLabel("Runtime")
        let actionsHeader = sectionLabel("Actions")
        let onboardingHeader = sectionLabel("Onboarding Profile")

        configureOnboardingField(
            onboardingNameField,
            placeholder: "Full name",
            identifier: Self.onboardingNameFieldIdentifier
        )
        configureOnboardingField(
            onboardingPhoneField,
            placeholder: "Phone (###-###-####)",
            identifier: Self.onboardingPhoneFieldIdentifier
        )
        onboardingPhoneField.keyboardType = .phonePad
        configureOnboardingField(
            onboardingEmailField,
            placeholder: "Email",
            identifier: Self.onboardingEmailFieldIdentifier
        )
        onboardingEmailField.keyboardType = .emailAddress

        onboardingAckSwitch.accessibilityIdentifier = Self.onboardingAckSwitchIdentifier
        let ackRow = UIStackView(arrangedSubviews: [UILabel(), onboardingAckSwitch])
        if let ackLabel = ackRow.arrangedSubviews.first as? UILabel {
            ackLabel.text = "I acknowledge FCC challenge sharing terms."
            ackLabel.numberOfLines = 0
            ackLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        }
        ackRow.axis = .horizontal
        ackRow.spacing = 12
        ackRow.alignment = .center
        ackRow.translatesAutoresizingMaskIntoConstraints = false

        let onboardingSubmitButton = UIButton(type: .system)
        onboardingSubmitButton.setTitle("Submit Onboarding", for: .normal)
        onboardingSubmitButton.accessibilityIdentifier = Self.onboardingSubmitButtonIdentifier
        applyButtonStyle(onboardingSubmitButton, role: .primary)
        onboardingSubmitButton.addTarget(self, action: #selector(submitOnboarding), for: .touchUpInside)
        onboardingSubmitButton.translatesAutoresizingMaskIntoConstraints = false

        let buttonsStack = UIStackView(arrangedSubviews: [
            title,
            subtitle,
            onboardingHeader,
            onboardingNameField,
            onboardingPhoneField,
            onboardingEmailField,
            ackRow,
            onboardingSubmitButton,
            runtimeHeader,
            localEnvButton,
            copyOutputButton,
            msakModeButton,
            supabaseModeButton,
            actionsHeader,
            mapStartButton,
            completeButton,
            selectServersButton,
            runPhase3SequenceButton,
            statusLabel
        ])
        buttonsStack.axis = .vertical
        buttonsStack.spacing = 16
        buttonsStack.translatesAutoresizingMaskIntoConstraints = false

        let buttonsScrollView = UIScrollView()
        buttonsScrollView.translatesAutoresizingMaskIntoConstraints = false
        buttonsScrollView.alwaysBounceVertical = true
        buttonsScrollView.addSubview(buttonsStack)

        view.addSubview(buttonsScrollView)
        view.addSubview(outputTextView)
        NSLayoutConstraint.activate([
            buttonsScrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            buttonsScrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            buttonsScrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            buttonsScrollView.heightAnchor.constraint(greaterThanOrEqualToConstant: 220),
            buttonsScrollView.heightAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.heightAnchor, multiplier: 0.55),

            buttonsStack.topAnchor.constraint(equalTo: buttonsScrollView.contentLayoutGuide.topAnchor, constant: 12),
            buttonsStack.leadingAnchor.constraint(equalTo: buttonsScrollView.contentLayoutGuide.leadingAnchor),
            buttonsStack.trailingAnchor.constraint(equalTo: buttonsScrollView.contentLayoutGuide.trailingAnchor),
            buttonsStack.bottomAnchor.constraint(equalTo: buttonsScrollView.contentLayoutGuide.bottomAnchor, constant: -12),
            buttonsStack.widthAnchor.constraint(equalTo: buttonsScrollView.frameLayoutGuide.widthAnchor),

            outputTextView.topAnchor.constraint(equalTo: buttonsScrollView.bottomAnchor, constant: 12),
            outputTextView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            outputTextView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            outputTextView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12)
        ])
        applyRuntimeModeChange()
        setStatus("Ready. Remote target is blocked unless explicitly enabled.")
    }

    private func scrollOutputToTop() {
        outputTextView.setContentOffset(.zero, animated: false)
    }

    @objc private func cycleMsakMode() {
        selectedMsakMode = runtimeModeBridge.nextMsakMode(current: selectedMsakMode)
        applyRuntimeModeChange()
    }

    @objc private func cycleSupabaseMode() {
        selectedSupabaseMode = runtimeModeBridge.nextSupabaseMode(current: selectedSupabaseMode)
        applyRuntimeModeChange()
    }

    private func applyRuntimeModeChange() {
        let resolution = RuntimeSelection.resolve(
            bridge: runtimeModeBridge,
            msakMode: selectedMsakMode,
            supabaseMode: selectedSupabaseMode
        )
        runtimeSnapshot = resolution.snapshot
        msakModeButton.setTitle(
            "MSAK Mode: \(runtimeModeBridge.msakModeLabel(mode: selectedMsakMode)) (tap to cycle)",
            for: .normal
        )
        supabaseModeButton.setTitle(
            "Supabase Mode: \(runtimeModeBridge.supabaseModeLabel(mode: selectedSupabaseMode)) (tap to cycle)",
            for: .normal
        )
        if let snapshot = runtimeSnapshot {
            NSLog(
                "[iosTestApp] runtime mode updated msak=%@ supabase=%@ msakEnv=%@ msakLocalHost=%@ supabaseUrl=%@ keyPresent=%@ diagnostics=%@",
                runtimeModeBridge.msakModeLabel(mode: selectedMsakMode),
                runtimeModeBridge.supabaseModeLabel(mode: selectedSupabaseMode),
                "\(snapshot.msakEnvironment)",
                snapshot.msakLocalServerHost ?? "n/a",
                snapshot.supabaseUrl,
                snapshot.supabaseApiKey.isEmpty ? "false" : "true",
                diagnosticsSummary
            )
        } else if let error = resolution.errorMessage {
            runtimeSnapshot = nil
            NSLog("[iosTestApp] runtime mode invalid: %@", error)
            setStatus("Runtime mode invalid: \(error)")
        }
    }

    @objc private func resolveLocalEnvironment() {
        guard let env = runtimeSnapshot else {
            setStatus("Runtime profile unavailable.")
            return
        }
        setStatus(
            "Local environment resolved:\n" +
            "url=\(env.supabaseUrl)\n" +
            "apiKeyPrefix=\(env.supabaseApiKey.prefix(12))...\n" +
            "diagnostics=\(diagnosticsSummary)"
        )
    }

    @objc private func copyOutput() {
        UIPasteboard.general.string = outputTextView.text
        setStatus("Output copied to clipboard.")
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
            "[iosTestApp] Phase3 preflight passed msakMode=%@ supabaseMode=%@ msakEnv=%@ msakLocalHost=%@ supabaseUrl=%@ keyPresent=%@ diagnostics=%@",
            selectedMsakMode.displayName,
            selectedSupabaseMode.displayName,
            "\(runtimeSnapshot.msakEnvironment)",
            runtimeSnapshot.msakLocalServerHost ?? "n/a",
            runtimeSnapshot.supabaseUrl,
            runtimeSnapshot.supabaseApiKey.isEmpty ? "false" : "true",
            diagnosticsSummary
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
                NSLog("[iosTestApp] Phase3 sequence success diagnostics=%@", self.diagnosticsSummary)
        }
    }

    private func configureSyncDiagnostics() {
        let level = RuntimeSelection.readConfig("CELLWATCH_SYNC_DIAGNOSTICS_LEVEL") ?? "VERBOSE"
        let maxSamplesRaw = RuntimeSelection.readConfig("CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES")
        let maxSamples = Int(maxSamplesRaw ?? "") ?? 12
        let includeCauseChain = RuntimeConfigSource.bool(
            "CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN",
            default: true
        )
        diagnosticsSummary = IosSyncDiagnosticsBridge().configure(
            levelRaw: level,
            maxSampledErrorsPerReport: Int32(maxSamples),
            includeCauseChain: includeCauseChain
        )
        NSLog("[iosTestApp] Sync diagnostics configured: %@", diagnosticsSummary)
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
        } else if selectedSupabaseMode == .local, isAnonJwt(snapshot.supabaseApiKey) {
            issues.append("Supabase LOCAL API key has anon role; local phase3 sync requires service-role JWT")
        }
        if let localMsakIssue = localMsakReachabilityIssue(snapshot: snapshot) {
            issues.append(localMsakIssue)
        }
        return issues.isEmpty ? nil : issues.joined(separator: "; ")
    }

    private func isAnonJwt(_ jwt: String) -> Bool {
        let parts = jwt.split(separator: ".")
        guard parts.count >= 2 else { return false }
        var payload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while payload.count % 4 != 0 {
            payload.append("=")
        }
        guard let data = Data(base64Encoded: payload),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let role = object["role"] as? String else {
            return false
        }
        return role == "anon"
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

    @objc private func submitOnboarding() {
        let profile = OnboardingProfile(
            collectionMode: .testing,
            name: onboardingNameField.text ?? "",
            phone: onboardingPhoneField.text ?? "",
            email: onboardingEmailField.text ?? "",
            fccAcknowledged: onboardingAckSwitch.isOn,
            onboardingComplete: false
        )
        let result = onboardingValidationUseCase.validate(profile: profile)
        if result.valid {
            setStatus(
                "Onboarding submit=SUCCESS\n" +
                "name=\(result.normalizedProfile.name)\n" +
                "phone=\(result.normalizedProfile.phone)\n" +
                "email=\(result.normalizedProfile.email)\n" +
                "ack=\(result.normalizedProfile.fccAcknowledged)\n" +
                "onboardingComplete=\(result.normalizedProfile.onboardingComplete)"
            )
        } else {
            let errors = result.fieldErrors.map { key, value in
                "\(key):\(value)"
            }.joined(separator: "; ")
            setStatus("Onboarding submit=FAILURE\nerrors=\(errors)")
        }
    }

    private func sectionLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = UIFont.preferredFont(forTextStyle: .caption1)
        label.textColor = .secondaryLabel
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }

    private enum ButtonRole {
        case primary
        case secondary
        case mode
    }

    private func applyButtonStyle(_ button: UIButton, role: ButtonRole) {
        var configuration = UIButton.Configuration.filled()
        configuration.cornerStyle = .large
        configuration.titleAlignment = .leading
        configuration.baseForegroundColor = .label
        switch role {
        case .primary:
            configuration.baseBackgroundColor = .systemBlue
            configuration.baseForegroundColor = .white
        case .secondary:
            configuration = UIButton.Configuration.tinted()
            configuration.cornerStyle = .large
            configuration.baseBackgroundColor = .systemBlue
            configuration.baseForegroundColor = .systemBlue
        case .mode:
            configuration = UIButton.Configuration.tinted()
            configuration.cornerStyle = .large
            configuration.baseBackgroundColor = .systemGreen
            configuration.baseForegroundColor = .systemGreen
        }
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12)
        button.configuration = configuration
    }

    private func configureOnboardingField(_ field: UITextField, placeholder: String, identifier: String) {
        field.placeholder = placeholder
        field.borderStyle = .roundedRect
        field.clearButtonMode = .whileEditing
        field.autocapitalizationType = .none
        field.translatesAutoresizingMaskIntoConstraints = false
        field.accessibilityIdentifier = identifier
    }
}

private enum RuntimeSelection {
    static func resolve(
        bridge: RuntimeModeUiBridge,
        msakMode: RuntimeMsakMode,
        supabaseMode: RuntimeSupabaseMode
    ) -> RuntimeModeUiResolution {
        let localMsakHost = RuntimeConfigSource.localMsakHostForIos(msakModeRaw: msakMode.displayName)
        let localSupabaseApiKey = RuntimeConfigSource.localSupabaseApiKeyPreferServiceRoleJwt()
        let resolvedLocalSupabaseUrl = RuntimeConfigSource.localSupabaseUrlForIos()
        let draft = RuntimeOnboardingDraft(
            msakMode: msakMode,
            supabaseMode: supabaseMode,
            localSupabaseUrl: resolvedLocalSupabaseUrl,
            localSupabaseApiKey: localSupabaseApiKey,
            testingSupabaseUrl: RuntimeConfigSource.value("SUPABASE_TESTING_URL"),
            testingSupabaseApiKey: RuntimeConfigSource.value("SUPABASE_TESTING_API_KEY"),
            liveSupabaseUrl: RuntimeConfigSource.value("SUPABASE_URL"),
            liveSupabaseApiKey: RuntimeConfigSource.value("SUPABASE_API_KEY"),
            localMsakHost: localMsakHost,
            localMsakSecure: RuntimeConfigSource.bool("MSAK_LOCAL_SERVER_SECURE")
        )
        let config = draft.toRuntimeProfileConfig(
            allowRemoteSupabase: RuntimeConfigSource.bool("CELLWATCH_ALLOW_REMOTE_SUPABASE"),
            strictSupabaseConfig: true,
            userAgent: "ios-test-app-runtime-profile"
        )
        return bridge.resolve(config: config)
    }

    static func readConfig(_ key: String) -> String? {
        RuntimeConfigSource.value(key)
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
