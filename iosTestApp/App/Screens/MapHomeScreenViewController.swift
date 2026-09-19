import UIKit
import sharedKit
#if canImport(MapboxMaps)
import MapboxMaps
#endif

/// The app's home: a map of where you have measured, with the actions.
///
/// Shared `MapHomeViewModel` owns all state - copy, sync status, map
/// interaction and the features to draw - so both platforms render the same
/// thing from the same source. This file only renders and forwards events.
final class MapHomeScreenViewController: UIViewController {

    private let viewModel: MapHomeViewModel
    private let inputProvider: () -> MapHomeInput
    /// Asynchronous: the points come from the database, off the main thread.
    private let measurementLocationProvider: (@escaping ([MapHomeMeasurementLocationSnapshot]) -> Void) -> Void
    private let onMeasure: () -> Void
    private let onHistory: () -> Void
    private let onSettings: () -> Void

    private let scaffold = MapScreenScaffold()
    private let statusCardHolder = UIView()
    private let measureButton = Components.primaryButton("Measure")

#if canImport(MapboxMaps)
    private var mapView: MapView?
    private var pointAnnotations: PointAnnotationManager?
    private var mapEventTokens: [AnyCancelable] = []
#endif

    init(
        viewModel: MapHomeViewModel,
        inputProvider: @escaping () -> MapHomeInput,
        measurementLocationProvider: @escaping (@escaping ([MapHomeMeasurementLocationSnapshot]) -> Void) -> Void,
        onMeasure: @escaping () -> Void,
        onHistory: @escaping () -> Void,
        onSettings: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.inputProvider = inputProvider
        self.measurementLocationProvider = measurementLocationProvider
        self.onMeasure = onMeasure
        self.onHistory = onHistory
        self.onSettings = onSettings
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("created in code") }

    override func loadView() {
        let historyButton = Components.secondaryButton("History & sync")
        let settingsButton = Components.secondaryButton("Settings")
        historyButton.addTarget(self, action: #selector(historyTapped), for: .touchUpInside)
        settingsButton.addTarget(self, action: #selector(settingsTapped), for: .touchUpInside)
        measureButton.addTarget(self, action: #selector(measureTapped), for: .touchUpInside)

        let secondaryRow = UIStackView(arrangedSubviews: [historyButton, settingsButton])
        secondaryRow.axis = .horizontal
        secondaryRow.distribution = .fillEqually
        secondaryRow.spacing = Theme.Space.s

        scaffold.addToPanel(statusCardHolder, measureButton, secondaryRow)
        view = scaffold
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "CellWatch"
        navigationController?.navigationBar.prefersLargeTitles = false
        installMap()
        refresh()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // Re-read on every appearance: returning from a measurement or from
        // history should show the new counts, not the ones from launch.
        refresh()
    }

    private func refresh() {
        render(viewModel.onInputChanged(input: inputProvider()))
        // Reloaded on every appearance rather than once: returning from a run
        // is exactly when there is a new point to draw.
        measurementLocationProvider { [weak self] snapshots in
            guard let self else { return }
            self.render(self.viewModel.onMeasurementsLoaded(snapshots: snapshots))
        }
    }

    // MARK: - Events

    @objc private func measureTapped() { onMeasure() }
    @objc private func historyTapped() { onHistory() }
    @objc private func settingsTapped() { onSettings() }

    // MARK: - Rendering

    private func render(_ state: MapHomeUiState) {
        measureButton.isEnabled = state.canStartMeasurement
        measureButton.alpha = state.canStartMeasurement ? 1.0 : 0.5

        statusCardHolder.subviews.forEach { $0.removeFromSuperview() }
        let card = Components.statusCard(state.syncSummary, tone: tone(for: state.syncStateKey))
        card.translatesAutoresizingMaskIntoConstraints = false
        statusCardHolder.addSubview(card)
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: statusCardHolder.topAnchor),
            card.bottomAnchor.constraint(equalTo: statusCardHolder.bottomAnchor),
            card.leadingAnchor.constraint(equalTo: statusCardHolder.leadingAnchor),
            card.trailingAnchor.constraint(equalTo: statusCardHolder.trailingAnchor),
        ])

        renderFeatures(state)
    }

