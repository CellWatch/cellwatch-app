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
    /// Asynchronous for the same reason: it counts rows in the database.
    private let syncStatusProvider: (@escaping (SyncStatusSummary?) -> Void) -> Void
    private let onMeasure: () -> Void
    private let onHistory: () -> Void
    private let onSettings: () -> Void

    private let scaffold = MapScreenScaffold()
    private let statusCardHolder = UIView()
    private let measureButton = Components.primaryButton(MapHomeCopy.shared.MEASURE)
    private let overlayButton = Components.secondaryButton("")
    /// Circular, sitting on the map itself rather than in the button column.
    /// The filled arrow is the convention every maps app uses for "put me
    /// back where I am", which is worth more here than a label.
    private let recenterButton = Components.mapOverlayButton(systemImage: "location.fill")

#if canImport(MapboxMaps)
    private var mapView: MapView?
    private var pointAnnotations: PointAnnotationManager?
    private var polygonAnnotations: PolygonAnnotationManager?
    private var countAnnotations: PointAnnotationManager?
    private var mapEventTokens: [AnyCancelable] = []
    /// Coalesces camera movement into one grid rebuild.
    private var boundsRebuild: DispatchWorkItem?
#endif

    init(
        viewModel: MapHomeViewModel,
        inputProvider: @escaping () -> MapHomeInput,
        measurementLocationProvider: @escaping (@escaping ([MapHomeMeasurementLocationSnapshot]) -> Void) -> Void,
        syncStatusProvider: @escaping (@escaping (SyncStatusSummary?) -> Void) -> Void,
        onMeasure: @escaping () -> Void,
        onHistory: @escaping () -> Void,
        onSettings: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.inputProvider = inputProvider
        self.measurementLocationProvider = measurementLocationProvider
        self.syncStatusProvider = syncStatusProvider
        self.onMeasure = onMeasure
        self.onHistory = onHistory
        self.onSettings = onSettings
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("created in code") }

    override func loadView() {
        let historyButton = Components.secondaryButton(HistoryCopy.shared.TITLE)
        let settingsButton = Components.secondaryButton(SettingsCopy.shared.TITLE)
        historyButton.addTarget(self, action: #selector(historyTapped), for: .touchUpInside)
        settingsButton.addTarget(self, action: #selector(settingsTapped), for: .touchUpInside)
        measureButton.addTarget(self, action: #selector(measureTapped), for: .touchUpInside)
        overlayButton.addTarget(self, action: #selector(overlayTapped), for: .touchUpInside)
        recenterButton.addTarget(self, action: #selector(recenterTapped), for: .touchUpInside)

        let secondaryRow = UIStackView(arrangedSubviews: [historyButton, settingsButton])
        secondaryRow.axis = .horizontal
        secondaryRow.distribution = .fillEqually
        secondaryRow.spacing = Theme.Space.s

        scaffold.addToPanel(statusCardHolder, overlayButton, measureButton, secondaryRow)
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
        syncStatusProvider { [weak self] status in
            guard let self, let status else { return }
            // Rebuilt rather than copied: Kotlin's generated doCopy takes every
            // parameter from Swift, so it is no shorter than this.
            let base = self.inputProvider()
            let input = MapHomeInput(
                onboardingComplete: base.onboardingComplete,
                recentRunCount: base.recentRunCount,
                pendingCountsKnown: base.pendingCountsKnown,
                pendingMeasurements: base.pendingMeasurements,
                pendingSubmissions: base.pendingSubmissions,
                syncStatus: status
            )
            self.render(self.viewModel.onInputChanged(input: input))
        }
    }

    // MARK: - Events

    @objc private func measureTapped() { onMeasure() }
    @objc private func historyTapped() { onHistory() }
    @objc private func settingsTapped() { onSettings() }

    // MARK: - Rendering

    private func render(_ state: MapHomeUiState) {
        // Labelled with the destination rather than the current mode: a button
        // reading MapHomeCopy.shared.HEX_GRID while showing the hex grid is ambiguous.
        overlayButton.setTitle(
            state.overlayMode == MapHomeOverlayMode.hexGrid ? MapHomeCopy.shared.SHOW_POINTS_ONLY : MapHomeCopy.shared.SHOW_COVERAGE_GRID,
            for: .normal
        )
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

        // The blue dot. Without it there is no way to tell where the map is
        // relative to where you are standing, which is the whole question
        // this screen exists to answer.
        map.location.options.puckType = .puck2D(.makeDefault(showBearing: false))

        // Somewhere useful rather than the whole globe. Georgia Tech is the
        // starting frame only until the device reports a fix; the camera
        // follows the puck as soon as one arrives, which is what a user who
        // is standing somewhere else expects. Before this it stayed on
        // campus forever unless a measurement had already been taken.
        map.mapboxMap.setCamera(
            to: CameraOptions(
                center: CLLocationCoordinate2D(latitude: 33.7756, longitude: -84.3963),
                zoom: 12.5
            )
        )
        followPuck(animated: false)

        map.addSubview(recenterButton)
        NSLayoutConstraint.activate([
            recenterButton.trailingAnchor.constraint(
                equalTo: map.trailingAnchor, constant: -Theme.Space.m
            ),
            recenterButton.bottomAnchor.constraint(
                equalTo: map.bottomAnchor, constant: -Theme.Space.l
            ),
        ])

        mapEventTokens.append(
            map.mapboxMap.onStyleLoaded.observeNext { [weak self] _ in
                guard let self else { return }
                self.registerMarkerImage()
                self.renderFeatures(self.viewModel.currentState())
            }
        )
        // A plain tap recogniser rather than a map event: the tap signal moved
        // between Mapbox versions, and converting a screen point to a
        // coordinate is stable across them.
        let cellTap = UITapGestureRecognizer(target: self, action: #selector(mapTapped(_:)))
        map.addGestureRecognizer(cellTap)

        mapEventTokens.append(
            map.mapboxMap.onMapIdle.observeNext { [weak self] _ in
                guard let self, let map = self.mapView else { return }
                self.reportBounds(of: map)
            }
        )
        mapEventTokens.append(
            // Idle alone is not enough. A camera driven by the viewport
            // plugin - which is how the recentre button moves it - can settle
            // without ever emitting idle, and the grid then stays frozen at
            // whatever bounds it last saw. Coalesced so a pan costs one
            // rebuild rather than one per frame.
            map.mapboxMap.onCameraChanged.observe { [weak self] _ in
                self?.scheduleBoundsRebuild()
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
        if (try? map.mapboxMap.image(withId: Self.countImageId)) == nil {
            try? map.mapboxMap.addImage(Self.countBadgeImage(), id: Self.countImageId)
        }
        guard (try? map.mapboxMap.image(withId: Self.markerImageId)) == nil else { return }
        try? map.mapboxMap.addImage(Self.markerImage(), id: Self.markerImageId)
    }

    private static let markerImageId = "cellwatch-measurement-pin"
    private static let countImageId = "cellwatch-hex-count"

    /// The badge behind a cell's count. Opaque green; only the cell fill is
    /// translucent.
    private static func countBadgeImage() -> UIImage {
        let diameter: CGFloat = 26
        let size = CGSize(width: diameter, height: diameter)
        return UIGraphicsImageRenderer(size: size).image { _ in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 1, dy: 1)
            UIColor(red: 0x2E / 255.0, green: 0x7D / 255.0, blue: 0x32 / 255.0, alpha: 1.0).setFill()
            UIBezierPath(ovalIn: rect).fill()
        }
    }

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

    /// Which cell was tapped is computed from the coordinate rather than
    /// hit-tested against the annotations: H3 is a spatial index, so the cell
    /// is a pure function of the point - and on Android the annotation click
    /// listener never fired at all, because the point and badge managers sit
    /// above the polygons and swallow the gesture.
    @objc private func mapTapped(_ recognizer: UITapGestureRecognizer) {
#if canImport(MapboxMaps)
        guard let map = mapView else { return }
        let coordinate = map.mapboxMap.coordinate(for: recognizer.location(in: map))
        guard let cell = H3Grid.shared.cellAt(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            resolution: Int32(H3Resolution.shared.OVERLAY)
        ) else { return }
        render(viewModel.onCellSelected(cellId: cell))
#endif
    }

    private func scheduleBoundsRebuild() {
        boundsRebuild?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self, let map = self.mapView else { return }
            self.reportBounds(of: map)
        }
        boundsRebuild = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25, execute: work)
    }

    /// Tells the view model what the map is now showing.
    ///
    /// Called after idle rather than every frame: the grid is recomputed from
    /// these bounds, and doing that mid-gesture would resample the lattice on
    /// every pan tick.
    private func reportBounds(of map: MapView) {
#if canImport(MapboxMaps)
        render(viewModel.onZoomChanged(zoomLevel: map.mapboxMap.cameraState.zoom))
        let bounds = map.mapboxMap.coordinateBounds(
            for: CameraOptions(cameraState: map.mapboxMap.cameraState)
        )
        render(viewModel.onBoundsChanged(
            north: bounds.northeast.latitude,
            south: bounds.southwest.latitude,
            east: bounds.northeast.longitude,
            west: bounds.southwest.longitude
        ))
#endif
    }

    /// Moves the camera to the device's own position and keeps it there
    /// until the user pans away.
    ///
    /// `viewport` rather than a one-shot `setCamera`: the first fix on a cold
    /// start can be seconds away, and a one-shot call made before it arrives
    /// silently does nothing. Following waits for the puck.
    private func followPuck(animated: Bool = true) {
#if canImport(MapboxMaps)
        guard let map = mapView else { return }
        let follow = map.viewport.makeFollowPuckViewportState(
            // 13, not the 14 a navigation app would use. The overlay is drawn
            // at H3 resolution 8, whose cells are about a kilometre across;
            // at zoom 14 the screen fits inside a single hexagon and the
            // grid is invisible - it looks broken rather than close up.
            options: FollowPuckViewportStateOptions(zoom: 13, bearing: .constant(0))
        )
        map.viewport.transition(
            to: follow,
            transition: animated ? map.viewport.makeDefaultViewportTransition()
                                 : map.viewport.makeImmediateViewportTransition()
        ) { [weak self] _ in
            // Back to idle once the camera has arrived, rather than staying
            // in follow mode. A camera that tracks the puck never settles, so
            // `onMapIdle` stops firing - and that is the only thing that
            // recomputes the hex grid, so the overlay would freeze at
            // whatever bounds it last saw. This is a recentre, not a
            // navigation mode.
            guard let self, let map = self.mapView else { return }
            map.viewport.idle()
            self.reportBounds(of: map)
        }
#endif
    }

    @objc private func recenterTapped() {
        followPuck()
    }

    @objc private func overlayTapped() {
        let next = viewModel.currentState().overlayMode == MapHomeOverlayMode.hexGrid
            ? MapHomeOverlayMode.points
            : MapHomeOverlayMode.hexGrid
        render(viewModel.setOverlayMode(mode: next))
    }

    private func renderFeatures(_ state: MapHomeUiState) {
#if canImport(MapboxMaps)
        guard let map = mapView else { return }
        // Made before the points so a pin is never hidden under a cell fill.
        if polygonAnnotations == nil {
            polygonAnnotations = map.annotations.makePolygonAnnotationManager(id: "mapHomeProductCells")
        }
        if pointAnnotations == nil {
            pointAnnotations = map.annotations.makePointAnnotationManager(id: "mapHomeProductPoints")
        }
        // Made last so the badges sit above both the fills and the pins.
        if countAnnotations == nil {
            let manager = map.annotations.makePointAnnotationManager(id: "mapHomeProductCounts")
            // Overlap allowed: suppressing a badge because a neighbour is
            // close would silently hide data.
            manager.iconAllowOverlap = true
            manager.textAllowOverlap = true
            countAnnotations = manager
        }

        let hexMode = state.overlayMode == MapHomeOverlayMode.hexGrid
        polygonAnnotations?.annotations = hexMode ? state.hexCells.compactMap { cell in
            guard cell.hasBoundary else { return nil }
            let ring = cell.boundary.map {
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
            }
            // Turf closes the ring itself, but only when the first and last
            // coordinates match, which H3 does not supply.
            var annotation = PolygonAnnotation(polygon: Polygon([ring + [ring[0]]]))
            // frozenApp's semantics: the outline draws the grid, the fill
            // marks which cells hold data. Clear rather than absent, so an
            // empty cell is still a visible part of the tiling.
            annotation.fillColor = StyleColor(cell.hasMeasurements ? Self.hexFill : .clear)
            // fillOpacity is deliberately left alone. Setting it to 0 for an
            // empty cell suppressed the outline as well - the whole fill layer
            // stops drawing - so the grid vanished and only occupied cells
            // showed. Transparency belongs in the colour, not the opacity.
            annotation.fillOutlineColor = StyleColor(Theme.Color.primary)
            return annotation
        } : []

        // The count on the cell, as frozenApp showed it: a filled green badge
        // with the number in white. Without it the fill says only "something
        // happened here", the least useful half of what the overlay knows.
        countAnnotations?.annotations = hexMode ? state.hexCells.filter { $0.hasMeasurements }.map { cell in
            var annotation = PointAnnotation(
                coordinate: CLLocationCoordinate2D(
                    latitude: cell.centerLatitude,
                    longitude: cell.centerLongitude
                )
            )
            annotation.iconImage = Self.countImageId
            annotation.textField = "\(cell.measurementCount)"
            annotation.textColor = StyleColor(.white)
            annotation.textSize = 12
            return annotation
        } : []

        // Points stay visible in both modes: the hexagon says how many, the
        // pins say where, and hiding them made the grid look like the only data.
        pointAnnotations?.annotations = state.points.map { point in
            var annotation = PointAnnotation(
                coordinate: CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
            )
            annotation.iconImage = Self.markerImageId
            annotation.iconSize = hexMode ? 0.7 : 1.0
            return annotation
        }
#endif
    }

    /// Translucent green, as frozenApp used: `cw_green_light` with its alpha
    /// halved so the basemap stays readable underneath.
    private static let hexFill = UIColor(red: 0x4C / 255.0, green: 0xAF / 255.0, blue: 0x50 / 255.0, alpha: 0.5)
}
