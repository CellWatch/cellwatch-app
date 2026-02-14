import UIKit
import SwiftUI
import CoreLocation
import Network
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
        if let launchValue = launchArgumentValue(key) {
            return launchValue
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

    private static func launchArgumentValue(_ key: String) -> String? {
        let args = ProcessInfo.processInfo.arguments.dropFirst()
        for raw in args {
            let compact = raw.replacingOccurrences(of: " ", with: "")
            if compact.hasPrefix("\(key)=") {
                let value = String(compact.dropFirst(key.count + 1))
                if !value.isEmpty {
                    return value
                }
            }
        }

        let tokens = args.map {
            $0.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "\""))
        }
        for i in tokens.indices {
            if tokens[i] == key, i + 2 < tokens.count, tokens[i + 1] == "=" {
                let value = tokens[i + 2]
                if !value.isEmpty {
                    return value
                }
            }
            if tokens[i] == key, i + 1 < tokens.count, tokens[i + 1] != "=" {
                let value = tokens[i + 1]
                if !value.isEmpty {
                    return value
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
    static func shouldClearOnboardingAtLaunch() -> Bool {
        RuntimeConfigSource.bool("CELLWATCH_CLEAR_ONBOARDING", default: false)
    }

    static func resolveDisplayMode() -> HarnessViewController.DisplayMode {
        let mode = RuntimeConfigSource.value("CELLWATCH_UI_MODE")?.lowercased()
        if mode == "full-harness" {
            return .fullHarness
        }
        if mode == "measurement-start-flow" {
            return .measurementStartFlow
        }
        return .onboardingFlow
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if Self.shouldClearOnboardingAtLaunch() {
            OnboardingUserDefaultsStore().clearProfile()
        }
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

final class OnboardingUserDefaultsStore: NSObject, OnboardingProfileStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        super.init()
    }

    func loadProfile() -> OnboardingProfile? {
        guard defaults.object(forKey: Keys.name) != nil else {
            return nil
        }
        let modeRaw = defaults.string(forKey: Keys.collectionMode) ?? "TESTING"
        let collectionMode = modeRaw == "FCC_CHALLENGE" ? CollectionMode.fccChallenge : CollectionMode.testing
        return OnboardingProfile(
            collectionMode: collectionMode,
            name: defaults.string(forKey: Keys.name) ?? "",
            phone: defaults.string(forKey: Keys.phone) ?? "",
            email: defaults.string(forKey: Keys.email) ?? "",
            fccAcknowledged: defaults.bool(forKey: Keys.ack),
            onboardingComplete: defaults.bool(forKey: Keys.complete)
        )
    }

    func saveProfile(profile: OnboardingProfile) {
        defaults.set(profile.collectionMode == .fccChallenge ? "FCC_CHALLENGE" : "TESTING", forKey: Keys.collectionMode)
        defaults.set(profile.name, forKey: Keys.name)
        defaults.set(profile.phone, forKey: Keys.phone)
        defaults.set(profile.email, forKey: Keys.email)
        defaults.set(profile.fccAcknowledged, forKey: Keys.ack)
        defaults.set(profile.onboardingComplete, forKey: Keys.complete)
    }

    func clearProfile() {
        [Keys.collectionMode, Keys.name, Keys.phone, Keys.email, Keys.ack, Keys.complete]
            .forEach { defaults.removeObject(forKey: $0) }
    }

    private enum Keys {
        static let collectionMode = "cellwatch.onboarding.collection_mode"
        static let name = "cellwatch.onboarding.name"
        static let phone = "cellwatch.onboarding.phone"
        static let email = "cellwatch.onboarding.email"
        static let ack = "cellwatch.onboarding.ack"
        static let complete = "cellwatch.onboarding.complete"
    }
}

final class HarnessViewController: UIViewController, UITextFieldDelegate {
    enum DisplayMode {
        case fullHarness
        case onboardingFlow
        case measurementStartFlow
    }

    enum OnboardingUiImplementation {
        case swiftui
        case uikit
    }
    static let mapStartSharedSliceButtonIdentifier = "harness.mapStartSharedSliceButton"
    static let phase3SequenceButtonIdentifier = "harness.phase3.sequenceButton"
    static let statusLabelIdentifier = "harness.statusLabel"
    static let outputTextViewIdentifier = "harness.outputTextView"
    static let msakModeButtonIdentifier = "harness.msakModeButton"
    static let supabaseModeButtonIdentifier = "harness.supabaseModeButton"
    static let onboardingNameFieldIdentifier = "harness.onboarding.name"
    static let onboardingPhoneFieldIdentifier = "harness.onboarding.phone"
    static let onboardingEmailFieldIdentifier = "harness.onboarding.email"
    static let onboardingAckSwitchIdentifier = "harness.onboarding.ack"
    static let onboardingSubmitButtonIdentifier = "harness.onboarding.submit"
    static let onboardingFeedbackLabelIdentifier = "harness.onboarding.feedback"
    static let onboardingRootContainerIdentifier = "harness.onboarding.container"
    static let onboardingCardIdentifier = "harness.onboarding.card"
    static let measurementPreflightInVehicleIdentifier = "harness.measurementStart.inVehicle"
    static let measurementPreflightEvaluateIdentifier = "harness.measurementStart.evaluate"
    static let measurementPreflightOutputIdentifier = "harness.measurementStart.output"

    private let statusLabel = UILabel()
    private let outputTextView = UITextView()
    private let smokeEnvelopeBuilder = SyncSmokeEnvelopeBuilder()
    private let smokeFormatter = SyncSmokeResultFormatter()
    private let runtimeModeBridge = RuntimeModeUiBridge()
    private let onboardingValidationUseCase = OnboardingValidationUseCase()
    private let onboardingPersistenceUseCase = OnboardingPersistenceUseCase(store: OnboardingUserDefaultsStore())
    private let measurementPreflightUseCase = MeasurementPreflightUseCase()
    private let measurementStartEnvironmentResolver = MeasurementStartPreflightEnvironmentResolver()
    private lazy var measurementStartFlowController = MeasurementStartPreflightFlowController(
        environmentResolver: measurementStartEnvironmentResolver,
        flowViewModel: MeasurementStartPreflightFlowViewModel(
            useCase: measurementPreflightUseCase,
            uiPresenter: MeasurementStartPreflightUiPresenter()
        )
    )
    private let measurementRunViewController = MeasurementRunViewController()
    private let measurementRunUiPresenter = MeasurementRunUiPresenter()
    private lazy var onboardingViewModel = OnboardingProfileViewModel(
        validationUseCase: onboardingValidationUseCase,
        persistenceUseCase: onboardingPersistenceUseCase
    )
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
    private let onboardingFeedbackLabel = UILabel()
    private let onboardingSubmitButton = UIButton(type: .system)
    private let measurementPreflightInVehicleSwitch = UISwitch()
    private let measurementPreflightOutputLabel = UILabel()
    private let measurementNetworkPathProbe = IosMeasurementNetworkPathProbe()
    private var onboardingTopConstraint: NSLayoutConstraint?
    private var onboardingKeyboardObservers: [NSObjectProtocol] = []
    private var onboardingKeyboardHeight: CGFloat = 0
    private let displayMode: DisplayMode
    private let onboardingUiImplementation: OnboardingUiImplementation

    init(
        displayMode: DisplayMode = .fullHarness,
        onboardingUiImplementation: OnboardingUiImplementation = .swiftui
    ) {
        self.displayMode = displayMode
        self.onboardingUiImplementation = onboardingUiImplementation
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        self.displayMode = .fullHarness
        self.onboardingUiImplementation = .swiftui
        super.init(coder: coder)
    }

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
        loadPersistedOnboardingProfile()
        applyOnboardingUiPrefillFromEnvironment()
    }

    deinit {
        onboardingKeyboardObservers.forEach { NotificationCenter.default.removeObserver($0) }
        onboardingKeyboardObservers.removeAll()
    }

    private func buildUi() {
        if displayMode == .onboardingFlow {
            if onboardingUiImplementation == .uikit {
                buildOnboardingFlowUi()
                return
            }
            buildOnboardingFlowSwiftUi()
            return
        }
        if displayMode == .measurementStartFlow {
            buildMeasurementStartFlowUi()
            return
        }

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
        runPhase3SequenceButton.accessibilityIdentifier = Self.phase3SequenceButtonIdentifier
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
        configureOnboardingField(
            onboardingEmailField,
            placeholder: "Email",
            identifier: Self.onboardingEmailFieldIdentifier
        )
        onboardingEmailField.returnKeyType = .done
        onboardingEmailField.addTarget(self, action: #selector(submitOnboardingFromReturnKey), for: .editingDidEndOnExit)

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

    private func buildOnboardingFlowUi() {
        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)

        let title = UILabel()
        title.text = "Complete Your Profile"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label
        title.translatesAutoresizingMaskIntoConstraints = false

        let subtitle = UILabel()
        subtitle.text = "Tell us who you are before starting measurements."
        subtitle.font = UIFont.preferredFont(forTextStyle: .body)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0
        subtitle.translatesAutoresizingMaskIntoConstraints = false

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
        onboardingNameField.addTarget(self, action: #selector(onboardingFieldChanged(_:)), for: .editingChanged)
        onboardingPhoneField.addTarget(self, action: #selector(onboardingFieldChanged(_:)), for: .editingChanged)
        onboardingEmailField.addTarget(self, action: #selector(onboardingFieldChanged(_:)), for: .editingChanged)

        onboardingAckSwitch.accessibilityIdentifier = Self.onboardingAckSwitchIdentifier
        onboardingAckSwitch.translatesAutoresizingMaskIntoConstraints = false
        onboardingAckSwitch.addTarget(self, action: #selector(onboardingAckChanged), for: .valueChanged)
        let ackLabel = UILabel()
        ackLabel.text = "I acknowledge FCC challenge sharing terms."
        ackLabel.numberOfLines = 0
        ackLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        ackLabel.textColor = .secondaryLabel
        ackLabel.translatesAutoresizingMaskIntoConstraints = false
        ackLabel.setContentCompressionResistancePriority(.required, for: .vertical)
        NSLayoutConstraint.activate([
            onboardingAckSwitch.widthAnchor.constraint(equalToConstant: 51),
            onboardingAckSwitch.heightAnchor.constraint(equalToConstant: 31),
        ])
        let ackRow = UIStackView(arrangedSubviews: [ackLabel, onboardingAckSwitch])
        ackRow.axis = .horizontal
        ackRow.alignment = .center
        ackRow.spacing = 12
        ackRow.translatesAutoresizingMaskIntoConstraints = false

        onboardingSubmitButton.setTitle("Save Profile", for: .normal)
        onboardingSubmitButton.accessibilityIdentifier = Self.onboardingSubmitButtonIdentifier
        applyButtonStyle(onboardingSubmitButton, role: .primary)
        applyOnboardingActionButtonStyle(onboardingSubmitButton)
        onboardingSubmitButton.addTarget(self, action: #selector(submitOnboarding), for: .touchUpInside)
        onboardingSubmitButton.translatesAutoresizingMaskIntoConstraints = false
        onboardingSubmitButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 50).isActive = true

        onboardingFeedbackLabel.text = "Complete the form and save your profile."
        onboardingFeedbackLabel.numberOfLines = 0
        onboardingFeedbackLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        onboardingFeedbackLabel.textColor = UIColor(red: 0.18, green: 0.45, blue: 0.22, alpha: 1.0)
        onboardingFeedbackLabel.translatesAutoresizingMaskIntoConstraints = false
        onboardingFeedbackLabel.accessibilityIdentifier = Self.onboardingFeedbackLabelIdentifier

        let card = UIView()
        card.translatesAutoresizingMaskIntoConstraints = false
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.cornerRadius = 14
        card.accessibilityIdentifier = Self.onboardingCardIdentifier
        card.setContentHuggingPriority(.required, for: .vertical)
        card.setContentCompressionResistancePriority(.required, for: .vertical)
        card.heightAnchor.constraint(greaterThanOrEqualToConstant: 400).isActive = true

        let cardStack = UIStackView(arrangedSubviews: [
            onboardingNameField,
            onboardingPhoneField,
            onboardingEmailField,
            ackRow,
            onboardingSubmitButton,
            onboardingFeedbackLabel
        ])
        cardStack.axis = .vertical
        cardStack.spacing = 9
        cardStack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(cardStack)

        statusLabel.text = "Complete the form and save your profile."
        statusLabel.numberOfLines = 0
        statusLabel.font = UIFont.preferredFont(forTextStyle: .body)
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.accessibilityIdentifier = Self.statusLabelIdentifier
        statusLabel.isHidden = true
        statusLabel.alpha = 0.01

        let stack = UIStackView(arrangedSubviews: [title, subtitle, card, statusLabel])
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.accessibilityIdentifier = Self.onboardingRootContainerIdentifier

        view.addSubview(stack)
        let topConstraint = stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8)
        onboardingTopConstraint = topConstraint
        NSLayoutConstraint.activate([
            topConstraint,
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            stack.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),

            cardStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 10),
            cardStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            cardStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            cardStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -10),
            card.widthAnchor.constraint(lessThanOrEqualToConstant: 680),
            card.centerXAnchor.constraint(equalTo: stack.centerXAnchor)
        ])
        installOnboardingKeyboardAvoidance()
        installOnboardingKeyboardDismissGesture()
        setStatus("Ready. Enter onboarding profile details.")
    }

    private func buildMeasurementStartFlowUi() {
        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)

        let title = UILabel()
        title.text = "Measurement Start Preflight"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label

        let subtitle = UILabel()
        subtitle.text = "Confirm your setup before starting a measurement."
        subtitle.font = UIFont.preferredFont(forTextStyle: .body)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0

        measurementPreflightInVehicleSwitch.isOn = false
        measurementPreflightInVehicleSwitch.accessibilityIdentifier = Self.measurementPreflightInVehicleIdentifier
        let inVehicleLabel = UILabel()
        inVehicleLabel.text = "In moving vehicle"
        inVehicleLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        inVehicleLabel.numberOfLines = 0
        let inVehicleRow = UIStackView(arrangedSubviews: [inVehicleLabel, measurementPreflightInVehicleSwitch])
        inVehicleRow.axis = .horizontal
        inVehicleRow.spacing = 12
        inVehicleRow.alignment = .center

        let evaluateButton = UIButton(type: .system)
        evaluateButton.setTitle("Go", for: .normal)
        evaluateButton.accessibilityIdentifier = Self.measurementPreflightEvaluateIdentifier
        applyButtonStyle(evaluateButton, role: .primary)
        evaluateButton.addTarget(self, action: #selector(evaluateMeasurementStartPreflightFromUi), for: .touchUpInside)

        measurementPreflightOutputLabel.text = "Tap Go to check readiness."
        measurementPreflightOutputLabel.font = UIFont.preferredFont(forTextStyle: .body)
        measurementPreflightOutputLabel.numberOfLines = 0
        measurementPreflightOutputLabel.accessibilityIdentifier = Self.measurementPreflightOutputIdentifier
        measurementPreflightOutputLabel.textColor = UIColor(red: 0.18, green: 0.45, blue: 0.22, alpha: 1.0)
        measurementPreflightOutputLabel.translatesAutoresizingMaskIntoConstraints = false

        let outputCard = UIView()
        outputCard.backgroundColor = .white
        outputCard.layer.cornerRadius = 12
        outputCard.layer.borderWidth = 1
        outputCard.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        outputCard.clipsToBounds = true
        outputCard.translatesAutoresizingMaskIntoConstraints = false
        outputCard.addSubview(measurementPreflightOutputLabel)
        NSLayoutConstraint.activate([
            measurementPreflightOutputLabel.topAnchor.constraint(equalTo: outputCard.topAnchor, constant: 10),
            measurementPreflightOutputLabel.leadingAnchor.constraint(equalTo: outputCard.leadingAnchor, constant: 10),
            measurementPreflightOutputLabel.trailingAnchor.constraint(equalTo: outputCard.trailingAnchor, constant: -10),
            measurementPreflightOutputLabel.bottomAnchor.constraint(equalTo: outputCard.bottomAnchor, constant: -10)
        ])

        let cardStack = UIStackView(arrangedSubviews: [
            title,
            subtitle,
            inVehicleRow,
            evaluateButton,
            outputCard
        ])
        cardStack.axis = .vertical
        cardStack.spacing = 12
        cardStack.translatesAutoresizingMaskIntoConstraints = false

        let card = UIView()
        card.backgroundColor = UIColor(white: 0.98, alpha: 1.0)
        card.layer.cornerRadius = 16
        card.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(cardStack)

        view.addSubview(card)
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            card.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            card.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            card.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            cardStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            cardStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            cardStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            cardStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14)
        ])
    }

    private func buildOnboardingFlowSwiftUi() {
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)
        let model = OnboardingFlowSwiftUiModel(viewModel: onboardingViewModel)
        model.bootstrap()

        let root = OnboardingFlowSwiftUiView(
            model: model,
            onStatusUpdate: { [weak self] statusText in
                self?.setStatus(statusText)
            }
        )

        let host = UIHostingController(rootView: root)
        host.view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)
        host.overrideUserInterfaceStyle = .light
        addChild(host)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        host.didMove(toParent: self)
        setStatus("Ready. Enter onboarding profile details.")
    }

    private func installOnboardingKeyboardAvoidance() {
        guard displayMode == .onboardingFlow else { return }
        guard onboardingKeyboardObservers.isEmpty else { return }
        let center = NotificationCenter.default
        let willShow = center.addObserver(
            forName: UIResponder.keyboardWillShowNotification,
            object: nil,
            queue: .main
        ) { [weak self] note in
            self?.handleKeyboard(note: note, showing: true)
        }
        let willHide = center.addObserver(
            forName: UIResponder.keyboardWillHideNotification,
            object: nil,
            queue: .main
        ) { [weak self] note in
            self?.handleKeyboard(note: note, showing: false)
        }
        onboardingKeyboardObservers = [willShow, willHide]
    }

    private func handleKeyboard(note: Notification, showing: Bool) {
        guard let topConstraint = onboardingTopConstraint else { return }
        let userInfo = note.userInfo
        let duration = (userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.2
        let curveRaw = (userInfo?[UIResponder.keyboardAnimationCurveUserInfoKey] as? UInt) ?? 0
        let options = UIView.AnimationOptions(rawValue: curveRaw << 16)
        if showing,
           let value = userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue {
            let keyboardFrame = value.cgRectValue
            let converted = view.convert(keyboardFrame, from: nil)
            onboardingKeyboardHeight = max(0, view.bounds.maxY - converted.minY)
        } else {
            onboardingKeyboardHeight = 0
        }
        topConstraint.constant = 8 - min(120, onboardingKeyboardHeight * 0.35)
        UIView.animate(withDuration: duration, delay: 0, options: options) {
            self.view.layoutIfNeeded()
        }
    }

    private func installOnboardingKeyboardDismissGesture() {
        guard displayMode == .onboardingFlow else { return }
        let gesture = UITapGestureRecognizer(target: self, action: #selector(dismissOnboardingKeyboard))
        gesture.cancelsTouchesInView = false
        view.addGestureRecognizer(gesture)
    }

    @objc private func dismissOnboardingKeyboard() {
        view.endEditing(true)
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
        let story2Preflight = measurementPreflightUseCase.evaluate(
            input: MeasurementStartPreflightInput(
                request: MeasurementStartRequest(
                    collectionMode: .fccChallenge,
                    inVehicle: false
                ),
                hasRuntimeProfile: true,
                // Phase3 harness button is deterministic smoke coverage, not interactive preflight UX.
                hasLocationPermission: true,
                networkPath: .cellular,
                userConfirmedNonCellularChallengePath: true
            )
        )
        if !story2Preflight.allowed {
            let envelope = smokeEnvelopeBuilder.failure(
                scenario: "measurement-start-preflight",
                errorMessage: "reason=\(story2Preflight.reasonCode)"
            )
            setStatus(smokeFormatter.format(envelope: envelope))
            return
        }

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
        let runGroupId = UUID().uuidString
        _ = measurementRunViewController.reset()
        _ = measurementRunViewController.onSequenceStarted(groupId: runGroupId)
        setStatus(measurementRunUiPresenter.present(state: measurementRunViewController.currentState()).headerText)
        let config = MsakLocateConfig(
            environment: runtimeSnapshot.msakEnvironment,
            userAgent: "ios-test-app-phase3",
            localServerHost: runtimeSnapshot.msakLocalServerHost,
            localServerSecure: runtimeSnapshot.msakLocalServerSecure
        )
        IosPhase3SequenceSyncHarness().runAsync(
            msakConfig: config,
            supabaseUrl: runtimeSnapshot.supabaseUrl,
            supabaseApiKey: runtimeSnapshot.supabaseApiKey,
            onProgressHeader: { headerText in
                DispatchQueue.main.async {
                    self.setStatus(headerText)
                }
            }
        ) { result, error in
            if let error = error {
                NSLog("[iosTestApp] Phase3 sequence failed: %@", String(describing: error))
                let hinted = self.withProtocolHint("\(error)")
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "phase3-sequence-sync",
                    errorMessage: hinted
                )
                _ = self.measurementRunViewController.onCompleted(
                    group: nil,
                    errorCode: nil,
                    errorText: hinted
                )
                let runHeader = self.measurementRunUiPresenter
                    .present(state: self.measurementRunViewController.currentState())
                    .headerText
                self.setStatus(runHeader + "\n" + self.smokeFormatter.format(envelope: envelope))
                return
            }
            guard let value = result else {
                let envelope = self.smokeEnvelopeBuilder.failure(
                    scenario: "phase3-sequence-sync",
                    errorMessage: "no result"
                )
                _ = self.measurementRunViewController.onCompleted(
                    group: nil,
                    errorCode: nil,
                    errorText: "no result"
                )
                let runHeader = self.measurementRunUiPresenter
                    .present(state: self.measurementRunViewController.currentState())
                    .headerText
                self.setStatus(runHeader + "\n" + self.smokeFormatter.format(envelope: envelope))
                return
            }
            _ = self.measurementRunViewController.onCompleted(
                group: MeasurementGroup(
                    latency: nil,
                    download: nil,
                    upload: nil,
                    submission: nil,
                    id: value.groupId
                ),
                errorCode: nil,
                errorText: nil
            )
            let runHeader = self.measurementRunUiPresenter
                .present(state: self.measurementRunViewController.currentState())
                .headerText
            let envelope = self.smokeEnvelopeBuilder.phase3Sequence(
                measurementCompleteUploadTimeSet: value.measurementCompleteUploadTimeSet,
                persistedMeasurements: Int32(value.persistedMeasurements),
                persistedSubmissions: Int32(value.persistedSubmissions),
                errorMessage: value.measurementCompleteUploadTimeSet
                    ? nil
                    : "measurement-complete upload time missing; \(value.measurementCompleteReportSummary)"
            )
                self.setStatus(
                    runHeader + "\n" + Phase3UiSliceFormatter().format(
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

    private func measurementStartCollectionMode() -> CollectionMode {
        onboardingPersistenceUseCase.loadProfile()?.collectionMode ?? .fccChallenge
    }

    private func observedMeasurementStartCapabilities() -> MeasurementStartCapabilitySnapshot {
        let status = CLLocationManager.authorizationStatus()
        return MeasurementStartCapabilitySnapshot(
            hasRuntimeProfile: true,
            hasLocationPermission: (status == .authorizedAlways || status == .authorizedWhenInUse),
            networkPath: measurementNetworkPathProbe.currentPath()
        )
    }

    private func measurementStartEnvironmentOverrides() -> MeasurementStartPreflightEnvironmentOverrides {
        let env = ProcessInfo.processInfo.environment
        return MeasurementStartPreflightEnvironmentOverrides(
            collectionMode: parseCollectionModeOverride(env["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_COLLECTION_MODE"]),
            hasRuntimeProfile: parseBooleanOverride(env["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_RUNTIME_PROFILE"])
                .map { KotlinBoolean(bool: $0) },
            hasLocationPermission: parseBooleanOverride(env["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_LOCATION_PERMISSION"])
                .map { KotlinBoolean(bool: $0) },
            networkPath: parseNetworkPathOverride(env["CELLWATCH_MEASUREMENT_PREFLIGHT_OVERRIDE_NETWORK_PATH"])
        )
    }

    private func parseCollectionModeOverride(_ raw: String?) -> CollectionMode? {
        let override = raw?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        switch override {
        case "testing":
            return CollectionMode.testing
        case "fcc", "fcc_challenge":
            return CollectionMode.fccChallenge
        default:
            return nil
        }
    }

    private func parseNetworkPathOverride(_ raw: String?) -> MeasurementNetworkPath? {
        let override = raw?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if let override {
            switch override {
            case "cell", "cellular":
                return MeasurementNetworkPath.cellular
            case "wifi":
                return MeasurementNetworkPath.wifi
            case "unknown":
                return MeasurementNetworkPath.unknown
            default:
                return nil
            }
        }
        return nil
    }

    private func parseBooleanOverride(_ raw: String?) -> Bool? {
        guard let raw else { return nil }
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if value == "1" || value == "true" || value == "yes" || value == "y" {
            return true
        }
        if value == "0" || value == "false" || value == "no" || value == "n" {
            return false
        }
        return nil
    }

    private func renderMeasurementStartPreflightOutput(
        flowState: MeasurementStartPreflightFlowUiState,
        networkPath: MeasurementNetworkPath
    ) {
        measurementPreflightOutputLabel.text = flowState.statusMessage
        measurementPreflightOutputLabel.accessibilityValue = flowState.debugSummary.isEmpty
            ? fallbackDebugSummary(result: flowState.latestResult, networkPath: networkPath)
            : flowState.debugSummary
        measurementPreflightOutputLabel.textColor = flowState.statusIsError
            ? UIColor(red: 0.66, green: 0.14, blue: 0.16, alpha: 1.0)
            : UIColor(red: 0.18, green: 0.45, blue: 0.22, alpha: 1.0)
    }

    @objc private func evaluateMeasurementStartPreflightFromUi() {
        _ = measurementStartFlowController.setInVehicle(value: measurementPreflightInVehicleSwitch.isOn)
        let go = measurementStartFlowController.onGoPressed(
            collectionMode: measurementStartCollectionMode(),
            capabilitySnapshot: observedMeasurementStartCapabilities(),
            overrides: measurementStartEnvironmentOverrides()
        )
        let path = go.environment.networkPath
        renderMeasurementStartPreflightOutput(flowState: go.state, networkPath: path)
        if go.state.shouldPromptConfirmation {
            let dialog = UIAlertController(
                title: nil,
                message: go.state.confirmationMessage ?? "Current network is not cellular. Continue anyway?",
                preferredStyle: .alert
            )
            dialog.addAction(UIAlertAction(title: "Measure anyway", style: .default) { [weak self] _ in
                guard let self else { return }
                let confirmed = self.measurementStartFlowController.onConfirmProceed()
                self.renderMeasurementStartPreflightOutput(
                    flowState: confirmed.state,
                    networkPath: confirmed.environment.networkPath
                )
            })
            dialog.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
                guard let self else { return }
                let canceled = self.measurementStartFlowController.onConfirmCancel()
                self.renderMeasurementStartPreflightOutput(
                    flowState: canceled.state,
                    networkPath: canceled.environment.networkPath
                )
            })
            present(dialog, animated: true)
        }
    }

    private func fallbackDebugSummary(
        result: MeasurementPreflightResult?,
        networkPath: MeasurementNetworkPath
    ) -> String {
        guard let result else {
            return "networkPath=\(networkPath)"
        }
        return "allowed=\(result.allowed);reason=\(result.reasonCode);networkPath=\(networkPath)"
    }

    @objc private func submitOnboarding() {
        _ = syncOnboardingViewModelWithInputs()
        let submission = onboardingViewModel.submit()
        applyOnboardingUiState(submission.state)
        setStatus(submission.statusText)
    }

    @objc private func submitOnboardingFromReturnKey() {
        submitOnboarding()
    }

    @objc private func onboardingFieldChanged(_ sender: UITextField) {
        _ = syncOnboardingViewModelWithInputs()
        applyOnboardingUiState(onboardingViewModel.currentState())
    }

    @objc private func onboardingAckChanged() {
        _ = syncOnboardingViewModelWithInputs()
        applyOnboardingUiState(onboardingViewModel.currentState())
    }

    private func loadPersistedOnboardingProfile() {
        applyOnboardingUiState(onboardingViewModel.loadPersistedProfile())
    }

    private func applyOnboardingUiPrefillFromEnvironment() {
        guard displayMode == .onboardingFlow else { return }
        let env = ProcessInfo.processInfo.environment
        if let name = env["CELLWATCH_UI_PREFILL_NAME"] {
            onboardingNameField.text = name
        }
        if let phone = env["CELLWATCH_UI_PREFILL_PHONE"] {
            onboardingPhoneField.text = phone
        }
        if let email = env["CELLWATCH_UI_PREFILL_EMAIL"] {
            onboardingEmailField.text = email
        }
        if let ackRaw = env["CELLWATCH_UI_PREFILL_ACK"] {
            onboardingAckSwitch.isOn = (ackRaw == "1" || ackRaw.lowercased() == "true")
        }
        if env["CELLWATCH_UI_AUTOSUBMIT"] == "1" {
            submitOnboarding()
        }
        _ = syncOnboardingViewModelWithInputs()
        applyOnboardingUiState(onboardingViewModel.currentState())
    }

    static func clearPersistedOnboardingForTests() {
        OnboardingUserDefaultsStore().clearProfile()
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

    private func applyOnboardingActionButtonStyle(_ button: UIButton) {
        guard var configuration = button.configuration else { return }
        configuration.titleAlignment = .center
        configuration.buttonSize = .large
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 14, leading: 20, bottom: 14, trailing: 20)
        button.configuration = configuration
        button.titleLabel?.adjustsFontSizeToFitWidth = false
    }

    private func configureOnboardingField(_ field: UITextField, placeholder: String, identifier: String) {
        field.placeholder = placeholder
        field.borderStyle = .roundedRect
        field.clearButtonMode = .whileEditing
        field.autocapitalizationType = (identifier == Self.onboardingNameFieldIdentifier) ? .words : .none
        field.translatesAutoresizingMaskIntoConstraints = false
        field.accessibilityIdentifier = identifier
        field.delegate = self
        field.inputAccessoryView = makeOnboardingInputAccessory()
    }

    private func makeOnboardingInputAccessory() -> UIToolbar {
        let toolbar = UIToolbar()
        toolbar.sizeToFit()
        let spacer = UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)
        let done = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(dismissOnboardingKeyboard)
        )
        toolbar.items = [spacer, done]
        return toolbar
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        if textField === onboardingNameField {
            onboardingPhoneField.becomeFirstResponder()
            return false
        }
        if textField === onboardingPhoneField {
            onboardingEmailField.becomeFirstResponder()
            return false
        }
        if textField === onboardingEmailField {
            dismissOnboardingKeyboard()
            submitOnboarding()
            return false
        }
        dismissOnboardingKeyboard()
        return true
    }

    func textFieldDidBeginEditing(_ textField: UITextField) {
        guard displayMode == .onboardingFlow else { return }
        guard let text = textField.text, !text.isEmpty else { return }
        DispatchQueue.main.async {
            textField.selectedTextRange = textField.textRange(
                from: textField.beginningOfDocument,
                to: textField.endOfDocument
            )
        }
    }

    @discardableResult
    private func syncOnboardingViewModelWithInputs() -> OnboardingProfileUiState {
        _ = onboardingViewModel.onNameChanged(name: onboardingNameField.text ?? "")
        _ = onboardingViewModel.onPhoneChanged(phone: onboardingPhoneField.text ?? "")
        _ = onboardingViewModel.onEmailChanged(email: onboardingEmailField.text ?? "")
        let state = onboardingViewModel.onAcknowledgementChanged(acknowledged: onboardingAckSwitch.isOn)
        return state
    }

    private func applyOnboardingUiState(_ state: OnboardingProfileUiState) {
        if onboardingNameField.text != state.name {
            onboardingNameField.text = state.name
        }
        if onboardingPhoneField.text != state.phone {
            onboardingPhoneField.text = state.phone
        }
        if onboardingEmailField.text != state.email {
            onboardingEmailField.text = state.email
        }
        if onboardingAckSwitch.isOn != state.fccAcknowledged {
            onboardingAckSwitch.isOn = state.fccAcknowledged
        }
        onboardingFeedbackLabel.text = state.feedbackMessage
        onboardingFeedbackLabel.textColor = state.feedbackIsError
            ? UIColor(red: 0.66, green: 0.14, blue: 0.16, alpha: 1.0)
            : UIColor(red: 0.18, green: 0.45, blue: 0.22, alpha: 1.0)
    }
}

