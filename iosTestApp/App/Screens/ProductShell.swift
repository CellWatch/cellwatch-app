import UIKit
import sharedKit

/// Hosts the product navigation graph.
///
/// The shared `Navigator` owns the back stack and this maps each `Destination`
/// to a view controller, so what screens exist is decided once in `commonMain`
/// rather than separately per platform. The harness's launch-mode enum is
/// untouched and stays an instrumentation affordance.
///
/// Destinations without a screen yet render `PlaceholderScreenViewController`
/// rather than being omitted: the graph is then walkable end to end from the
/// first screen onward, and unfinished work is visible instead of looking like
/// a dead button.
final class ProductShell: NSObject {

    /// Resolved once, from the packaged runtime resource.
    ///
    /// A failure means the build has no usable runtime configuration, which is
    /// a blocking error rather than something a screen can retry past.
    private enum Container {
        static let result: Result<ProductContainer, Error> = {
            do {
                return .success(try IosProductContainerFactory.shared.create(
                    onboardingStore: OnboardingUserDefaultsStore(),
                    contact: {
                    let profile = OnboardingUserDefaultsStore().loadProfile()
                    return ProductSubmissionIdentity(
                        appName: IosProductServices.companion.appName(),
                        appVersion: IosProductServices.companion.appVersion(),
                        provider: nil,
                        contactName: profile?.name,
                        contactEmail: profile?.email,
                        contactPhone: profile?.phone
                    )
                }))
            } catch {
                return .failure(error)
            }
        }()

        static var errorText: String? {
            if case .failure(let error) = result { return error.localizedDescription }
            return nil
        }
    }

    /// The user's collection-mode choice, or the challenge default.
    ///
    /// Was hardcoded to `.fccChallenge`, so the settings toggle could not
    /// actually stop submissions being built.
    private static func collectionMode() -> CollectionMode {
        (try? Container.result.get())?.collectionMode() ?? CollectionMode.fccChallenge
    }

    /// Carried from the consent step into the profile save; the mode the user
    /// chose has to reach the saved profile or the choice is cosmetic.
    private var consentMode: CollectionMode = CollectionMode.fccChallenge
    private var consentAcknowledged = false

    private let navigator: Navigator
    private let navigationController = UINavigationController()

    override init() {
        let decision = AppLaunchRoutingUseCase().resolve(
            input: AppLaunchRoutingInput(
                onboardingComplete: OnboardingUserDefaultsStore().loadProfile()?.onboardingComplete == true,
                // Previously hardcoded true, which meant a build with no usable
                // runtime configuration still offered a Measure button that
                // could only fail once a measurement was under way.
                runtimeProfileReady: (try? Container.result.get()) != nil
            )
        )
        navigator = Navigator(start: decision.toDestination())
        super.init()
        navigationController.navigationBar.prefersLargeTitles = true
        navigationController.delegate = self
        navigationController.setViewControllers([screen(for: navigator.current)], animated: false)
    }

    var rootViewController: UIViewController { navigationController }

    /// Replaces the stack so it mirrors the shared graph. The navigator is the
    /// source of truth; UIKit follows it rather than the reverse.
    private func syncStack(animated: Bool = true) {
        navigationController.setViewControllers(
            navigator.backStack.map { screen(for: $0) },
            animated: animated
        )
    }

    private func go(to destination: Destination) {
        navigator.goTo(destination: destination)
        syncStack()
    }

    private func reset(to destination: Destination) {
        navigator.resetTo(destination: destination)
        syncStack()
    }