    private func tone(for key: MapHomeSyncStateKey) -> Components.StatusTone {
        switch key {
        case .synced: return .success
        case .pending: return .warning
        default: return .neutral
        }
    }

    // MARK: - Map

    private func installMap() {
#if canImport(MapboxMaps)
        guard let token = RuntimeConfigSource.mapboxAccessToken()?
            .trimmingCharacters(in: .whitespacesAndNewlines), !token.isEmpty else {
            // No token is a configuration problem, not a crash. The panel still
            // works, so the app stays usable.
            SharedLog.shared.w(tag: "MapHome", message: "no Mapbox token; map surface stays empty", throwable: nil)
            return
        }
        MapboxOptions.accessToken = token

        let map = MapView(frame: .zero, mapInitOptions: MapInitOptions())
        map.translatesAutoresizingMaskIntoConstraints = false
        scaffold.mapContainer.addSubview(map)
        NSLayoutConstraint.activate([
            map.topAnchor.constraint(equalTo: scaffold.mapContainer.topAnchor),
            map.leadingAnchor.constraint(equalTo: scaffold.mapContainer.leadingAnchor),
            map.trailingAnchor.constraint(equalTo: scaffold.mapContainer.trailingAnchor),
            map.bottomAnchor.constraint(equalTo: scaffold.mapContainer.bottomAnchor),
        ])
        mapView = map

        // Somewhere useful rather than the whole globe. The app is used while
        // standing in the place being measured, so once there are measurements
        // the camera moves to the most recent; until then this is the project's
        // home campus.
        map.mapboxMap.setCamera(
            to: CameraOptions(
                center: CLLocationCoordinate2D(latitude: 33.7756, longitude: -84.3963),
                zoom: 12.5
            )
        )

        mapEventTokens.append(
            map.mapboxMap.onStyleLoaded.observeNext { [weak self] _ in
                guard let self else { return }
                self.registerMarkerImage()
                self.renderFeatures(self.viewModel.currentState())
            }
        )
        mapEventTokens.append(
            map.mapboxMap.onMapIdle.observeNext { [weak self] _ in
                guard let self, let map = self.mapView else { return }
                self.render(self.viewModel.onZoomChanged(zoomLevel: map.mapboxMap.cameraState.zoom))
            }
        )
#endif
    }

#if canImport(MapboxMaps)
    /// Registers the pin image with the style.
    ///
    /// This is why pins never appeared: the annotations set
    /// `iconImage = "marker-15"`, a Maki name, but in Mapbox v11 an iconImage
    /// must reference an image present **in the style**, and the default
    /// Standard style does not carry Maki icons. The icon resolved to nothing,
    /// so the annotation drew nothing - no error, just an empty map.
    private func registerMarkerImage() {
        guard let map = mapView else { return }
        guard (try? map.mapboxMap.image(withId: Self.markerImageId)) == nil else { return }
        try? map.mapboxMap.addImage(Self.markerImage(), id: Self.markerImageId)
    }

    private static let markerImageId = "cellwatch-measurement-pin"

    /// Drawn rather than shipped as an asset, so it follows the palette and
    /// there is no image to keep in sync with the theme.
    private static func markerImage() -> UIImage {
        let diameter: CGFloat = 18
        let size = CGSize(width: diameter, height: diameter)
        return UIGraphicsImageRenderer(size: size).image { context in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 2, dy: 2)
            Theme.Color.primary.setFill()
            UIColor.white.setStroke()
            let circle = UIBezierPath(ovalIn: rect)
            circle.lineWidth = 2
            circle.fill()
            circle.stroke()
            _ = context
        }
    }
#endif

    private func renderFeatures(_ state: MapHomeUiState) {
#if canImport(MapboxMaps)
        guard let map = mapView else { return }
        if pointAnnotations == nil {
            pointAnnotations = map.annotations.makePointAnnotationManager(id: "mapHomeProductPoints")
        }
        pointAnnotations?.annotations = state.points.map { point in
            var annotation = PointAnnotation(
                coordinate: CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
            )
            annotation.iconImage = Self.markerImageId
            annotation.iconSize = 1.0
            return annotation
        }
#endif
    }
}