final class OnboardingFlowSwiftUiModel: ObservableObject {
    private let viewModel: OnboardingProfileViewModel
    @Published var name: String
    @Published var phone: String
    @Published var email: String
    @Published var ack: Bool
    @Published var feedback: String = ""
    @Published var feedbackIsError: Bool = false

    init(viewModel: OnboardingProfileViewModel) {
        self.viewModel = viewModel
        let state = viewModel.currentState()
        self.name = state.name
        self.phone = state.phone
        self.email = state.email
        self.ack = state.fccAcknowledged
        self.feedback = state.feedbackMessage
        self.feedbackIsError = state.feedbackIsError
    }

    func bootstrap() {
        apply(viewModel.loadPersistedProfile())
    }

    func onNameChanged(_ value: String) {
        apply(viewModel.onNameChanged(name: value))
    }

    func onPhoneChanged(_ value: String) {
        apply(viewModel.onPhoneChanged(phone: value))
    }

    func onEmailChanged(_ value: String) {
        apply(viewModel.onEmailChanged(email: value))
    }

    func onAcknowledgementChanged(_ value: Bool) {
        apply(viewModel.onAcknowledgementChanged(acknowledged: value))
    }

    func submit() -> OnboardingProfileSubmission {
        let submission = viewModel.submit()
        apply(submission.state)
        return submission
    }