    private func screen(for destination: Destination) -> UIViewController {
        switch destination {
        case is DestinationDataUse:
            return DataUseScreenViewController(
                onContinue: { [weak self] in self?.go(to: DestinationCollectionChoice.shared) }
            )

        case is DestinationCollectionChoice:
            return CollectionChoiceScreenViewController(
                viewModel: ConsentViewModel(),
                onContinue: { [weak self] mode, acknowledged in
                    self?.consentMode = mode
                    self?.consentAcknowledged = acknowledged
                    self?.go(to: DestinationOnboarding.shared)
                }
            )

        case is DestinationOnboarding:
            return OnboardingScreenViewController(
                viewModel: OnboardingProfileViewModel(
                    validationUseCase: OnboardingValidationUseCase(),
                    persistenceUseCase: OnboardingPersistenceUseCase(store: OnboardingUserDefaultsStore()),
                    // The mode the user picked on the consent step, not a
                    // default: recording anything else would contradict the
                    // choice they were just shown the consequences of.
                    collectionMode: consentMode
                ),
                acknowledged: consentAcknowledged,
                onComplete: { [weak self] in
                    // Reset rather than push: the back button must not return
                    // to onboarding once a profile is saved.
                    self?.reset(to: DestinationMapHome.shared)
                }
            )
        case is DestinationMapHome:
            return MapHomeScreenViewController(
                viewModel: MapHomeViewModel(minHexGridZoom: 0.0),
                inputProvider: {
                    // Counts stay zero until history is wired (task 2.1); what
                    // matters here is that a saved profile enables Measure.
                    MapHomeInput(
                        onboardingComplete: OnboardingUserDefaultsStore().loadProfile()?.onboardingComplete == true,
                        recentRunCount: 0,
                        pendingCountsKnown: false,
                        pendingMeasurements: 0,
                        pendingSubmissions: 0,
                        syncStatus: nil
                    )
                },
                measurementLocationProvider: { completion in
                    guard case .success(let container) = Container.result else {
                        completion([])
                        return
                    }
                    container.recentMeasurementLocations(limit: 500) { snapshots, _ in
                        DispatchQueue.main.async { completion(snapshots ?? []) }
                    }
                },
                syncStatusProvider: { completion in
                    guard case .success(let container) = Container.result else {
                        completion(nil)
                        return
                    }
                    container.syncStatus(inProgress: false) { summary, _ in
                        DispatchQueue.main.async { completion(summary) }
                    }
                },
                onMeasure: { [weak self] in self?.go(to: DestinationMeasurementStart.shared) },
                onHistory: { [weak self] in self?.go(to: DestinationHistory.shared) },
                onSettings: { [weak self] in self?.go(to: DestinationSettings.shared) }
            )

        case is DestinationMeasurementStart:
            return MeasurementStartScreenViewController(
                viewModel: MeasurementStartViewModel(collectionMode: Self.collectionMode()),
                hasRuntimeProfile: (try? Container.result.get()) != nil,
                onReadyToRun: { [weak self] inVehicle in
                    self?.go(to: DestinationMeasurementRun(inVehicle: inVehicle))
                }
            )

        case let run as DestinationMeasurementRun:
            guard case .success(let container) = Container.result else {
                return PlaceholderScreenViewController(
                    titleText: "Cannot start",
                    message: "Measurement is unavailable: \(Container.errorText ?? "runtime configuration missing").",
                    tone: .warning
                )
            }
            return MeasurementRunScreenViewController(
                viewModel: MeasurementRunViewModel(
                    container: container,
                    mode: Self.collectionMode(),
                    inVehicle: run.inVehicle
                ),
                onDone: { [weak self] in self?.reset(to: DestinationMapHome.shared) },
                onMeasureAgain: { [weak self] in self?.reset(to: DestinationMeasurementStart.shared) }
            )

        case is DestinationHistory:
            guard case .success(let container) = Container.result else {
                return PlaceholderScreenViewController(
                    titleText: "History",
                    message: "History is unavailable: \(Container.errorText ?? "runtime configuration missing").",
                    tone: .warning
                )
            }
            return HistoryScreenViewController(
                viewModel: MeasurementHistoryViewModel(),
                snapshotProvider: { completion in
                    container.historySnapshot { snapshot, _ in
                        guard let snapshot else { return }
                        DispatchQueue.main.async { completion(snapshot) }
                    }
                },
                onRetry: { completion in
                    container.retryPendingUploads { snapshot, _ in
                        guard let snapshot else { return }
                        DispatchQueue.main.async { completion(snapshot) }
                    }
                },
                onExport: { [weak self] in self?.go(to: DestinationExport.shared) },
                onBack: { [weak self] in self?.reset(to: DestinationMapHome.shared) }
            )

        case is DestinationSettings:
            guard case .success(let container) = Container.result else {
                return PlaceholderScreenViewController(
                    titleText: "Settings",
                    message: "Settings are unavailable: \(Container.errorText ?? "runtime configuration missing").",
                    tone: .warning
                )
            }
            return SettingsScreenViewController(
                viewModel: SettingsProfileViewModel(
                    validationUseCase: OnboardingValidationUseCase(),
                    persistenceUseCase: OnboardingPersistenceUseCase(store: OnboardingUserDefaultsStore())
                ),
                diagnosticsProvider: { completion in
                    container.diagnostics { value, _ in
                        guard let value else { return }
                        DispatchQueue.main.async { completion(value) }
                    }
                },
                onSaved: { [weak self] in self?.reset(to: DestinationMapHome.shared) },
                onBack: { [weak self] in self?.reset(to: DestinationMapHome.shared) }
            )

        case is DestinationExport:
            guard case .success(let container) = Container.result else {
                return PlaceholderScreenViewController(
                    titleText: "Export",
                    message: "Export is unavailable: \(Container.errorText ?? "runtime configuration missing").",
                    tone: .warning
                )
            }
            return ExportScreenViewController(
                buildFcc: { completion in
                    container.exportFccJson { document, _ in
                        guard let document else { return }
                        DispatchQueue.main.async { completion(document) }
                    }
                },
                buildExtended: { completion in
                    container.exportExtendedJson { document, _ in
                        guard let document else { return }
                        DispatchQueue.main.async { completion(document) }
                    }
                },
                onBack: { [weak self] in self?.reset(to: DestinationMapHome.shared) }
            )

        case let blocking as DestinationBlockingError:
            return PlaceholderScreenViewController(
                titleText: "Cannot start",
                message: blocking.reason,
                tone: .warning
            )
        default:
            return PlaceholderScreenViewController(
                titleText: label(for: destination),
                message: "This screen is not built yet. See UI_DELIVERY_PLAN.md for where it lands.",
                tone: .neutral
            )
        }
    }

