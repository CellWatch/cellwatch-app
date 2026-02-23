import UIKit
import SwiftUI
import CoreLocation
import Network
import sharedKit
#if canImport(MapboxMaps)
import MapboxMaps
#endif

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

    static func mapboxAccessToken() -> String? {
        if let explicit = value("MAPBOX_ACCESS_TOKEN"), !explicit.isEmpty {
            return explicit
        }
        if let bundled = bundledRuntimeProperty("MAPBOX_ACCESS_TOKEN"), !bundled.isEmpty {
            return bundled
        }
        if let plist = Bundle.main.object(forInfoDictionaryKey: "MAPBOX_ACCESS_TOKEN") as? String,
           !plist.isEmpty,
           !plist.contains("$(") {
            return plist
        }
        if let legacy = value("mapbox_access_token"), !legacy.isEmpty {
            return legacy
        }
        if let downloads = value("MAPBOX_DOWNLOADS_TOKEN"), !downloads.isEmpty {
            return downloads
        }
        return nil
    }

    private static func bundledRuntimeProperty(_ key: String) -> String? {
        guard let url = Bundle.main.url(forResource: "cellwatch.runtime", withExtension: "properties"),
              let contents = try? String(contentsOf: url, encoding: .utf8) else {
            return nil
        }
        for rawLine in contents.split(separator: "\n", omittingEmptySubsequences: false) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty || line.hasPrefix("#") {
                continue
            }
            let parts = line.split(separator: "=", maxSplits: 1).map(String.init)
            if parts.count == 2 && parts[0].trimmingCharacters(in: .whitespacesAndNewlines) == key {
                return parts[1]
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .trimmingCharacters(in: CharacterSet(charactersIn: "\""))
            }
        }
        return nil
    }

    private static func property(_ key: String) -> String? {
        var candidates = [
            URL(fileURLWithPath: "iosTestApp/cellwatch.local.properties"),
            URL(fileURLWithPath: "cellwatch.local.properties"),
            URL(fileURLWithPath: "iosTestApp/cellwatch.properties"),
            URL(fileURLWithPath: "cellwatch.properties"),
            URL(fileURLWithPath: "../cellwatch.properties"),
            URL(fileURLWithPath: "../../cellwatch.properties"),
        ]
        if let userName = ProcessInfo.processInfo.environment["USER"] {
            candidates.append(URL(fileURLWithPath: "/Users/\(userName)/Projects/cellwatch-app/cellwatch.local.properties"))
            candidates.append(URL(fileURLWithPath: "/Users/\(userName)/Projects/cellwatch-app/cellwatch.properties"))
        }
        // Interactive Xcode runs can have a working directory where relative paths above miss.
        // Walk up from source path as a dev-harness fallback.
        var sourceDir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<8 {
            candidates.append(sourceDir.appendingPathComponent("cellwatch.local.properties"))
            candidates.append(sourceDir.appendingPathComponent("cellwatch.properties"))
            let parent = sourceDir.deletingLastPathComponent()
            if parent.path == sourceDir.path {
                break
            }
            sourceDir = parent
        }
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
        if mode == "map-home" || mode == "home" {
            return .mapHome
        }
        if mode == "mvp-menu" || mode == "mvp" {
            return .mvpMenu
        }
        if mode == "onboarding-flow" {
            return .onboardingFlow
        }
        if mode == "settings-profile-flow" {
            return .settingsProfileFlow
        }
        if mode == "measurement-start-flow" {
            return .measurementStartFlow
        }
        if mode == "pending-sync-flow" {
            return .pendingSyncFlow
        }
        if mode == "measurement-run-flow" {
            return .measurementRunFlow
        }
        if mode == "measurement-history-flow" {
            return .measurementHistoryFlow
        }
        let onboardingComplete = OnboardingUserDefaultsStore().loadProfile()?.onboardingComplete == true
        let decision = AppLaunchRoutingUseCase().resolve(
            input: AppLaunchRoutingInput(
                onboardingComplete: onboardingComplete,
                runtimeProfileReady: true
            )
        )
        switch decision.destinationToken {
        case "map-home":
            return .mapHome
        case "mvp-home":
            return .mvpMenu
        case "blocking-error":
            return .onboardingFlow
        default:
            return .onboardingFlow
        }
    }

    static func shouldDefaultToMvpNavigation() -> Bool {
        let mode = RuntimeConfigSource.value("CELLWATCH_UI_MODE")?.lowercased()
        return mode == nil || mode == "map-home" || mode == "home" || mode == "mvp-menu" || mode == "mvp"
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

final class InsetLabel: UILabel {
    var textInsets: UIEdgeInsets = .zero {
        didSet {
            invalidateIntrinsicContentSize()
            setNeedsDisplay()
        }
    }

    override func drawText(in rect: CGRect) {
        super.drawText(in: rect.inset(by: textInsets))
    }

    override var intrinsicContentSize: CGSize {
        let size = super.intrinsicContentSize
        return CGSize(
            width: size.width + textInsets.left + textInsets.right,
            height: size.height + textInsets.top + textInsets.bottom
        )
    }
}

final class HarnessViewController: UIViewController, UITextFieldDelegate {
    enum DisplayMode {
        case fullHarness
        case mapHome
        case mvpMenu
        case onboardingFlow
        case settingsProfileFlow
        case measurementStartFlow
        case pendingSyncFlow
        case measurementRunFlow
        case measurementHistoryFlow
    }

    enum OnboardingUiImplementation {
        case swiftui
        case uikit
    }
    static let mapStartSharedSliceButtonIdentifier = "harness.mapStartSharedSliceButton"
    static let pendingSyncCountsButtonIdentifier = "harness.pendingSync.countsButton"
    static let retryPendingSyncButtonIdentifier = "harness.pendingSync.retryButton"
    static let pendingSyncSummaryIdentifier = "harness.pendingSync.summary"
    static let pendingSyncDetailIdentifier = "harness.pendingSync.detail"
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
    static let settingsModeControlIdentifier = "harness.settings.mode"
    static let settingsSubmitButtonIdentifier = "harness.settings.submit"
    static let settingsDeviceIdValueIdentifier = "harness.settings.deviceId.value"
    static let settingsAppVersionValueIdentifier = "harness.settings.appVersion.value"
    static let settingsCopyDeviceIdButtonIdentifier = "harness.settings.copyDeviceId"
    static let settingsCopyAppVersionButtonIdentifier = "harness.settings.copyAppVersion"
    static let onboardingFeedbackLabelIdentifier = "harness.onboarding.feedback"
    static let onboardingRootContainerIdentifier = "harness.onboarding.container"
    static let onboardingCardIdentifier = "harness.onboarding.card"
    static let measurementPreflightInVehicleIdentifier = "harness.measurementStart.inVehicle"
    static let measurementPreflightEvaluateIdentifier = "harness.measurementStart.evaluate"
    static let measurementPreflightOutputIdentifier = "harness.measurementStart.output"
    static let measurementRunStartIdentifier = "harness.measurementRun.start"
    static let measurementRunHeaderIdentifier = "harness.measurementRun.header"
    static let measurementRunDetailIdentifier = "harness.measurementRun.detail"
    static let measurementRunProgressIdentifier = "harness.measurementRun.progress"
    static let measurementRunResultsIdentifier = "harness.measurementRun.results"
    static let measurementHistoryTitleIdentifier = "harness.measurementHistory.title"
    static let measurementHistoryMetricsIdentifier = "harness.measurementHistory.metrics"
    static let measurementHistoryRunsIdentifier = "harness.measurementHistory.runs"
    static let measurementHistorySelectedDetailIdentifier = "harness.measurementHistory.selectedDetail"
    static let measurementHistorySyncIdentifier = "harness.measurementHistory.sync"
    static let measurementHistoryRefreshIdentifier = "harness.measurementHistory.refresh"
    static let mapHomeMapContainerIdentifier = "harness.mapHome.mapContainer"
    static let mapHomeRenderStateIdentifier = "harness.mapHome.renderState"

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
    private let measurementResultReadModelUseCase = MeasurementResultReadModelUseCase()
    private let mapHomeViewController = MapHomeViewController()
    private let mapHomeMapInteractionController = MapHomeMapInteractionController(minHexGridZoom: 0.0)
    private let mapHomeFeatureViewController = MapHomeFeatureViewController(
        maxPoints: 100,
        coarseGridDegrees: 0.08,
        fineGridDegrees: 0.03,
        fineGridZoomThreshold: 12.0
    )
    private lazy var onboardingViewModel = OnboardingProfileViewModel(
        validationUseCase: onboardingValidationUseCase,
        persistenceUseCase: onboardingPersistenceUseCase
    )
    private lazy var settingsViewModel = SettingsProfileViewModel(
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
    private let settingsModeControl = UISegmentedControl(items: ["Testing", "FCC Challenge"])
    private let settingsSubmitButton = UIButton(type: .system)
    private let measurementPreflightInVehicleSwitch = UISwitch()
    private let measurementPreflightOutputLabel = UILabel()
    private let pendingSyncSummaryLabel = UILabel()
    private let pendingSyncDetailLabel = UILabel()
    private let measurementRunHeaderLabel = UILabel()
    private let measurementRunDetailLabel = UILabel()
    private let measurementRunProgressView = UIProgressView(progressViewStyle: .default)
    private let measurementRunResultsLabel = InsetLabel()
    private let measurementRunPrimaryButton = UIButton(type: .system)
    private let measurementHistoryTitleLabel = UILabel()
    private let measurementHistoryMetricsLabel = InsetLabel()
    private let measurementHistoryRunsScrollView = UIScrollView()
    private let measurementHistoryRunsStack = UIStackView()
    private let measurementHistorySelectedDetailLabel = InsetLabel()
    private let measurementHistorySyncLabel = InsetLabel()
    private let mapHomeRenderStateLabel = UILabel()
#if canImport(MapboxMaps)
    private var mapHomeMapboxEventCancelables: [AnyCancelable] = []
#endif
    private struct HistorySnapshotEntry {
        let timestampMs: Double
        let latency: String
        let download: String
        let upload: String
        let uploaded: String
        let detail: String
        let latitude: Double?
        let longitude: Double?
    }
    private var historyPendingMeasurements: Int? = nil
    private var historyPendingSubmissions: Int? = nil
    private var selectedHistoryTimestampMs: Double? = nil
    private var measurementRunCachedLatencySummary = "--"
    private var measurementRunCachedDownloadSummary = "--"
    private var measurementRunCachedUploadSummary = "--"
    private var measurementRunCachedUploadedSummary = "In progress"
    private var measurementRunCachedCompletionSummary = "Measurement in progress."
    private var measurementRunCachedCenterLatitude: Double? = nil
    private var measurementRunCachedCenterLongitude: Double? = nil
    private let measurementRunFlowQueue = DispatchQueue(label: "cellwatch.measurementRunFlow.queue")
    private var measurementRunFlowScheduledSteps: Int = 0
    private var measurementRunFlowFinalizing: Bool = false
    private let measurementRunFlowStepDelay: TimeInterval = 1.2
    private let measurementNetworkPathProbe = IosMeasurementNetworkPathProbe()
    private let locationPermissionManager = CLLocationManager()
    private let pendingSyncHarness = IosPendingSyncHarness()
    private var onboardingTopConstraint: NSLayoutConstraint?
    private var onboardingKeyboardObservers: [NSObjectProtocol] = []
    private var onboardingKeyboardHeight: CGFloat = 0
#if canImport(MapboxMaps)
    private var mapHomeMapView: MapView?
    private var mapHomePointAnnotationManager: PointAnnotationManager?
    private var mapHomeHexAnnotationManager: PointAnnotationManager?
#endif
    private let displayMode: DisplayMode
    private let onboardingUiImplementation: OnboardingUiImplementation
    private let postSubmitMode: DisplayMode?
    private let postSubmitAutoStartMeasurement: Bool
    private let autoStartMeasurementAfterPreflight: Bool
    private let autoStartMeasurementRunOnAppear: Bool
    private var didAutoStartMeasurementRun = false

    init(
        displayMode: DisplayMode = .fullHarness,
        onboardingUiImplementation: OnboardingUiImplementation = .swiftui,
        postSubmitMode: DisplayMode? = nil,
        postSubmitAutoStartMeasurement: Bool = false,
        autoStartMeasurementAfterPreflight: Bool = false,
        autoStartMeasurementRunOnAppear: Bool = false
    ) {
        self.displayMode = displayMode
        self.onboardingUiImplementation = onboardingUiImplementation
        self.postSubmitMode = postSubmitMode
        self.postSubmitAutoStartMeasurement = postSubmitAutoStartMeasurement
        self.autoStartMeasurementAfterPreflight = autoStartMeasurementAfterPreflight
        self.autoStartMeasurementRunOnAppear = autoStartMeasurementRunOnAppear
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        self.displayMode = .fullHarness
        self.onboardingUiImplementation = .swiftui
        self.postSubmitMode = nil
        self.postSubmitAutoStartMeasurement = false
        self.autoStartMeasurementAfterPreflight = false
        self.autoStartMeasurementRunOnAppear = false
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
        if onboardingPersistenceUseCase.loadProfile()?.onboardingComplete != true {
            clearPersistedHistorySnapshots()
        }
        view.backgroundColor = .systemGroupedBackground
        buildUi()
        configureNavigationForDisplayMode()
        if displayMode == .onboardingFlow {
            loadPersistedOnboardingProfile()
            applyOnboardingUiPrefillFromEnvironment()
        } else if displayMode == .settingsProfileFlow {
            loadPersistedSettingsProfile()
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        requestLocationPermissionIfNeeded()
        if displayMode == .measurementRunFlow &&
            autoStartMeasurementRunOnAppear &&
            !didAutoStartMeasurementRun {
            didAutoStartMeasurementRun = true
            DispatchQueue.main.async { [weak self] in
                self?.runPhase3Sequence()
            }
        }
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
        if displayMode == .settingsProfileFlow {
            buildSettingsProfileFlowUi()
            return
        }
        if displayMode == .mapHome {
            buildMapHomeUi()
            return
        }
        if displayMode == .mvpMenu {
            buildMvpMenuUi()
            return
        }
        if displayMode == .measurementStartFlow {
            buildMeasurementStartFlowUi()
            return
        }
        if displayMode == .pendingSyncFlow {
            buildPendingSyncFlowUi()
            return
        }
        if displayMode == .measurementRunFlow {
            buildMeasurementRunFlowUi()
            return
        }
        if displayMode == .measurementHistoryFlow {
            buildMeasurementHistoryFlowUi()
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

        let pendingCountsButton = UIButton(type: .system)
        pendingCountsButton.setTitle("Show Pending Sync Counts", for: .normal)
        pendingCountsButton.accessibilityIdentifier = Self.pendingSyncCountsButtonIdentifier
        applyButtonStyle(pendingCountsButton, role: .secondary)
        pendingCountsButton.addTarget(self, action: #selector(runPendingSyncCounts), for: .touchUpInside)
        pendingCountsButton.translatesAutoresizingMaskIntoConstraints = false

        let retryPendingButton = UIButton(type: .system)
        retryPendingButton.setTitle("Retry Pending Sync", for: .normal)
        retryPendingButton.accessibilityIdentifier = Self.retryPendingSyncButtonIdentifier
        applyButtonStyle(retryPendingButton, role: .secondary)
        retryPendingButton.addTarget(self, action: #selector(runRetryPendingSync), for: .touchUpInside)
        retryPendingButton.translatesAutoresizingMaskIntoConstraints = false

        pendingSyncSummaryLabel.text = "Pending sync status: not checked."
        pendingSyncSummaryLabel.textColor = UIColor(red: 0.23, green: 0.37, blue: 0.47, alpha: 1)
        pendingSyncSummaryLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        pendingSyncSummaryLabel.numberOfLines = 0
        pendingSyncSummaryLabel.accessibilityIdentifier = Self.pendingSyncSummaryIdentifier
        pendingSyncSummaryLabel.translatesAutoresizingMaskIntoConstraints = false
        pendingSyncSummaryLabel.backgroundColor = UIColor(red: 0.95, green: 0.98, blue: 0.96, alpha: 1)
        pendingSyncSummaryLabel.layer.cornerRadius = 10
        pendingSyncSummaryLabel.layer.masksToBounds = true
        pendingSyncSummaryLabel.layoutMargins = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)

        let pendingSyncSummaryContainer = UIView()
        pendingSyncSummaryContainer.translatesAutoresizingMaskIntoConstraints = false
        pendingSyncSummaryContainer.addSubview(pendingSyncSummaryLabel)
        NSLayoutConstraint.activate([
            pendingSyncSummaryLabel.topAnchor.constraint(equalTo: pendingSyncSummaryContainer.topAnchor),
            pendingSyncSummaryLabel.leadingAnchor.constraint(equalTo: pendingSyncSummaryContainer.leadingAnchor),
            pendingSyncSummaryLabel.trailingAnchor.constraint(equalTo: pendingSyncSummaryContainer.trailingAnchor),
            pendingSyncSummaryLabel.bottomAnchor.constraint(equalTo: pendingSyncSummaryContainer.bottomAnchor)
        ])

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
            ackLabel.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
            ackLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)
        }
        onboardingAckSwitch.setContentCompressionResistancePriority(.required, for: .horizontal)
        onboardingAckSwitch.setContentHuggingPriority(.required, for: .horizontal)
        ackRow.axis = .horizontal
        ackRow.spacing = 12
        ackRow.alignment = .top
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
            pendingCountsButton,
            retryPendingButton,
            pendingSyncSummaryContainer,
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

    private func configureNavigationForDisplayMode() {
        guard navigationController != nil else { return }
        switch displayMode {
        case .mapHome, .mvpMenu, .fullHarness:
            navigationItem.leftBarButtonItem = nil
        default:
            // For direct mode launches (not pushed from map), provide a deterministic escape hatch.
            if navigationController?.viewControllers.first === self {
                navigationItem.leftBarButtonItem = UIBarButtonItem(
                    title: "Map",
                    style: .plain,
                    target: self,
                    action: #selector(returnToMapHome)
                )
            }
        }
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
        ackRow.alignment = .top
        ackRow.spacing = 12
        ackRow.translatesAutoresizingMaskIntoConstraints = false
        ackLabel.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        ackLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)
        onboardingAckSwitch.setContentCompressionResistancePriority(.required, for: .horizontal)
        onboardingAckSwitch.setContentHuggingPriority(.required, for: .horizontal)

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

    private func buildSettingsProfileFlowUi() {
        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)
        navigationItem.title = "Profile Settings"

        let title = UILabel()
        title.text = "Profile Settings"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label

        let subtitle = UILabel()
        subtitle.text = "Update profile details and collection mode."
        subtitle.font = UIFont.preferredFont(forTextStyle: .body)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0

        settingsModeControl.selectedSegmentIndex = 0
        settingsModeControl.accessibilityIdentifier = Self.settingsModeControlIdentifier
        settingsModeControl.addTarget(self, action: #selector(settingsModeChanged), for: .valueChanged)

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
        onboardingAckSwitch.addTarget(self, action: #selector(settingsAckChanged), for: .valueChanged)
        let ackLabel = UILabel()
        ackLabel.text = "I acknowledge FCC challenge sharing terms."
        ackLabel.font = UIFont.preferredFont(forTextStyle: .body)
        ackLabel.numberOfLines = 0
        ackLabel.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        ackLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)
        onboardingAckSwitch.setContentCompressionResistancePriority(.required, for: .horizontal)
        onboardingAckSwitch.setContentHuggingPriority(.required, for: .horizontal)
        onboardingAckSwitch.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            onboardingAckSwitch.widthAnchor.constraint(equalToConstant: 51),
            onboardingAckSwitch.heightAnchor.constraint(equalToConstant: 31),
        ])
        let ackRow = UIStackView(arrangedSubviews: [ackLabel, onboardingAckSwitch])
        ackRow.axis = .horizontal
        ackRow.alignment = .top
        ackRow.spacing = 12

        settingsSubmitButton.setTitle("Save Settings", for: .normal)
        settingsSubmitButton.accessibilityIdentifier = Self.settingsSubmitButtonIdentifier
        applyButtonStyle(settingsSubmitButton, role: .primary)
        applyOnboardingActionButtonStyle(settingsSubmitButton)
        settingsSubmitButton.addTarget(self, action: #selector(submitSettings), for: .touchUpInside)

        let identityHeader = UILabel()
        identityHeader.text = "App Identity"
        identityHeader.font = UIFont.preferredFont(forTextStyle: .headline)
        identityHeader.textColor = .label

        let deviceIdLabel = UILabel()
        deviceIdLabel.text = "Device ID"
        deviceIdLabel.font = UIFont.preferredFont(forTextStyle: .caption1)
        deviceIdLabel.textColor = .secondaryLabel

        let deviceIdValue = InsetLabel()
        deviceIdValue.text = resolveSettingsDeviceId()
        deviceIdValue.numberOfLines = 0
        deviceIdValue.font = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)
        deviceIdValue.textColor = UIColor(red: 0.18, green: 0.29, blue: 0.38, alpha: 1.0)
        deviceIdValue.backgroundColor = .white
        deviceIdValue.layer.cornerRadius = 10
        deviceIdValue.layer.masksToBounds = true
        deviceIdValue.layer.borderColor = UIColor(red: 0.78, green: 0.86, blue: 0.93, alpha: 1.0).cgColor
        deviceIdValue.layer.borderWidth = 1
        deviceIdValue.textInsets = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
        deviceIdValue.accessibilityIdentifier = Self.settingsDeviceIdValueIdentifier

        let copyDeviceButton = UIButton(type: .system)
        copyDeviceButton.setTitle("Copy Device ID", for: .normal)
        copyDeviceButton.accessibilityIdentifier = Self.settingsCopyDeviceIdButtonIdentifier
        applyButtonStyle(copyDeviceButton, role: .secondary)
        copyDeviceButton.addTarget(self, action: #selector(copySettingsDeviceId), for: .touchUpInside)

        let appVersionLabel = UILabel()
        appVersionLabel.text = "App Version"
        appVersionLabel.font = UIFont.preferredFont(forTextStyle: .caption1)
        appVersionLabel.textColor = .secondaryLabel

        let appVersionValue = InsetLabel()
        appVersionValue.text = resolveSettingsAppVersion()
        appVersionValue.numberOfLines = 0
        appVersionValue.font = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)
        appVersionValue.textColor = UIColor(red: 0.18, green: 0.29, blue: 0.38, alpha: 1.0)
        appVersionValue.backgroundColor = .white
        appVersionValue.layer.cornerRadius = 10
        appVersionValue.layer.masksToBounds = true
        appVersionValue.layer.borderColor = UIColor(red: 0.78, green: 0.86, blue: 0.93, alpha: 1.0).cgColor
        appVersionValue.layer.borderWidth = 1
        appVersionValue.textInsets = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
        appVersionValue.accessibilityIdentifier = Self.settingsAppVersionValueIdentifier

        let copyVersionButton = UIButton(type: .system)
        copyVersionButton.setTitle("Copy App Version", for: .normal)
        copyVersionButton.accessibilityIdentifier = Self.settingsCopyAppVersionButtonIdentifier
        applyButtonStyle(copyVersionButton, role: .secondary)
        copyVersionButton.addTarget(self, action: #selector(copySettingsAppVersion), for: .touchUpInside)

        onboardingFeedbackLabel.numberOfLines = 0
        onboardingFeedbackLabel.font = UIFont.preferredFont(forTextStyle: .body)
        onboardingFeedbackLabel.accessibilityIdentifier = Self.onboardingFeedbackLabelIdentifier

        let cardStack = UIStackView(arrangedSubviews: [
            title,
            subtitle,
            settingsModeControl,
            onboardingNameField,
            onboardingPhoneField,
            onboardingEmailField,
            ackRow,
            settingsSubmitButton,
            identityHeader,
            deviceIdLabel,
            deviceIdValue,
            copyDeviceButton,
            appVersionLabel,
            appVersionValue,
            copyVersionButton,
            onboardingFeedbackLabel
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

        onboardingNameField.addTarget(self, action: #selector(settingsFieldChanged), for: .editingChanged)
        onboardingPhoneField.addTarget(self, action: #selector(settingsFieldChanged), for: .editingChanged)
        onboardingEmailField.addTarget(self, action: #selector(settingsFieldChanged), for: .editingChanged)
        onboardingEmailField.addTarget(self, action: #selector(submitSettingsFromReturnKey), for: .editingDidEndOnExit)
    }

    private func buildMapHomeUi() {
        let historyEntries = loadHistoryEntries()
        let onboardingComplete = onboardingPersistenceUseCase.loadProfile()?.onboardingComplete == true
        let pendingKnown = historyPendingMeasurements != nil && historyPendingSubmissions != nil
        let mapHomeState = mapHomeViewController.present(
            input: MapHomeInput(
                onboardingComplete: onboardingComplete,
                recentRunCount: Int32(historyEntries.count),
                pendingCountsKnown: pendingKnown,
                pendingMeasurements: Int32(historyPendingMeasurements ?? 0),
                pendingSubmissions: Int32(historyPendingSubmissions ?? 0)
            )
        )

        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)
        navigationItem.title = mapHomeState.title

        let mapSurfaceContainer = UIView()
        mapSurfaceContainer.translatesAutoresizingMaskIntoConstraints = false
        mapSurfaceContainer.backgroundColor = UIColor(red: 0.91, green: 0.94, blue: 0.98, alpha: 1.0)
        mapSurfaceContainer.accessibilityIdentifier = Self.mapHomeMapContainerIdentifier

        mapHomeRenderStateLabel.text = "INIT"
        mapHomeRenderStateLabel.accessibilityIdentifier = Self.mapHomeRenderStateIdentifier
        mapHomeRenderStateLabel.font = .systemFont(ofSize: 1)
        mapHomeRenderStateLabel.textColor = .clear
        mapHomeRenderStateLabel.backgroundColor = .clear
        mapHomeRenderStateLabel.isAccessibilityElement = true
        mapHomeRenderStateLabel.translatesAutoresizingMaskIntoConstraints = false
        mapSurfaceContainer.addSubview(mapHomeRenderStateLabel)
        NSLayoutConstraint.activate([
            mapHomeRenderStateLabel.topAnchor.constraint(equalTo: mapSurfaceContainer.topAnchor),
            mapHomeRenderStateLabel.leadingAnchor.constraint(equalTo: mapSurfaceContainer.leadingAnchor),
            mapHomeRenderStateLabel.widthAnchor.constraint(equalToConstant: 1),
            mapHomeRenderStateLabel.heightAnchor.constraint(equalToConstant: 1)
        ])

#if canImport(MapboxMaps)
        mapHomeMapView?.removeFromSuperview()
        mapHomeMapView = nil
        mapHomePointAnnotationManager = nil
        mapHomeHexAnnotationManager = nil
        mapHomeMapboxEventCancelables.removeAll()
        let token = RuntimeConfigSource.mapboxAccessToken()?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let token, !token.isEmpty {
            MapboxOptions.accessToken = token
            let mapView = MapView(frame: .zero, mapInitOptions: MapInitOptions())
            mapView.translatesAutoresizingMaskIntoConstraints = false
            mapSurfaceContainer.addSubview(mapView)
            NSLayoutConstraint.activate([
                mapView.topAnchor.constraint(equalTo: mapSurfaceContainer.topAnchor),
                mapView.leadingAnchor.constraint(equalTo: mapSurfaceContainer.leadingAnchor),
                mapView.trailingAnchor.constraint(equalTo: mapSurfaceContainer.trailingAnchor),
                mapView.bottomAnchor.constraint(equalTo: mapSurfaceContainer.bottomAnchor)
            ])
            mapHomeMapView = mapView
            mapHomeRenderStateLabel.text = "MAP_VIEW_CREATED"
            mapHomeMapboxEventCancelables.append(
                mapView.mapboxMap.onStyleLoaded.observeNext { [weak self] _ in
                    self?.mapHomeRenderStateLabel.text = "STYLE_LOADED"
                    guard let self else { return }
                    let state = self.mapHomeMapInteractionController.currentState()
                    self.applyInitialMapHomeCamera(historyEntries: historyEntries, interactionState: state)
                }
            )
            mapHomeMapboxEventCancelables.append(
                mapView.mapboxMap.onMapIdle.observeNext { [weak self] _ in
                    self?.mapHomeRenderStateLabel.text = "MAP_IDLE"
                }
            )
            mapHomeMapboxEventCancelables.append(
                mapView.mapboxMap.onMapLoadingError.observeNext { [weak self] _ in
                    self?.mapHomeRenderStateLabel.text = "MAP_ERROR"
                }
            )
        } else {
            let missingTokenLabel = UILabel()
            missingTokenLabel.text = "Mapbox token missing. Set MAPBOX_ACCESS_TOKEN in cellwatch.properties."
            mapHomeRenderStateLabel.text = "TOKEN_MISSING"
            missingTokenLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
            missingTokenLabel.textColor = UIColor(red: 0.36, green: 0.15, blue: 0.15, alpha: 1.0)
            missingTokenLabel.numberOfLines = 0
            missingTokenLabel.textAlignment = .center
            missingTokenLabel.backgroundColor = UIColor(red: 1.0, green: 0.95, blue: 0.85, alpha: 0.96)
            missingTokenLabel.layer.cornerRadius = 10
            missingTokenLabel.layer.masksToBounds = true
            missingTokenLabel.translatesAutoresizingMaskIntoConstraints = false
            mapSurfaceContainer.addSubview(missingTokenLabel)
            NSLayoutConstraint.activate([
                missingTokenLabel.centerXAnchor.constraint(equalTo: mapSurfaceContainer.centerXAnchor),
                missingTokenLabel.centerYAnchor.constraint(equalTo: mapSurfaceContainer.centerYAnchor),
                missingTokenLabel.leadingAnchor.constraint(equalTo: mapSurfaceContainer.leadingAnchor, constant: 20),
                missingTokenLabel.trailingAnchor.constraint(equalTo: mapSurfaceContainer.trailingAnchor, constant: -20)
            ])
        }
#else
        let unavailableLabel = UILabel()
        unavailableLabel.text = "MapboxMaps package not linked yet."
        unavailableLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        unavailableLabel.textColor = UIColor.white
        unavailableLabel.numberOfLines = 0
        unavailableLabel.textAlignment = .center
        unavailableLabel.translatesAutoresizingMaskIntoConstraints = false
        mapSurfaceContainer.addSubview(unavailableLabel)
        NSLayoutConstraint.activate([
            unavailableLabel.centerXAnchor.constraint(equalTo: mapSurfaceContainer.centerXAnchor),
            unavailableLabel.centerYAnchor.constraint(equalTo: mapSurfaceContainer.centerYAnchor),
            unavailableLabel.leadingAnchor.constraint(greaterThanOrEqualTo: mapSurfaceContainer.leadingAnchor, constant: 20),
            unavailableLabel.trailingAnchor.constraint(lessThanOrEqualTo: mapSurfaceContainer.trailingAnchor, constant: -20)
        ])
        mapHomeRenderStateLabel.text = "MAPBOX_PACKAGE_MISSING"
#endif

        _ = mapHomeMapInteractionController.reset()
        let initialInteractionState = mapHomeMapInteractionController.currentState()
        let initialFeatureState = mapHomeFeatureViewController
            .loadMeasurements(values: mapHomeLocationSnapshots(from: historyEntries))
        _ = mapHomeFeatureViewController.onZoomChanged(zoomLevel: initialInteractionState.zoomLevel)

        let topTitle = UILabel()
        topTitle.text = mapHomeState.title
        topTitle.font = UIFont.preferredFont(forTextStyle: .title2)
        topTitle.textColor = .label
        let topSubtitle = UILabel()
        topSubtitle.text = mapHomeState.subtitle
        topSubtitle.font = UIFont.preferredFont(forTextStyle: .body)
        topSubtitle.textColor = .secondaryLabel
        topSubtitle.numberOfLines = 0
        let topCard = UIStackView(arrangedSubviews: [topTitle, topSubtitle])
        topCard.axis = .vertical
        topCard.spacing = 8
        topCard.translatesAutoresizingMaskIntoConstraints = false

        let topCardContainer = UIView()
        topCardContainer.backgroundColor = UIColor(white: 0.98, alpha: 0.96)
        topCardContainer.layer.cornerRadius = 14
        topCardContainer.translatesAutoresizingMaskIntoConstraints = false
        topCardContainer.addSubview(topCard)

        let overlaySummaryLabel = InsetLabel()
        overlaySummaryLabel.textInsets = UIEdgeInsets(top: 8, left: 10, bottom: 8, right: 10)
        overlaySummaryLabel.numberOfLines = 0
        overlaySummaryLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        overlaySummaryLabel.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        overlaySummaryLabel.backgroundColor = UIColor(red: 0.95, green: 0.98, blue: 0.96, alpha: 0.97)
        overlaySummaryLabel.layer.cornerRadius = 12
        overlaySummaryLabel.layer.masksToBounds = true
        overlaySummaryLabel.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        overlaySummaryLabel.layer.borderWidth = 1
        if initialFeatureState.hasAnyLocationData {
            overlaySummaryLabel.text =
                "Map overlay: \(initialInteractionState.overlayMode == .hexGrid ? "Hex grid" : "Points")\n\(initialFeatureState.summary)"
        } else {
            overlaySummaryLabel.text = "Map overlay: \(initialInteractionState.overlayMode == .hexGrid ? "Hex grid" : "Points")"
        }

        let overlayToggleButton = UIButton(type: .system)
        overlayToggleButton.setTitle(
            initialInteractionState.overlayMode == .hexGrid ? "Switch to Points" : "Switch to Hex Grid",
            for: .normal
        )
        applyButtonStyle(overlayToggleButton, role: .secondary)
        overlayToggleButton.addAction(UIAction { [weak self, weak overlaySummaryLabel, weak overlayToggleButton] _ in
            guard let self else { return }
            let current = self.mapHomeMapInteractionController.currentState()
            let nextMode: MapHomeOverlayMode = current.overlayMode == .hexGrid ? .points : .hexGrid
            let next = self.mapHomeMapInteractionController.setPreferredOverlayMode(mode: nextMode)
            let featureState = self.renderMapHomeFeatures(
                historyEntries: historyEntries,
                interactionState: next
            )
            if featureState.hasAnyLocationData {
                overlaySummaryLabel?.text =
                    "Map overlay: \(next.overlayMode == .hexGrid ? "Hex grid" : "Points")\n\(featureState.summary)"
            } else {
                overlaySummaryLabel?.text = "Map overlay: \(next.overlayMode == .hexGrid ? "Hex grid" : "Points")"
            }
            overlayToggleButton?.setTitle(
                next.overlayMode == .hexGrid ? "Switch to Points" : "Switch to Hex Grid",
                for: .normal
            )
        }, for: .touchUpInside)

        let profileButton = UIButton(type: .system)
        profileButton.setTitle("Settings", for: .normal)
        applyButtonStyle(profileButton, role: .secondary)
        profileButton.addTarget(self, action: #selector(openSettingsFromMvpMenu), for: .touchUpInside)

        let measureButton = UIButton(type: .system)
        measureButton.setTitle("Measure", for: .normal)
        applyButtonStyle(measureButton, role: .primary)
        measureButton.addTarget(self, action: #selector(openMeasureFromMvpMenu), for: .touchUpInside)

        let historyButton = UIButton(type: .system)
        historyButton.setTitle("History & Sync Status", for: .normal)
        applyButtonStyle(historyButton, role: .secondary)
        historyButton.addTarget(self, action: #selector(openHistoryFromMvpMenu), for: .touchUpInside)

        let syncSummaryLabel = InsetLabel()
        syncSummaryLabel.text = mapHomeState.syncSummary
        syncSummaryLabel.numberOfLines = 0
        syncSummaryLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        syncSummaryLabel.translatesAutoresizingMaskIntoConstraints = false
        syncSummaryLabel.textInsets = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
        syncSummaryLabel.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        syncSummaryLabel.backgroundColor = UIColor(red: 0.95, green: 0.98, blue: 0.96, alpha: 0.97)
        syncSummaryLabel.layer.cornerRadius = 12
        syncSummaryLabel.layer.masksToBounds = true
        syncSummaryLabel.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        syncSummaryLabel.layer.borderWidth = 1

        let mvpStatusLabel = InsetLabel()
        mvpStatusLabel.text = mapHomeState.statusText
        mvpStatusLabel.numberOfLines = 0
        mvpStatusLabel.font = UIFont.preferredFont(forTextStyle: .body)
        mvpStatusLabel.translatesAutoresizingMaskIntoConstraints = false
        mvpStatusLabel.accessibilityIdentifier = Self.statusLabelIdentifier
        mvpStatusLabel.textInsets = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
        mvpStatusLabel.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        mvpStatusLabel.backgroundColor = UIColor(white: 1.0, alpha: 0.96)
        mvpStatusLabel.layer.cornerRadius = 12
        mvpStatusLabel.layer.masksToBounds = true
        mvpStatusLabel.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        mvpStatusLabel.layer.borderWidth = 1

        let bottomStack = UIStackView(
            arrangedSubviews: [
                overlaySummaryLabel,
                overlayToggleButton,
                profileButton,
                measureButton,
                historyButton,
                syncSummaryLabel,
                mvpStatusLabel
            ]
        )
        bottomStack.axis = .vertical
        bottomStack.spacing = 10
        bottomStack.translatesAutoresizingMaskIntoConstraints = false

        let bottomCardContainer = UIView()
        bottomCardContainer.backgroundColor = UIColor(white: 0.98, alpha: 0.96)
        bottomCardContainer.layer.cornerRadius = 14
        bottomCardContainer.translatesAutoresizingMaskIntoConstraints = false
        bottomCardContainer.addSubview(bottomStack)

        view.addSubview(mapSurfaceContainer)
        view.addSubview(topCardContainer)
        view.addSubview(bottomCardContainer)
        NSLayoutConstraint.activate([
            mapSurfaceContainer.topAnchor.constraint(equalTo: view.topAnchor),
            mapSurfaceContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            mapSurfaceContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            mapSurfaceContainer.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            topCardContainer.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 10),
            topCardContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            topCardContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            topCard.topAnchor.constraint(equalTo: topCardContainer.topAnchor, constant: 12),
            topCard.leadingAnchor.constraint(equalTo: topCardContainer.leadingAnchor, constant: 12),
            topCard.trailingAnchor.constraint(equalTo: topCardContainer.trailingAnchor, constant: -12),
            topCard.bottomAnchor.constraint(equalTo: topCardContainer.bottomAnchor, constant: -12),

            bottomCardContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            bottomCardContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            bottomCardContainer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -10),
            bottomStack.topAnchor.constraint(equalTo: bottomCardContainer.topAnchor, constant: 12),
            bottomStack.leadingAnchor.constraint(equalTo: bottomCardContainer.leadingAnchor, constant: 12),
            bottomStack.trailingAnchor.constraint(equalTo: bottomCardContainer.trailingAnchor, constant: -12),
            bottomStack.bottomAnchor.constraint(equalTo: bottomCardContainer.bottomAnchor, constant: -12)
        ])
        _ = renderMapHomeFeatures(historyEntries: historyEntries, interactionState: initialInteractionState)
        applyInitialMapHomeCamera(historyEntries: historyEntries, interactionState: initialInteractionState)
    }

    private func buildMvpMenuUi() {
        let historyEntries = loadHistoryEntries()
        let onboardingComplete = onboardingPersistenceUseCase.loadProfile()?.onboardingComplete == true
        let pendingKnown = historyPendingMeasurements != nil && historyPendingSubmissions != nil
        let mapHomeState = mapHomeViewController.present(
            input: MapHomeInput(
                onboardingComplete: onboardingComplete,
                recentRunCount: Int32(historyEntries.count),
                pendingCountsKnown: pendingKnown,
                pendingMeasurements: Int32(historyPendingMeasurements ?? 0),
                pendingSubmissions: Int32(historyPendingSubmissions ?? 0)
            )
        )

        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)
        navigationItem.title = mapHomeState.title

        let title = UILabel()
        title.text = mapHomeState.title
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label

        let subtitle = UILabel()
        subtitle.text = mapHomeState.subtitle
        subtitle.font = UIFont.preferredFont(forTextStyle: .body)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0

        let mapPlaceholder = UILabel()
        mapPlaceholder.text = mapHomeState.mapPanelTitle
        mapPlaceholder.font = UIFont.preferredFont(forTextStyle: .footnote)
        mapPlaceholder.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        mapPlaceholder.numberOfLines = 0
        mapPlaceholder.textAlignment = .center
        mapPlaceholder.backgroundColor = .clear

        let mapSurfaceContainer = UIView()
        mapSurfaceContainer.translatesAutoresizingMaskIntoConstraints = false
        mapSurfaceContainer.backgroundColor = UIColor(red: 0.91, green: 0.94, blue: 0.98, alpha: 1.0)
        mapSurfaceContainer.layer.cornerRadius = 12
        mapSurfaceContainer.layer.masksToBounds = true
        mapSurfaceContainer.accessibilityIdentifier = Self.mapHomeMapContainerIdentifier
        mapSurfaceContainer.heightAnchor.constraint(equalToConstant: 220).isActive = true

        mapHomeRenderStateLabel.text = "INIT"
        mapHomeRenderStateLabel.accessibilityIdentifier = Self.mapHomeRenderStateIdentifier
        mapHomeRenderStateLabel.font = .systemFont(ofSize: 1)
        mapHomeRenderStateLabel.textColor = .clear
        mapHomeRenderStateLabel.backgroundColor = .clear
        mapHomeRenderStateLabel.isAccessibilityElement = true
        mapHomeRenderStateLabel.translatesAutoresizingMaskIntoConstraints = false
        mapSurfaceContainer.addSubview(mapHomeRenderStateLabel)
        NSLayoutConstraint.activate([
            mapHomeRenderStateLabel.topAnchor.constraint(equalTo: mapSurfaceContainer.topAnchor),
            mapHomeRenderStateLabel.leadingAnchor.constraint(equalTo: mapSurfaceContainer.leadingAnchor),
            mapHomeRenderStateLabel.widthAnchor.constraint(equalToConstant: 1),
            mapHomeRenderStateLabel.heightAnchor.constraint(equalToConstant: 1)
        ])

#if canImport(MapboxMaps)
        mapHomeMapView?.removeFromSuperview()
        mapHomeMapView = nil
        mapHomePointAnnotationManager = nil
        mapHomeHexAnnotationManager = nil
        mapHomeMapboxEventCancelables.removeAll()
        let token = RuntimeConfigSource.mapboxAccessToken()?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let token, !token.isEmpty {
            MapboxOptions.accessToken = token
            let mapView = MapView(frame: .zero, mapInitOptions: MapInitOptions())
            mapView.translatesAutoresizingMaskIntoConstraints = false
            mapSurfaceContainer.addSubview(mapView)
            NSLayoutConstraint.activate([
                mapView.topAnchor.constraint(equalTo: mapSurfaceContainer.topAnchor),
                mapView.leadingAnchor.constraint(equalTo: mapSurfaceContainer.leadingAnchor),
                mapView.trailingAnchor.constraint(equalTo: mapSurfaceContainer.trailingAnchor),
                mapView.bottomAnchor.constraint(equalTo: mapSurfaceContainer.bottomAnchor)
            ])
            mapHomeMapView = mapView
            mapHomeRenderStateLabel.text = "MAP_VIEW_CREATED"
            mapHomeMapboxEventCancelables.append(
                mapView.mapboxMap.onStyleLoaded.observeNext { [weak self] _ in
                    self?.mapHomeRenderStateLabel.text = "STYLE_LOADED"
                    guard let self else { return }
                    let state = self.mapHomeMapInteractionController.currentState()
                    self.applyInitialMapHomeCamera(historyEntries: historyEntries, interactionState: state)
                }
            )
            mapHomeMapboxEventCancelables.append(
                mapView.mapboxMap.onMapIdle.observeNext { [weak self] _ in
                    self?.mapHomeRenderStateLabel.text = "MAP_IDLE"
                }
            )
            mapHomeMapboxEventCancelables.append(
                mapView.mapboxMap.onMapLoadingError.observeNext { [weak self] _ in
                    self?.mapHomeRenderStateLabel.text = "MAP_ERROR"
                }
            )
        } else {
            let missingTokenLabel = UILabel()
            missingTokenLabel.text = "Mapbox token missing. Set MAPBOX_ACCESS_TOKEN in cellwatch.properties."
            mapHomeRenderStateLabel.text = "TOKEN_MISSING"
            missingTokenLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
            missingTokenLabel.textColor = UIColor(red: 0.36, green: 0.15, blue: 0.15, alpha: 1.0)
            missingTokenLabel.numberOfLines = 0
            missingTokenLabel.textAlignment = .center
            missingTokenLabel.backgroundColor = UIColor(red: 1.0, green: 0.95, blue: 0.85, alpha: 0.96)
            missingTokenLabel.layer.cornerRadius = 10
            missingTokenLabel.layer.masksToBounds = true
            missingTokenLabel.translatesAutoresizingMaskIntoConstraints = false
            mapSurfaceContainer.addSubview(missingTokenLabel)
            NSLayoutConstraint.activate([
                missingTokenLabel.topAnchor.constraint(equalTo: mapSurfaceContainer.topAnchor, constant: 12),
                missingTokenLabel.leadingAnchor.constraint(equalTo: mapSurfaceContainer.leadingAnchor, constant: 12),
                missingTokenLabel.trailingAnchor.constraint(equalTo: mapSurfaceContainer.trailingAnchor, constant: -12),
                missingTokenLabel.bottomAnchor.constraint(equalTo: mapSurfaceContainer.bottomAnchor, constant: -12)
            ])
        }
#else
        let unavailableLabel = UILabel()
        unavailableLabel.text = "MapboxMaps package not linked yet."
        unavailableLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        unavailableLabel.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        unavailableLabel.numberOfLines = 0
        unavailableLabel.textAlignment = .center
        unavailableLabel.translatesAutoresizingMaskIntoConstraints = false
        mapSurfaceContainer.addSubview(unavailableLabel)
        NSLayoutConstraint.activate([
            unavailableLabel.topAnchor.constraint(equalTo: mapSurfaceContainer.topAnchor, constant: 12),
            unavailableLabel.leadingAnchor.constraint(equalTo: mapSurfaceContainer.leadingAnchor, constant: 12),
            unavailableLabel.trailingAnchor.constraint(equalTo: mapSurfaceContainer.trailingAnchor, constant: -12),
            unavailableLabel.bottomAnchor.constraint(equalTo: mapSurfaceContainer.bottomAnchor, constant: -12)
        ])
        mapHomeRenderStateLabel.text = "MAPBOX_PACKAGE_MISSING"
#endif

        _ = mapHomeMapInteractionController.reset()
        let initialInteractionState = mapHomeMapInteractionController.currentState()
        let initialFeatureState = mapHomeFeatureViewController
            .loadMeasurements(values: mapHomeLocationSnapshots(from: historyEntries))
        _ = mapHomeFeatureViewController.onZoomChanged(zoomLevel: initialInteractionState.zoomLevel)
        let overlaySummaryLabel = InsetLabel()
        overlaySummaryLabel.textInsets = UIEdgeInsets(top: 8, left: 10, bottom: 8, right: 10)
        overlaySummaryLabel.numberOfLines = 0
        overlaySummaryLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        overlaySummaryLabel.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        overlaySummaryLabel.backgroundColor = UIColor(red: 0.95, green: 0.98, blue: 0.96, alpha: 1.0)
        overlaySummaryLabel.layer.cornerRadius = 12
        overlaySummaryLabel.layer.masksToBounds = true
        overlaySummaryLabel.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        overlaySummaryLabel.layer.borderWidth = 1
        if initialFeatureState.hasAnyLocationData {
            overlaySummaryLabel.text =
                "Map overlay: \(initialInteractionState.overlayMode == .hexGrid ? "Hex grid" : "Points")\n\(initialFeatureState.summary)"
        } else {
            overlaySummaryLabel.text = "Map overlay: \(initialInteractionState.overlayMode == .hexGrid ? "Hex grid" : "Points")"
        }

        let overlayToggleButton = UIButton(type: .system)
        overlayToggleButton.setTitle(
            initialInteractionState.overlayMode == .hexGrid ? "Switch to Points" : "Switch to Hex Grid",
            for: .normal
        )
        applyButtonStyle(overlayToggleButton, role: .secondary)
        overlayToggleButton.addAction(UIAction { [weak self, weak overlaySummaryLabel, weak overlayToggleButton] _ in
            guard let self else { return }
            let current = self.mapHomeMapInteractionController.currentState()
            let nextMode: MapHomeOverlayMode = current.overlayMode == .hexGrid ? .points : .hexGrid
            let next = self.mapHomeMapInteractionController.setPreferredOverlayMode(mode: nextMode)
            let featureState = self.renderMapHomeFeatures(
                historyEntries: historyEntries,
                interactionState: next
            )
            if featureState.hasAnyLocationData {
                overlaySummaryLabel?.text =
                    "Map overlay: \(next.overlayMode == .hexGrid ? "Hex grid" : "Points")\n\(featureState.summary)"
            } else {
                overlaySummaryLabel?.text = "Map overlay: \(next.overlayMode == .hexGrid ? "Hex grid" : "Points")"
            }
            overlayToggleButton?.setTitle(
                next.overlayMode == .hexGrid ? "Switch to Points" : "Switch to Hex Grid",
                for: .normal
            )
        }, for: .touchUpInside)

        let profileButton = UIButton(type: .system)
        profileButton.setTitle("Settings", for: .normal)
        applyButtonStyle(profileButton, role: .secondary)
        profileButton.addTarget(self, action: #selector(openSettingsFromMvpMenu), for: .touchUpInside)

        let measureButton = UIButton(type: .system)
        measureButton.setTitle("Measure", for: .normal)
        applyButtonStyle(measureButton, role: .primary)
        measureButton.addTarget(self, action: #selector(openMeasureFromMvpMenu), for: .touchUpInside)

        let historyButton = UIButton(type: .system)
        historyButton.setTitle("History & Sync Status", for: .normal)
        applyButtonStyle(historyButton, role: .secondary)
        historyButton.addTarget(self, action: #selector(openHistoryFromMvpMenu), for: .touchUpInside)

        let syncSummaryLabel = InsetLabel()
        syncSummaryLabel.text = mapHomeState.syncSummary
        syncSummaryLabel.numberOfLines = 0
        syncSummaryLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        syncSummaryLabel.translatesAutoresizingMaskIntoConstraints = false
        syncSummaryLabel.textInsets = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
        syncSummaryLabel.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        syncSummaryLabel.backgroundColor = UIColor(red: 0.95, green: 0.98, blue: 0.96, alpha: 1.0)
        syncSummaryLabel.layer.cornerRadius = 12
        syncSummaryLabel.layer.masksToBounds = true
        syncSummaryLabel.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        syncSummaryLabel.layer.borderWidth = 1

        let mvpStatusLabel = InsetLabel()
        mvpStatusLabel.text = mapHomeState.statusText
        mvpStatusLabel.numberOfLines = 0
        mvpStatusLabel.font = UIFont.preferredFont(forTextStyle: .body)
        mvpStatusLabel.translatesAutoresizingMaskIntoConstraints = false
        mvpStatusLabel.accessibilityIdentifier = Self.statusLabelIdentifier
        mvpStatusLabel.textInsets = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
        mvpStatusLabel.textColor = UIColor(red: 0.20, green: 0.31, blue: 0.39, alpha: 1.0)
        mvpStatusLabel.backgroundColor = .white
        mvpStatusLabel.layer.cornerRadius = 12
        mvpStatusLabel.layer.masksToBounds = true
        mvpStatusLabel.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        mvpStatusLabel.layer.borderWidth = 1

        let cardStack = UIStackView(
            arrangedSubviews: [
                title,
                subtitle,
                mapPlaceholder,
                mapSurfaceContainer,
                overlaySummaryLabel,
                overlayToggleButton,
                profileButton,
                measureButton,
                historyButton,
                syncSummaryLabel,
                mvpStatusLabel
            ]
        )
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
        _ = renderMapHomeFeatures(historyEntries: historyEntries, interactionState: initialInteractionState)
        applyInitialMapHomeCamera(historyEntries: historyEntries, interactionState: initialInteractionState)
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
        evaluateButton.setTitle("Start Measurement", for: .normal)
        evaluateButton.accessibilityIdentifier = Self.measurementPreflightEvaluateIdentifier
        applyButtonStyle(evaluateButton, role: .primary)
        evaluateButton.addTarget(self, action: #selector(evaluateMeasurementStartPreflightFromUi), for: .touchUpInside)

        measurementPreflightOutputLabel.text = ""
        measurementPreflightOutputLabel.font = UIFont.preferredFont(forTextStyle: .body)
        measurementPreflightOutputLabel.numberOfLines = 0
        measurementPreflightOutputLabel.accessibilityIdentifier = Self.measurementPreflightOutputIdentifier
        measurementPreflightOutputLabel.textColor = UIColor(red: 0.18, green: 0.45, blue: 0.22, alpha: 1.0)
        measurementPreflightOutputLabel.translatesAutoresizingMaskIntoConstraints = false
        measurementPreflightOutputLabel.isHidden = true

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

    private func buildPendingSyncFlowUi() {
        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)

        let title = UILabel()
        title.text = "Sync Pending Uploads"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label

        let subtitle = UILabel()
        subtitle.text = "Check pending uploads and retry sync."
        subtitle.font = UIFont.preferredFont(forTextStyle: .body)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0

        let countsButton = UIButton(type: .system)
        countsButton.setTitle("Check Pending Uploads", for: .normal)
        countsButton.accessibilityIdentifier = Self.pendingSyncCountsButtonIdentifier
        applyButtonStyle(countsButton, role: .secondary)
        countsButton.addTarget(self, action: #selector(runPendingSyncCounts), for: .touchUpInside)

        let retryButton = UIButton(type: .system)
        retryButton.setTitle("Retry Sync", for: .normal)
        retryButton.accessibilityIdentifier = Self.retryPendingSyncButtonIdentifier
        applyButtonStyle(retryButton, role: .primary)
        retryButton.addTarget(self, action: #selector(runRetryPendingSync), for: .touchUpInside)

        pendingSyncSummaryLabel.text = "Pending sync status: not checked."
        pendingSyncSummaryLabel.textColor = UIColor(red: 0.23, green: 0.37, blue: 0.47, alpha: 1)
        pendingSyncSummaryLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        pendingSyncSummaryLabel.numberOfLines = 0
        pendingSyncSummaryLabel.accessibilityIdentifier = Self.pendingSyncSummaryIdentifier

        pendingSyncDetailLabel.text = "Tap Check Pending Uploads to begin."
        pendingSyncDetailLabel.textColor = UIColor(red: 0.23, green: 0.37, blue: 0.47, alpha: 1)
        pendingSyncDetailLabel.font = UIFont.preferredFont(forTextStyle: .body)
        pendingSyncDetailLabel.numberOfLines = 0
        pendingSyncDetailLabel.accessibilityIdentifier = Self.pendingSyncDetailIdentifier

        let summaryCard = UIView()
        summaryCard.backgroundColor = .white
        summaryCard.layer.cornerRadius = 12
        summaryCard.layer.borderWidth = 1
        summaryCard.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        summaryCard.clipsToBounds = true
        summaryCard.translatesAutoresizingMaskIntoConstraints = false
        pendingSyncSummaryLabel.translatesAutoresizingMaskIntoConstraints = false
        summaryCard.addSubview(pendingSyncSummaryLabel)
        NSLayoutConstraint.activate([
            pendingSyncSummaryLabel.topAnchor.constraint(equalTo: summaryCard.topAnchor, constant: 10),
            pendingSyncSummaryLabel.leadingAnchor.constraint(equalTo: summaryCard.leadingAnchor, constant: 10),
            pendingSyncSummaryLabel.trailingAnchor.constraint(equalTo: summaryCard.trailingAnchor, constant: -10),
            pendingSyncSummaryLabel.bottomAnchor.constraint(equalTo: summaryCard.bottomAnchor, constant: -10)
        ])

        let detailCard = UIView()
        detailCard.backgroundColor = .white
        detailCard.layer.cornerRadius = 12
        detailCard.layer.borderWidth = 1
        detailCard.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        detailCard.clipsToBounds = true
        detailCard.translatesAutoresizingMaskIntoConstraints = false
        pendingSyncDetailLabel.translatesAutoresizingMaskIntoConstraints = false
        detailCard.addSubview(pendingSyncDetailLabel)
        NSLayoutConstraint.activate([
            pendingSyncDetailLabel.topAnchor.constraint(equalTo: detailCard.topAnchor, constant: 10),
            pendingSyncDetailLabel.leadingAnchor.constraint(equalTo: detailCard.leadingAnchor, constant: 10),
            pendingSyncDetailLabel.trailingAnchor.constraint(equalTo: detailCard.trailingAnchor, constant: -10),
            pendingSyncDetailLabel.bottomAnchor.constraint(equalTo: detailCard.bottomAnchor, constant: -10)
        ])

        let cardStack = UIStackView(arrangedSubviews: [
            title,
            subtitle,
            countsButton,
            retryButton,
            summaryCard,
            detailCard
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

        applyRuntimeModeChange()
    }

    private func buildMeasurementRunFlowUi() {
        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)

        let title = UILabel()
        title.text = "Run Measurement"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label

        let subtitle = UILabel()
        subtitle.text = "Start a measurement and follow live progress."
        subtitle.font = UIFont.preferredFont(forTextStyle: .body)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0

        measurementRunPrimaryButton.setTitle("Start Measurement", for: .normal)
        measurementRunPrimaryButton.accessibilityIdentifier = Self.measurementRunStartIdentifier
        applyButtonStyle(measurementRunPrimaryButton, role: .primary)
        measurementRunPrimaryButton.addTarget(self, action: #selector(runPhase3Sequence), for: .touchUpInside)

        measurementRunHeaderLabel.text = "Ready to start."
        measurementRunHeaderLabel.font = UIFont.preferredFont(forTextStyle: .title3)
        measurementRunHeaderLabel.textColor = .label
        measurementRunHeaderLabel.numberOfLines = 0
        measurementRunHeaderLabel.accessibilityIdentifier = Self.measurementRunHeaderIdentifier

        measurementRunProgressView.progress = 0
        measurementRunProgressView.accessibilityValue = "PRE"
        measurementRunProgressView.accessibilityIdentifier = Self.measurementRunProgressIdentifier

        measurementRunDetailLabel.text = "Tap Start Measurement to begin."
        measurementRunDetailLabel.font = UIFont.preferredFont(forTextStyle: .body)
        measurementRunDetailLabel.textColor = .secondaryLabel
        measurementRunDetailLabel.numberOfLines = 0
        measurementRunDetailLabel.accessibilityIdentifier = Self.measurementRunDetailIdentifier

        measurementRunResultsLabel.text =
            "Latency: --\n" +
            "Download: --\n" +
            "Upload: --\n" +
            "Uploaded: In progress\n\n" +
            "Measurement in progress."
        measurementRunResultsLabel.font = UIFont.preferredFont(forTextStyle: .body)
        measurementRunResultsLabel.textColor = UIColor(red: 0.18, green: 0.29, blue: 0.38, alpha: 1.0)
        measurementRunResultsLabel.numberOfLines = 0
        measurementRunResultsLabel.setContentCompressionResistancePriority(.required, for: .vertical)
        measurementRunResultsLabel.backgroundColor = .white
        measurementRunResultsLabel.layer.cornerRadius = 12
        measurementRunResultsLabel.layer.masksToBounds = true
        measurementRunResultsLabel.layer.borderColor = UIColor(red: 0.78, green: 0.86, blue: 0.93, alpha: 1.0).cgColor
        measurementRunResultsLabel.layer.borderWidth = 1
        measurementRunResultsLabel.textInsets = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        measurementRunResultsLabel.isHidden = true
        measurementRunResultsLabel.accessibilityIdentifier = Self.measurementRunResultsIdentifier

        let cardStack = UIStackView(arrangedSubviews: [
            title,
            subtitle,
            measurementRunHeaderLabel,
            measurementRunProgressView,
            measurementRunDetailLabel,
            measurementRunResultsLabel,
            measurementRunPrimaryButton
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

        applyRuntimeModeChange()
    }

    private func buildMeasurementHistoryFlowUi() {
        overrideUserInterfaceStyle = .light
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)

        let title = UILabel()
        title.text = "Measurement History"
        title.font = UIFont.preferredFont(forTextStyle: .title2)
        title.textColor = .label

        let subtitle = UILabel()
        subtitle.text = "Review your latest results and sync status."
        subtitle.font = UIFont.preferredFont(forTextStyle: .body)
        subtitle.textColor = .secondaryLabel
        subtitle.numberOfLines = 0

        measurementHistoryTitleLabel.text = "Measurement details"
        measurementHistoryTitleLabel.font = UIFont.preferredFont(forTextStyle: .title3)
        measurementHistoryTitleLabel.textColor = .label
        measurementHistoryTitleLabel.numberOfLines = 0
        measurementHistoryTitleLabel.accessibilityIdentifier = Self.measurementHistoryTitleIdentifier

        let recentRunsTitle = UILabel()
        recentRunsTitle.text = "Recent runs (newest first)"
        recentRunsTitle.font = UIFont.preferredFont(forTextStyle: .headline)
        recentRunsTitle.textColor = .label

        measurementHistoryRunsStack.axis = .vertical
        measurementHistoryRunsStack.spacing = 8
        measurementHistoryRunsStack.accessibilityIdentifier = Self.measurementHistoryRunsIdentifier
        measurementHistoryRunsStack.translatesAutoresizingMaskIntoConstraints = false
        measurementHistoryRunsScrollView.translatesAutoresizingMaskIntoConstraints = false
        measurementHistoryRunsScrollView.showsVerticalScrollIndicator = true
        measurementHistoryRunsScrollView.alwaysBounceVertical = true
        measurementHistoryRunsScrollView.addSubview(measurementHistoryRunsStack)
        NSLayoutConstraint.activate([
            measurementHistoryRunsStack.topAnchor.constraint(equalTo: measurementHistoryRunsScrollView.topAnchor),
            measurementHistoryRunsStack.leadingAnchor.constraint(equalTo: measurementHistoryRunsScrollView.leadingAnchor),
            measurementHistoryRunsStack.trailingAnchor.constraint(equalTo: measurementHistoryRunsScrollView.trailingAnchor),
            measurementHistoryRunsStack.bottomAnchor.constraint(equalTo: measurementHistoryRunsScrollView.bottomAnchor),
            measurementHistoryRunsStack.widthAnchor.constraint(equalTo: measurementHistoryRunsScrollView.widthAnchor)
        ])

        measurementHistorySelectedDetailLabel.text = "Tap a run to view details."
        measurementHistorySelectedDetailLabel.font = UIFont.preferredFont(forTextStyle: .body)
        measurementHistorySelectedDetailLabel.textColor = UIColor(red: 0.18, green: 0.29, blue: 0.38, alpha: 1.0)
        measurementHistorySelectedDetailLabel.numberOfLines = 0
        measurementHistorySelectedDetailLabel.backgroundColor = .white
        measurementHistorySelectedDetailLabel.layer.cornerRadius = 12
        measurementHistorySelectedDetailLabel.layer.masksToBounds = true
        measurementHistorySelectedDetailLabel.layer.borderColor = UIColor(red: 0.78, green: 0.86, blue: 0.93, alpha: 1.0).cgColor
        measurementHistorySelectedDetailLabel.layer.borderWidth = 1
        measurementHistorySelectedDetailLabel.textInsets = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        measurementHistorySelectedDetailLabel.accessibilityIdentifier = Self.measurementHistorySelectedDetailIdentifier

        measurementHistorySyncLabel.text = "Sync status unknown. Tap refresh."
        measurementHistorySyncLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        measurementHistorySyncLabel.textColor = UIColor(red: 0.23, green: 0.37, blue: 0.47, alpha: 1.0)
        measurementHistorySyncLabel.numberOfLines = 0
        measurementHistorySyncLabel.backgroundColor = UIColor(red: 0.95, green: 0.98, blue: 0.96, alpha: 1)
        measurementHistorySyncLabel.layer.cornerRadius = 10
        measurementHistorySyncLabel.layer.masksToBounds = true
        measurementHistorySyncLabel.layer.borderColor = UIColor(red: 0.78, green: 0.89, blue: 0.80, alpha: 1.0).cgColor
        measurementHistorySyncLabel.layer.borderWidth = 1
        measurementHistorySyncLabel.textInsets = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
        measurementHistorySyncLabel.accessibilityIdentifier = Self.measurementHistorySyncIdentifier

        let refreshButton = UIButton(type: .system)
        refreshButton.setTitle("Refresh Sync Status", for: .normal)
        applyButtonStyle(refreshButton, role: .primary)
        refreshButton.accessibilityIdentifier = Self.measurementHistoryRefreshIdentifier
        refreshButton.addTarget(self, action: #selector(refreshMeasurementHistorySync), for: .touchUpInside)

        let cardStack = UIStackView(arrangedSubviews: [
            title,
            subtitle,
            measurementHistoryTitleLabel,
            recentRunsTitle,
            measurementHistoryRunsScrollView,
            measurementHistorySelectedDetailLabel,
            measurementHistorySyncLabel,
            refreshButton
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
            cardStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14),
            measurementHistoryRunsScrollView.heightAnchor.constraint(equalToConstant: 220)
        ])
        renderMeasurementHistoryUi()
        refreshMeasurementHistorySync()
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

    @objc private func copySettingsDeviceId() {
        UIPasteboard.general.string = resolveSettingsDeviceId()
        onboardingFeedbackLabel.text = "Device ID copied."
        onboardingFeedbackLabel.textColor = UIColor(red: 0.18, green: 0.45, blue: 0.22, alpha: 1.0)
        setStatus("Device ID copied.")
    }

    @objc private func copySettingsAppVersion() {
        UIPasteboard.general.string = resolveSettingsAppVersion()
        onboardingFeedbackLabel.text = "App version copied."
        onboardingFeedbackLabel.textColor = UIColor(red: 0.18, green: 0.45, blue: 0.22, alpha: 1.0)
        setStatus("App version copied.")
    }

    private func resolveSettingsDeviceId() -> String {
        let raw = UIDevice.current.identifierForVendor?.uuidString.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return raw.isEmpty ? "unavailable" : raw
    }

    private func resolveSettingsAppVersion() -> String {
        let short = (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let build = (Bundle.main.object(forInfoDictionaryKey: kCFBundleVersionKey as String) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if let short, !short.isEmpty, let build, !build.isEmpty {
            return "\(short) (\(build))"
        }
        if let short, !short.isEmpty {
            return short
        }
        if let build, !build.isEmpty {
            return build
        }
        return "unknown"
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

    @objc private func runPendingSyncCounts() {
        if displayMode == .pendingSyncFlow {
            pendingSyncDetailLabel.text = "Checking pending uploads..."
            pendingSyncDetailLabel.textColor = UIColor(red: 0.23, green: 0.37, blue: 0.47, alpha: 1)
        }
        setStatus("Checking pending sync counts...")
        pendingSyncHarness.runPendingCountsAsync { text, error in
            DispatchQueue.main.async {
                if let error = error {
                    self.pendingSyncSummaryLabel.text = "Unable to load pending uploads."
                    self.pendingSyncSummaryLabel.textColor = UIColor.systemRed
                    if self.displayMode == .pendingSyncFlow {
                        self.pendingSyncDetailLabel.text = "Unable to load pending uploads."
                        self.pendingSyncDetailLabel.textColor = UIColor.systemRed
                    }
                    let envelope = self.smokeEnvelopeBuilder.failure(
                        scenario: "pending-sync-counts",
                        errorMessage: "\(error)"
                    )
                    self.setStatus(self.smokeFormatter.format(envelope: envelope))
                    return
                }
                let rendered = text ?? "Pending sync counts unavailable."
                self.pendingSyncSummaryLabel.text = self.pendingSummaryFromCounts(rendered)
                self.pendingSyncSummaryLabel.textColor = UIColor(red: 0.11, green: 0.43, blue: 0.23, alpha: 1)
                if self.displayMode == .pendingSyncFlow {
                    self.pendingSyncDetailLabel.text = self.pendingSummaryFromCounts(rendered)
                    self.pendingSyncDetailLabel.textColor = UIColor(red: 0.11, green: 0.43, blue: 0.23, alpha: 1)
                }
                self.setStatus(rendered)
            }
        }
    }

    @objc private func runRetryPendingSync() {
        guard let snapshot = runtimeSnapshot else {
            if displayMode == .pendingSyncFlow {
                pendingSyncDetailLabel.text = "Runtime profile unavailable."
                pendingSyncDetailLabel.textColor = UIColor.systemRed
            }
            setStatus("Runtime profile unavailable.")
            return
        }
        if displayMode == .pendingSyncFlow {
            pendingSyncDetailLabel.text = "Retrying sync..."
            pendingSyncDetailLabel.textColor = UIColor(red: 0.23, green: 0.37, blue: 0.47, alpha: 1)
        }
        setStatus("Retrying pending sync...")
        pendingSyncHarness.runRetryPendingSyncAsync(
            supabaseUrl: snapshot.supabaseUrl,
            supabaseApiKey: snapshot.supabaseApiKey
        ) { text, error in
            DispatchQueue.main.async {
                if let error = error {
                    self.pendingSyncSummaryLabel.text = "Retry failed. Check connection and retry."
                    self.pendingSyncSummaryLabel.textColor = UIColor.systemRed
                    if self.displayMode == .pendingSyncFlow {
                        self.pendingSyncDetailLabel.text = "Retry failed. Check connection and retry."
                        self.pendingSyncDetailLabel.textColor = UIColor.systemRed
                    }
                    let envelope = self.smokeEnvelopeBuilder.failure(
                        scenario: "retry-pending-sync",
                        errorMessage: "\(error)"
                    )
                    self.setStatus(self.smokeFormatter.format(envelope: envelope))
                    return
                }
                let rendered = text ?? "Retry pending sync returned no output."
                self.pendingSyncSummaryLabel.text = self.pendingSummaryFromRetry(rendered)
                self.pendingSyncSummaryLabel.textColor = self.pendingSummaryColorFromRetry(rendered)
                if self.displayMode == .pendingSyncFlow {
                    self.pendingSyncDetailLabel.text = self.pendingSummaryFromRetry(rendered)
                    self.pendingSyncDetailLabel.textColor = self.pendingSummaryColorFromRetry(rendered)
                }
                self.setStatus(rendered)
            }
        }
    }

    @objc private func refreshMeasurementHistorySync() {
        setStatus("Refreshing sync status...")
        pendingSyncHarness.runPendingCountsAsync { text, error in
            DispatchQueue.main.async {
                if let error = error {
                    self.historyPendingMeasurements = nil
                    self.historyPendingSubmissions = nil
                    self.renderMeasurementHistoryUi()
                    self.setStatus("Unable to refresh sync status: \(error)")
                    return
                }
                let rendered = text ?? ""
                self.historyPendingMeasurements = self.extractInt(rendered, key: "measurements=")
                self.historyPendingSubmissions = self.extractInt(rendered, key: "submissions=")
                self.renderMeasurementHistoryUi()
                self.setStatus("Sync status refreshed.")
            }
        }
    }

    private func renderMeasurementHistoryUi() {
        let defaults = UserDefaults.standard
        let detail = defaults.string(forKey: "cellwatch.history.detail")
            ?? "Run your first measurement to populate history."
        let hasFallbackMeasurement =
            !(defaults.string(forKey: "cellwatch.history.latency") ?? "").isEmpty ||
            !(defaults.string(forKey: "cellwatch.history.download") ?? "").isEmpty ||
            !(defaults.string(forKey: "cellwatch.history.upload") ?? "").isEmpty
        let entries = loadHistoryEntries()
        measurementHistoryTitleLabel.accessibilityValue = (entries.isEmpty && !hasFallbackMeasurement) ? "EMPTY" : "HAS_MEASUREMENT"
        renderHistoryEntriesRows(entries, fallbackDetail: detail)
        let syncSummary: String
        let syncStateKey: String
        if let measurements = historyPendingMeasurements, let submissions = historyPendingSubmissions {
            let total = measurements + submissions
            if total <= 0 {
                syncSummary = "All records are synced."
                syncStateKey = "SYNCED"
            } else {
                syncSummary = "Pending sync queue: \(measurements) measurement record(s), \(submissions) submission record(s)."
                syncStateKey = "PENDING"
            }
        } else {
            syncSummary = "Sync status unknown. Tap refresh."
            syncStateKey = "UNKNOWN"
        }
        measurementHistorySyncLabel.text = syncSummary
        measurementHistorySyncLabel.accessibilityValue = syncStateKey
    }

    private func clearPersistedHistorySnapshots() {
        let defaults = UserDefaults.standard
        [
            "cellwatch.history.latency",
            "cellwatch.history.download",
            "cellwatch.history.upload",
            "cellwatch.history.uploaded",
            "cellwatch.history.detail",
            "cellwatch.history.entries",
        ].forEach { defaults.removeObject(forKey: $0) }
        selectedHistoryTimestampMs = nil
    }

    private func persistLatestHistorySnapshot(
        latency: String,
        download: String,
        upload: String,
        uploaded: String,
        detail: String,
        latitude: Double? = nil,
        longitude: Double? = nil
    ) {
        let defaultLat = Double(RuntimeConfigSource.value("CELLWATCH_MAP_SIM_LAT") ?? "") ?? 33.778462
        let defaultLon = Double(RuntimeConfigSource.value("CELLWATCH_MAP_SIM_LON") ?? "") ?? -84.390123
        let currentCoordinate = locationPermissionManager.location?.coordinate
        let resolvedLatitude = latitude ?? currentCoordinate?.latitude ?? defaultLat
        let resolvedLongitude = longitude ?? currentCoordinate?.longitude ?? defaultLon
        let defaults = UserDefaults.standard
        var entries = loadHistoryEntries()
        entries.insert(
            HistorySnapshotEntry(
                timestampMs: Date().timeIntervalSince1970 * 1000.0,
                latency: latency,
                download: download,
                upload: upload,
                uploaded: uploaded,
                detail: detail,
                latitude: resolvedLatitude,
                longitude: resolvedLongitude
            ),
            at: 0
        )
        if entries.count > 20 {
            entries = Array(entries.prefix(20))
        }
        defaults.set(latency, forKey: "cellwatch.history.latency")
        defaults.set(download, forKey: "cellwatch.history.download")
        defaults.set(upload, forKey: "cellwatch.history.upload")
        defaults.set(uploaded, forKey: "cellwatch.history.uploaded")
        defaults.set(detail, forKey: "cellwatch.history.detail")
        defaults.set(
            entries.map { entry in
                var row: [String: Any] = [
                    "timestampMs": entry.timestampMs,
                    "latency": entry.latency,
                    "download": entry.download,
                    "upload": entry.upload,
                    "uploaded": entry.uploaded,
                    "detail": entry.detail
                ]
                if let latitude = entry.latitude {
                    row["latitude"] = latitude
                }
                if let longitude = entry.longitude {
                    row["longitude"] = longitude
                }
                return row
            },
            forKey: "cellwatch.history.entries"
        )
    }

    private func loadHistoryEntries() -> [HistorySnapshotEntry] {
        let raw = UserDefaults.standard.array(forKey: "cellwatch.history.entries") as? [[String: Any]] ?? []
        return raw.compactMap { row in
            guard let timestampMs = row["timestampMs"] as? Double else { return nil }
            let latency = row["latency"] as? String ?? "--"
            let download = row["download"] as? String ?? "--"
            let upload = row["upload"] as? String ?? "--"
            let uploaded = row["uploaded"] as? String ?? "Pending sync"
            let detail = row["detail"] as? String ?? "Latest measurement captured."
            let latitude = row["latitude"] as? Double
            let longitude = row["longitude"] as? Double
            return HistorySnapshotEntry(
                timestampMs: timestampMs,
                latency: latency,
                download: download,
                upload: upload,
                uploaded: uploaded,
                detail: detail,
                latitude: latitude,
                longitude: longitude
            )
        }
    }

    private func mapHomeLocationSnapshots(from entries: [HistorySnapshotEntry]) -> [MapHomeMeasurementLocationSnapshot] {
        let snapshots = entries.enumerated().map { index, entry in
            MapHomeMeasurementLocationSnapshot(
                id: "history-\(Int64(entry.timestampMs))-\(index)",
                title: "Run \(index + 1)",
                timestampMs: Int64(entry.timestampMs),
                latitude: entry.latitude ?? Double.nan,
                longitude: entry.longitude ?? Double.nan
            )
        }
        if !snapshots.isEmpty {
            return snapshots
        }
        let defaultLat = Double(RuntimeConfigSource.value("CELLWATCH_MAP_SIM_LAT") ?? "") ?? 33.778462
        let defaultLon = Double(RuntimeConfigSource.value("CELLWATCH_MAP_SIM_LON") ?? "") ?? -84.390123
        return [
            MapHomeMeasurementLocationSnapshot(
                id: "current-location",
                title: "Current location",
                timestampMs: Int64(Date().timeIntervalSince1970 * 1000),
                latitude: defaultLat,
                longitude: defaultLon
            )
        ]
    }

    private func applyInitialMapHomeCamera(
        historyEntries: [HistorySnapshotEntry],
        interactionState: MapHomeMapInteractionState
    ) {
#if canImport(MapboxMaps)
        guard let mapView = mapHomeMapView else { return }
        _ = mapHomeFeatureViewController.loadMeasurements(values: mapHomeLocationSnapshots(from: historyEntries))
        _ = mapHomeFeatureViewController.onZoomChanged(zoomLevel: interactionState.zoomLevel)
        let featureState = mapHomeFeatureViewController.currentState()
        let defaultLat = Double(RuntimeConfigSource.value("CELLWATCH_MAP_SIM_LAT") ?? "") ?? 33.778462
        let defaultLon = Double(RuntimeConfigSource.value("CELLWATCH_MAP_SIM_LON") ?? "") ?? -84.390123
        let targetLat = featureState.centerLatitude?.doubleValue ?? defaultLat
        let targetLon = featureState.centerLongitude?.doubleValue ?? defaultLon
        let targetZoom: Double = if featureState.hasAnyLocationData {
            interactionState.overlayMode == .hexGrid ? 10.5 : 12.5
        } else {
            12.5
        }
        mapView.mapboxMap.setCamera(
            to: CameraOptions(
                center: CLLocationCoordinate2D(latitude: targetLat, longitude: targetLon),
                zoom: targetZoom
            )
        )
#endif
    }

    @discardableResult
    private func renderMapHomeFeatures(
        historyEntries: [HistorySnapshotEntry],
        interactionState: MapHomeMapInteractionState
    ) -> MapHomeFeatureState {
        _ = mapHomeFeatureViewController.loadMeasurements(
            values: mapHomeLocationSnapshots(from: historyEntries)
        )
        _ = mapHomeFeatureViewController.onZoomChanged(zoomLevel: interactionState.zoomLevel)
        let latestState = mapHomeFeatureViewController.currentState()

#if canImport(MapboxMaps)
        guard let mapView = mapHomeMapView else {
            return latestState
        }

        if mapHomePointAnnotationManager == nil {
            mapHomePointAnnotationManager = mapView.annotations.makePointAnnotationManager(id: "mapHomePoints")
        }
        if mapHomeHexAnnotationManager == nil {
            mapHomeHexAnnotationManager = mapView.annotations.makePointAnnotationManager(id: "mapHomeHex")
        }
        guard let pointManager = mapHomePointAnnotationManager,
              let hexManager = mapHomeHexAnnotationManager else {
            return latestState
        }

        if interactionState.overlayMode == .points {
            hexManager.annotations = []
            pointManager.annotations = latestState.points.map { point in
                var annotation = PointAnnotation(
                    coordinate: CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
                )
                annotation.iconImage = "marker-15"
                annotation.iconSize = 1.6
                annotation.textField = point.title
                return annotation
            }
        } else {
            pointManager.annotations = []
            hexManager.annotations = latestState.hexCells.map { cell in
                var annotation = PointAnnotation(
                    coordinate: CLLocationCoordinate2D(latitude: cell.centerLatitude, longitude: cell.centerLongitude)
                )
                annotation.iconImage = "circle-15"
                annotation.iconSize = 1.2
                annotation.textField = "\(cell.measurementCount)"
                return annotation
            }
        }
#endif

        return latestState
    }

    private func renderHistoryEntriesRows(_ entries: [HistorySnapshotEntry], fallbackDetail: String) {
        measurementHistoryRunsStack.arrangedSubviews.forEach { view in
            measurementHistoryRunsStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        let sorted = Array(entries.sorted { lhs, rhs in lhs.timestampMs > rhs.timestampMs }.prefix(5))
        guard !sorted.isEmpty else {
            let empty = UILabel()
            empty.text = "No recent runs yet."
            empty.textColor = .secondaryLabel
            empty.font = UIFont.preferredFont(forTextStyle: .subheadline)
            measurementHistoryRunsStack.addArrangedSubview(empty)
            measurementHistoryTitleLabel.text = "Measurement details"
            measurementHistorySelectedDetailLabel.text = fallbackDetail
            selectedHistoryTimestampMs = nil
            return
        }

        if selectedHistoryTimestampMs == nil || !sorted.contains(where: { $0.timestampMs == selectedHistoryTimestampMs }) {
            selectedHistoryTimestampMs = sorted.first?.timestampMs
        }

        for (index, entry) in sorted.enumerated() {
            let button = UIButton(type: .system)
            button.setTitle(
                "Run \(index + 1): \(entry.latency) latency, \(entry.download) download, \(entry.upload) upload",
                for: .normal
            )
            button.titleLabel?.numberOfLines = 0
            button.contentHorizontalAlignment = .left
            button.titleLabel?.lineBreakMode = .byWordWrapping
            button.contentEdgeInsets = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
            button.accessibilityIdentifier = "harness.measurementHistory.row.\(index + 1)"
            applyButtonStyle(
                button,
                role: entry.timestampMs == selectedHistoryTimestampMs ? .primary : .secondary
            )
            button.addAction(
                UIAction { [weak self] _ in
                    self?.selectedHistoryTimestampMs = entry.timestampMs
                    self?.renderHistoryEntriesRows(entries, fallbackDetail: fallbackDetail)
                },
                for: .touchUpInside
            )
            measurementHistoryRunsStack.addArrangedSubview(button)
        }

        if let selected = sorted.first(where: { $0.timestampMs == selectedHistoryTimestampMs }) ?? sorted.first {
            let selectedIndex = (sorted.firstIndex { $0.timestampMs == selected.timestampMs } ?? 0) + 1
            let captured = Date(timeIntervalSince1970: selected.timestampMs / 1000.0)
            let capturedText = DateFormatter.localizedString(from: captured, dateStyle: .short, timeStyle: .short)
            measurementHistoryTitleLabel.text = "Selected run"
            measurementHistorySelectedDetailLabel.text =
                "Captured: \(capturedText)\n" +
                "Latency: \(selected.latency)\n" +
                "Download: \(selected.download)\n" +
                "Upload: \(selected.upload)\n" +
                "Status: \(selected.uploaded)\n\n" +
                selected.detail
        }
    }

    private func pendingSummaryFromCounts(_ rendered: String) -> String {
        let total = extractInt(rendered, key: "total=") ?? 0
        return "Pending uploads: \(total) item(s)"
    }

    private func pendingSummaryFromRetry(_ rendered: String) -> String {
        if rendered.contains("status=SUCCEEDED") {
            return "Sync complete. No pending uploads."
        }
        if rendered.contains("status=PARTIAL_FAILURE") {
            return "Sync partially complete. Some uploads are still pending."
        }
        if rendered.contains("status=FAILED") {
            return "Sync failed. Check connection and retry."
        }
        if rendered.contains("status=PENDING") {
            return "Sync still pending."
        }
        if rendered.contains("status=IN_PROGRESS") {
            return "Sync in progress."
        }
        return "No pending uploads."
    }

    private func pendingSummaryColorFromRetry(_ rendered: String) -> UIColor {
        if rendered.contains("status=SUCCEEDED") || rendered.contains("status=IDLE") {
            return UIColor(red: 0.11, green: 0.43, blue: 0.23, alpha: 1)
        }
        if rendered.contains("status=FAILED") {
            return .systemRed
        }
        return UIColor(red: 0.63, green: 0.42, blue: 0.00, alpha: 1)
    }

    private func extractInt(_ text: String, key: String) -> Int? {
        guard let range = text.range(of: key) else { return nil }
        let suffix = text[range.upperBound...]
        let digits = suffix.prefix { $0.isNumber }
        return Int(digits)
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
        let bypassStrictPreflight = (displayMode == .measurementRunFlow)
        if displayMode == .measurementRunFlow {
            measurementRunDetailLabel.text = "Initializing measurement run..."
            measurementRunDetailLabel.textColor = .secondaryLabel
        }
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
        if !story2Preflight.allowed && !bypassStrictPreflight {
            let envelope = smokeEnvelopeBuilder.failure(
                scenario: "measurement-start-preflight",
                errorMessage: "reason=\(story2Preflight.reasonCode)"
            )
            if displayMode == .measurementRunFlow {
                measurementRunDetailLabel.text = "Unable to start measurement right now (preflight gate)."
                measurementRunDetailLabel.textColor = .systemRed
            } else {
                setStatus(smokeFormatter.format(envelope: envelope))
            }
            return
        }

        if runtimeSnapshot == nil {
            applyRuntimeModeChange()
        }
        let activeSnapshot = runtimeSnapshot ?? RuntimeSelection.resolve(
            bridge: runtimeModeBridge,
            msakMode: selectedMsakMode,
            supabaseMode: selectedSupabaseMode
        ).snapshot
        guard let runtimeSnapshot = activeSnapshot else {
            if displayMode == .measurementRunFlow {
                measurementRunDetailLabel.text = "Runtime profile unavailable (configuration required)."
                measurementRunDetailLabel.textColor = .systemRed
            }
            setStatus("Runtime profile unavailable.")
            return
        }
        if !bypassStrictPreflight, let preflightError = phase3PreflightError(
            snapshot: runtimeSnapshot,
            includeReachabilityCheck: displayMode != .measurementRunFlow
        ) {
            NSLog("[iosTestApp] Phase3 preflight failed: %@", preflightError)
            let envelope = smokeEnvelopeBuilder.failure(
                scenario: "phase3-preflight",
                errorMessage: preflightError
            )
            if displayMode == .measurementRunFlow {
                measurementRunDetailLabel.text = "Configuration issue: \(preflightError)"
                measurementRunDetailLabel.textColor = .systemRed
            } else {
                setStatus(smokeFormatter.format(envelope: envelope))
            }
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
        measurementRunCachedLatencySummary = "--"
        measurementRunCachedDownloadSummary = "--"
        measurementRunCachedUploadSummary = "--"
        measurementRunCachedUploadedSummary = "In progress"
        measurementRunCachedCompletionSummary = "Measurement in progress."
        measurementRunCachedCenterLatitude = nil
        measurementRunCachedCenterLongitude = nil
        resetMeasurementRunFlowScheduling()
        if displayMode == .measurementRunFlow {
            renderMeasurementRunFlowState(detailText: "Measurement started. Preparing test run...")
        } else {
            setStatus(measurementRunUiPresenter.present(state: measurementRunViewController.currentState()).headerText)
        }
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
                if self.displayMode == .measurementRunFlow {
                    self.scheduleMeasurementRunFlowUpdate {
                        self.applyMeasurementRunProgressHeader(headerText)
                        self.renderMeasurementRunFlowState(
                            detailText: self.detailTextForMeasurementHeader(headerText)
                        )
                    }
                } else {
                    DispatchQueue.main.async {
                        self.applyMeasurementRunProgressHeader(headerText)
                        self.setStatus(headerText)
                    }
                }
            }
        ) { result, error in
            DispatchQueue.main.async {
                if let error = error {
                    NSLog("[iosTestApp] Phase3 sequence failed: %@", String(describing: error))
                    let hinted = self.withProtocolHint("\(error)")
                    let envelope = self.smokeEnvelopeBuilder.failure(
                        scenario: "phase3-sequence-sync",
                        errorMessage: hinted
                    )
                    if self.displayMode == .measurementRunFlow {
                        self.measurementRunCachedLatencySummary = "--"
                        self.measurementRunCachedDownloadSummary = "--"
                        self.measurementRunCachedUploadSummary = "--"
                        self.measurementRunCachedUploadedSummary = "Not uploaded"
                        self.measurementRunCachedCompletionSummary = hinted
                        self.measurementRunCachedCenterLatitude = nil
                        self.measurementRunCachedCenterLongitude = nil
                        self.scheduleMeasurementRunFlowCompletion {
                            _ = self.measurementRunViewController.onCompleted(
                                group: nil,
                                errorCode: nil,
                                errorText: hinted
                            )
                            self.renderMeasurementRunFlowState(detailText: "Measurement failed. \(hinted)")
                        }
                    } else {
                        _ = self.measurementRunViewController.onCompleted(
                            group: nil,
                            errorCode: nil,
                            errorText: hinted
                        )
                        let runHeader = self.measurementRunUiPresenter
                            .present(state: self.measurementRunViewController.currentState())
                            .headerText
                        self.setStatus(runHeader + "\n" + self.smokeFormatter.format(envelope: envelope))
                    }
                    return
                }
                guard let value = result else {
                    let envelope = self.smokeEnvelopeBuilder.failure(
                        scenario: "phase3-sequence-sync",
                        errorMessage: "no result"
                    )
                    if self.displayMode == .measurementRunFlow {
                        self.measurementRunCachedLatencySummary = "--"
                        self.measurementRunCachedDownloadSummary = "--"
                        self.measurementRunCachedUploadSummary = "--"
                        self.measurementRunCachedUploadedSummary = "Not uploaded"
                        self.measurementRunCachedCompletionSummary = "Measurement failed. No result."
                        self.measurementRunCachedCenterLatitude = nil
                        self.measurementRunCachedCenterLongitude = nil
                        self.scheduleMeasurementRunFlowCompletion {
                            _ = self.measurementRunViewController.onCompleted(
                                group: nil,
                                errorCode: nil,
                                errorText: "no result"
                            )
                            self.renderMeasurementRunFlowState(detailText: "Measurement failed. No result.")
                        }
                    } else {
                        _ = self.measurementRunViewController.onCompleted(
                            group: nil,
                            errorCode: nil,
                            errorText: "no result"
                        )
                        let runHeader = self.measurementRunUiPresenter
                            .present(state: self.measurementRunViewController.currentState())
                            .headerText
                        self.setStatus(runHeader + "\n" + self.smokeFormatter.format(envelope: envelope))
                    }
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
                if self.displayMode == .measurementRunFlow {
                    self.measurementRunCachedLatencySummary = value.latencySummary
                    self.measurementRunCachedDownloadSummary = value.downloadSummary
                    self.measurementRunCachedUploadSummary = value.uploadSummary
                    self.measurementRunCachedUploadedSummary = value.measurementCompleteUploadTimeSet ? "Uploaded" : "Pending sync"
                    self.measurementRunCachedCompletionSummary = value.measurementCompleteUploadTimeSet
                        ? "Measurement complete. Results saved and synced."
                        : "Measurement complete. Results saved and sync attempted."
                    self.measurementRunCachedCenterLatitude = value.centerLatitude.isNaN ? nil : value.centerLatitude
                    self.measurementRunCachedCenterLongitude = value.centerLongitude.isNaN ? nil : value.centerLongitude
                    self.scheduleMeasurementRunFlowCompletion {
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
                        self.renderMeasurementRunFlowState(
                            detailText: value.completionSummary
                        )
                    }
                } else {
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
                }
                NSLog("[iosTestApp] Phase3 sequence success diagnostics=%@", self.diagnosticsSummary)
            }
        }
    }

    private func resetMeasurementRunFlowScheduling() {
        measurementRunFlowQueue.sync {
            measurementRunFlowScheduledSteps = 0
            measurementRunFlowFinalizing = false
        }
    }

    private func scheduleMeasurementRunFlowUpdate(_ action: @escaping () -> Void) {
        let delay = measurementRunFlowQueue.sync { () -> TimeInterval? in
            if measurementRunFlowFinalizing {
                return nil
            }
            measurementRunFlowScheduledSteps += 1
            return TimeInterval(measurementRunFlowScheduledSteps) * measurementRunFlowStepDelay
        }
        guard let delay else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: action)
    }

    private func scheduleMeasurementRunFlowCompletion(_ action: @escaping () -> Void) {
        let delay = measurementRunFlowQueue.sync { () -> TimeInterval in
            measurementRunFlowFinalizing = true
            let nextStep = max(measurementRunFlowScheduledSteps + 1, 1)
            return TimeInterval(nextStep) * measurementRunFlowStepDelay
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: action)
    }

    private func renderMeasurementRunFlowState(detailText: String) {
        let state = measurementRunViewController.currentState()
        let model = measurementRunUiPresenter.present(state: state)
        let readModel = measurementResultReadModelUseCase.present(state: state)
        measurementRunHeaderLabel.text = model.headerText
        measurementRunDetailLabel.text = detailText
        measurementRunDetailLabel.textColor = state.progress == .error ? .systemRed : .secondaryLabel
        measurementRunProgressView.progress = measurementRunProgressValue(state.progress)
        measurementRunProgressView.accessibilityValue = canonicalMeasurementRunStateName(state.progress)
        let isTerminal = model.showCompletionActions
        measurementRunResultsLabel.isHidden = !isTerminal
        measurementRunPrimaryButton.setTitle(
            isTerminal ? "Take Another Measurement" : "Start Measurement",
            for: .normal
        )
        let latency = readModel.latencyText == "--" ? measurementRunCachedLatencySummary : readModel.latencyText
        let download = readModel.downloadText == "--" ? measurementRunCachedDownloadSummary : readModel.downloadText
        let upload = readModel.uploadText == "--" ? measurementRunCachedUploadSummary : readModel.uploadText
        let uploaded = isTerminal ? measurementRunCachedUploadedSummary : readModel.uploadedText
        let summary: String
        if state.progress == .error {
            summary = readModel.summaryText
        } else if isTerminal {
            summary = measurementRunCachedCompletionSummary
        } else if readModel.summaryText == "Measurement in progress." {
            summary = measurementRunCachedCompletionSummary
        } else {
            summary = readModel.summaryText
        }
        measurementRunResultsLabel.text =
            "Latency: \(latency)\n" +
            "Download: \(download)\n" +
            "Upload: \(upload)\n" +
            "Uploaded: \(uploaded)\n\n" +
            summary
        measurementRunResultsLabel.textColor = state.progress == .error
            ? .systemRed
            : UIColor(red: 0.18, green: 0.29, blue: 0.38, alpha: 1.0)
        if isTerminal {
            persistLatestHistorySnapshot(
                latency: latency,
                download: download,
                upload: upload,
                uploaded: uploaded,
                detail: summary,
                latitude: measurementRunCachedCenterLatitude,
                longitude: measurementRunCachedCenterLongitude
            )
        }
    }

    private func canonicalMeasurementRunStateName(_ progress: MeasurementRunProgress) -> String {
        switch progress {
        case .pre: return "PRE"
        case .start: return "START"
        case .locate: return "LOCATE"
        case .latency: return "LATENCY"
        case .download: return "DOWNLOAD"
        case .upload: return "UPLOAD"
        case .end: return "END"
        case .error: return "ERROR"
        default: return "UNKNOWN"
        }
    }

    private func measurementRunProgressValue(_ progress: MeasurementRunProgress) -> Float {
        switch progress {
        case .pre: return 0.0
        case .start: return 0.10
        case .locate: return 0.25
        case .latency: return 0.45
        case .download: return 0.65
        case .upload: return 0.85
        case .end, .error: return 1.0
        default: return 0.0
        }
    }

    private func detailTextForMeasurementHeader(_ headerText: String) -> String {
        if headerText.contains("Finding server") {
            return "Finding closest test server..."
        }
        if headerText.contains("Measuring latency") {
            return "Running latency test..."
        }
        if headerText.contains("Measuring download speed") {
            return "Running download throughput test..."
        }
        if headerText.contains("Measuring upload speed") {
            return "Running upload throughput test..."
        }
        if headerText.contains("Measurement complete") {
            return "Measurement complete. Results are saved and sync was attempted."
        }
        if headerText.contains("Measurement failed") {
            return "Measurement failed. Please try again."
        }
        return "Running tests against selected server..."
    }

    private func applyMeasurementRunProgressHeader(_ headerText: String) {
        if headerText.contains("Finding server") {
            _ = measurementRunViewController.onLocateStarted()
            return
        }
        if headerText.contains("Measuring latency") {
            _ = measurementRunViewController.onLatencyStarted()
            return
        }
        if headerText.contains("Measuring download speed") {
            _ = measurementRunViewController.onDownloadStarted()
            return
        }
        if headerText.contains("Measuring upload speed") {
            _ = measurementRunViewController.onUploadStarted()
            return
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

    private func phase3PreflightError(
        snapshot: RuntimeSyncMsakProfileSnapshot,
        includeReachabilityCheck: Bool = true
    ) -> String? {
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
        if includeReachabilityCheck {
            if let localMsakIssue = localMsakReachabilityIssue(snapshot: snapshot) {
                issues.append(localMsakIssue)
            }
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
        measurementPreflightOutputLabel.isHidden = false
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
                if self.autoStartMeasurementAfterPreflight,
                   confirmed.state.latestResult?.allowed == true {
                    self.pushMvpScreen(
                        mode: .measurementRunFlow,
                        autoStartMeasurementRunOnAppear: true
                    )
                }
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
        } else if autoStartMeasurementAfterPreflight, go.state.latestResult?.allowed == true {
            pushMvpScreen(
                mode: .measurementRunFlow,
                autoStartMeasurementRunOnAppear: true
            )
        } else if go.state.latestResult?.reasonCode == .missingLocationPermission {
            requestLocationPermissionIfNeeded(forceRequest: true)
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
        if submission.success && displayMode == .onboardingFlow {
            if let postSubmitMode {
                if postSubmitMode == .measurementStartFlow {
                    pushMvpScreen(
                        mode: .measurementStartFlow,
                        autoStartAfterPreflight: postSubmitAutoStartMeasurement
                    )
                } else if postSubmitMode == .measurementRunFlow {
                    pushMvpScreen(
                        mode: .measurementRunFlow,
                        autoStartMeasurementRunOnAppear: postSubmitAutoStartMeasurement
                    )
                } else {
                    pushMvpScreen(mode: postSubmitMode)
                }
            } else if navigationController == nil && AppDelegate.shouldDefaultToMvpNavigation() {
                replaceRootWithMvpMenu()
            }
        }
    }

    @objc private func submitSettings() {
        _ = syncSettingsViewModelWithInputs()
        let submission = settingsViewModel.submit()
        applySettingsUiState(submission.state)
        setStatus(submission.statusText)
        if submission.success, displayMode == .settingsProfileFlow, let postSubmitMode {
            pushMvpScreen(mode: postSubmitMode)
        }
    }

    @objc private func submitOnboardingFromReturnKey() {
        submitOnboarding()
    }

    @objc private func submitSettingsFromReturnKey() {
        submitSettings()
    }

    @objc private func onboardingFieldChanged(_ sender: UITextField) {
        _ = syncOnboardingViewModelWithInputs()
        applyOnboardingUiState(onboardingViewModel.currentState())
    }

    @objc private func onboardingAckChanged() {
        _ = syncOnboardingViewModelWithInputs()
        applyOnboardingUiState(onboardingViewModel.currentState())
    }

    @objc private func settingsFieldChanged(_ sender: UITextField) {
        _ = syncSettingsViewModelWithInputs()
        applySettingsUiState(settingsViewModel.currentState())
    }

    @objc private func settingsAckChanged() {
        _ = syncSettingsViewModelWithInputs()
        applySettingsUiState(settingsViewModel.currentState())
    }

    @objc private func settingsModeChanged() {
        _ = syncSettingsViewModelWithInputs()
        applySettingsUiState(settingsViewModel.currentState())
    }

    private func loadPersistedOnboardingProfile() {
        applyOnboardingUiState(onboardingViewModel.loadPersistedProfile())
    }

    private func loadPersistedSettingsProfile() {
        applySettingsUiState(settingsViewModel.loadPersistedProfile())
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

    @objc private func openSettingsFromMvpMenu() {
        pushMvpScreen(mode: .settingsProfileFlow, postSubmitMode: .mapHome)
    }

    @objc private func openMeasureFromMvpMenu() {
        if onboardingPersistenceUseCase.loadProfile()?.onboardingComplete == true {
            pushMvpScreen(
                mode: .measurementRunFlow,
                autoStartMeasurementRunOnAppear: true
            )
        } else {
            pushMvpScreen(
                mode: .onboardingFlow,
                postSubmitMode: .measurementRunFlow,
                postSubmitAutoStartMeasurement: true
            )
        }
    }

    @objc private func openHistoryFromMvpMenu() {
        pushMvpScreen(mode: .measurementHistoryFlow)
    }

    @objc private func returnToMapHome() {
        if navigationController?.viewControllers.first === self {
            replaceRootWithMvpMenu()
        } else {
            navigationController?.popViewController(animated: true)
        }
    }

    private func pushMvpScreen(
        mode: DisplayMode,
        postSubmitMode: DisplayMode? = nil,
        postSubmitAutoStartMeasurement: Bool = false,
        autoStartAfterPreflight: Bool = false,
        autoStartMeasurementRunOnAppear: Bool = false
    ) {
        if navigationController == nil {
            if mode == .mapHome {
                replaceRootWithMvpMenu()
            }
            return
        }
        if mode == .mapHome {
            navigationController?.popToRootViewController(animated: true)
            return
        }
        let target = HarnessViewController(
            displayMode: mode,
            onboardingUiImplementation: .uikit,
            postSubmitMode: postSubmitMode,
            postSubmitAutoStartMeasurement: postSubmitAutoStartMeasurement,
            autoStartMeasurementAfterPreflight: autoStartAfterPreflight,
            autoStartMeasurementRunOnAppear: autoStartMeasurementRunOnAppear
        )
        navigationController?.pushViewController(target, animated: true)
    }

    private func requestLocationPermissionIfNeeded(forceRequest: Bool = false) {
        let status = CLLocationManager.authorizationStatus()
        if forceRequest && status != .authorizedWhenInUse && status != .authorizedAlways {
            locationPermissionManager.requestWhenInUseAuthorization()
            return
        }
        if status == .notDetermined {
            locationPermissionManager.requestWhenInUseAuthorization()
        }
    }

    private func replaceRootWithMvpMenu() {
        guard let window = view.window else { return }
        let menu = HarnessViewController(displayMode: .mapHome, onboardingUiImplementation: .uikit)
        window.rootViewController = UINavigationController(rootViewController: menu)
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
            if displayMode == .settingsProfileFlow {
                submitSettings()
            } else {
                submitOnboarding()
            }
            return false
        }
        dismissOnboardingKeyboard()
        return true
    }

    func textFieldDidBeginEditing(_ textField: UITextField) {
        guard displayMode == .onboardingFlow || displayMode == .settingsProfileFlow else { return }
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

    @discardableResult
    private func syncSettingsViewModelWithInputs() -> SettingsProfileUiState {
        let mode: CollectionMode = settingsModeControl.selectedSegmentIndex == 1 ? .fccChallenge : .testing
        _ = settingsViewModel.onCollectionModeChanged(collectionMode: mode)
        _ = settingsViewModel.onNameChanged(name: onboardingNameField.text ?? "")
        _ = settingsViewModel.onPhoneChanged(phone: onboardingPhoneField.text ?? "")
        _ = settingsViewModel.onEmailChanged(email: onboardingEmailField.text ?? "")
        let state = settingsViewModel.onAcknowledgementChanged(acknowledged: onboardingAckSwitch.isOn)
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

    private func applySettingsUiState(_ state: SettingsProfileUiState) {
        let selectedIndex = state.collectionMode == .fccChallenge ? 1 : 0
        if settingsModeControl.selectedSegmentIndex != selectedIndex {
            settingsModeControl.selectedSegmentIndex = selectedIndex
        }
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