    private func apply(_ state: OnboardingProfileUiState) {
        name = state.name
        phone = state.phone
        email = state.email
        ack = state.fccAcknowledged
        feedback = state.feedbackMessage
        feedbackIsError = state.feedbackIsError
    }
}

struct OnboardingFlowSwiftUiView: View {
    @ObservedObject var model: OnboardingFlowSwiftUiModel
    let onStatusUpdate: (_ statusText: String) -> Void

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .top) {
                Color(red: 0.94, green: 0.94, blue: 0.97)
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Complete Your Profile")
                            .font(.system(size: 32, weight: .semibold))
                            .foregroundStyle(Color.black)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)

                        Text("Tell us who you are before starting measurements.")
                            .font(.system(size: 18))
                            .foregroundStyle(Color(white: 0.35))
                            .lineLimit(3)
                            .fixedSize(horizontal: false, vertical: true)

                        TextField("Full name", text: $model.name)
                            .textInputAutocapitalization(.words)
                            .accessibilityIdentifier("harness.onboarding.name")
                            .textFieldStyle(.roundedBorder)
                            .onChange(of: model.name) { _, _ in
                                model.onNameChanged(model.name)
                            }

                        TextField("Phone (###-###-####)", text: $model.phone)
                            .keyboardType(.numberPad)
                            .accessibilityIdentifier("harness.onboarding.phone")
                            .textFieldStyle(.roundedBorder)
                            .onChange(of: model.phone) { _, _ in
                                model.onPhoneChanged(model.phone)
                            }

                        TextField("Email", text: $model.email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                            .accessibilityIdentifier("harness.onboarding.email")
                            .textFieldStyle(.roundedBorder)
                            .onChange(of: model.email) { _, _ in
                                model.onEmailChanged(model.email)
                            }

                        HStack(alignment: .center, spacing: 12) {
                            Text("I acknowledge FCC challenge sharing terms.")
                                .font(.system(size: 17))
                                .foregroundStyle(Color(white: 0.35))
                            Spacer(minLength: 8)
                            Toggle("", isOn: $model.ack)
                                .labelsHidden()
                                .accessibilityIdentifier("harness.onboarding.ack")
                                .onChange(of: model.ack) { _, _ in
                                    model.onAcknowledgementChanged(model.ack)
                                }
                        }

                        Button("Save Profile") {
                            let submission = model.submit()
                            onStatusUpdate(submission.statusText)
                        }
                        .buttonStyle(.borderedProminent)
                        .font(.system(size: 20, weight: .semibold))
                        .frame(maxWidth: .infinity, minHeight: 58)
                        .accessibilityIdentifier("harness.onboarding.submit")

                        Text(model.feedback)
                            .font(.system(size: 18))
                            .foregroundStyle(model.feedbackIsError ? Color.red : Color(red: 0.18, green: 0.45, blue: 0.22))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityIdentifier("harness.onboarding.feedback")
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                    .background(Color(white: 0.98))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14)
                .padding(.top, 8)
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .top)
            }
        }
        .preferredColorScheme(.light)
    }
}

final class IosMeasurementNetworkPathProbe {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "cellwatch.measurement.path-probe")
    private let lock = NSLock()
    private var path: MeasurementNetworkPath = .unknown

    init() {
        monitor.pathUpdateHandler = { [weak self] nwPath in
            guard let self else { return }
            let mapped: MeasurementNetworkPath
            if nwPath.usesInterfaceType(.cellular) {
                mapped = .cellular
            } else if nwPath.usesInterfaceType(.wifi) {
                mapped = .wifi
            } else {
                mapped = .unknown
            }
            self.lock.lock()
            self.path = mapped
            self.lock.unlock()
        }
        monitor.start(queue: queue)
    }

    func currentPath() -> MeasurementNetworkPath {
        lock.lock()
        defer { lock.unlock() }
        return path
    }

    deinit {
        monitor.cancel()
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