    private func label(for destination: Destination) -> String {
        switch destination {
        case is DestinationDataUse: return "Data use"
        case is DestinationCollectionChoice: return "Collection mode"
        case is DestinationMapHome: return "Map home"
        case is DestinationMeasurementStart: return "Start measurement"
        case is DestinationMeasurementRun: return "Measurement"
        case is DestinationHistory: return "History"
        case is DestinationSettings: return "Settings"
        case is DestinationExport: return "Export"
        default: return "Screen"
        }
    }
}

/// Stands in for a destination in the graph that has no screen yet.
final class PlaceholderScreenViewController: UIViewController {

    private let titleText: String
    private let message: String
    private let tone: Components.StatusTone

    init(titleText: String, message: String, tone: Components.StatusTone) {
        self.titleText = titleText
        self.message = message
        self.tone = tone
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("created in code") }

    override func loadView() {
        let scaffold = ScreenScaffold()
        scaffold.addContent(Components.statusCard(message, tone: tone))
        view = scaffold
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = titleText
    }

}

extension ProductShell: UINavigationControllerDelegate {

    /// Keeps the shared navigator in step when UIKit pops on its own.
    ///
    /// The back button and the interactive swipe pop UIKit's stack directly;
    /// nothing routes through `Navigator`, so without this its `current` goes
    /// stale the moment a user presses Back. The symptom is quiet - navigating
    /// to the screen you just left is ignored, because the navigator still
    /// believes it is there - which is exactly the platform-owns-its-own-stack
    /// drift the shared graph exists to prevent.
    func navigationController(
        _ navigationController: UINavigationController,
        didShow viewController: UIViewController,
        animated: Bool
    ) {
        while navigationController.viewControllers.count < navigator.backStack.count,
              navigator.back() {}
    }
}
