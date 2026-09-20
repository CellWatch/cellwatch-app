package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.MapScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeInput
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeOverlayMode
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeMeasurementLocationSnapshot
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusSummary
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeSyncStateKey
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeUiState
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeViewModel

/**
 * The app's home. Android counterpart of `MapHomeScreenViewController`.
 *
 * Same shared [MapHomeViewModel], same components, same template, so both
 * platforms render the same state.
 */
class MapHomeScreen(
    private val context: Context,
    private val viewModel: MapHomeViewModel,
    private val inputProvider: () -> MapHomeInput,
    /**
     * Asynchronous: the points come from the database. Nothing supplied these
     * before, so the map had no points at all - separate from, and upstream of,
     * the icon defect fixed in 1.2a.
     */
    private val measurementLocationProvider: ((List<MapHomeMeasurementLocationSnapshot>) -> Unit) -> Unit,
    /** Asynchronous for the same reason: it counts rows in the database. */
    private val syncStatusProvider: ((SyncStatusSummary?) -> Unit) -> Unit,
    onMeasure: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {

    private val scaffold = MapScreenScaffold(context)
    private val statusHolder = FrameLayout(context)
    private val measureButton = Components.primaryButton(context, "Measure")
    private val overlayButton = Components.secondaryButton(context, "")
    private var mapView: MapView? = null
    private var pointAnnotations: PointAnnotationManager? = null
    private var polygonAnnotations: PolygonAnnotationManager? = null
    private var countAnnotations: PointAnnotationManager? = null

    val view: View get() = scaffold

    init {
        measureButton.setOnClickListener { onMeasure() }
        overlayButton.setOnClickListener {
            val next = if (viewModel.currentState().overlayMode == MapHomeOverlayMode.HEX_GRID) {
                MapHomeOverlayMode.POINTS
            } else {
                MapHomeOverlayMode.HEX_GRID
            }
            render(viewModel.setOverlayMode(next))
        }
        val historyButton = Components.secondaryButton(context, "History & sync").apply {
            setOnClickListener { onHistory() }
        }
        val settingsButton = Components.secondaryButton(context, "Settings").apply {
            setOnClickListener { onSettings() }
        }
        val secondaryRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(historyButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                settingsButton,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = with(Theme) { context.dp(Theme.Space.S) }
                },
            )
        }

        scaffold.addToPanel(statusHolder, overlayButton, measureButton, secondaryRow)
        installMap()
        refresh()
    }

    /** Re-read on every appearance, so returning shows new counts and points. */
    fun refresh() {
        render(viewModel.onInputChanged(inputProvider()))
        measurementLocationProvider { snapshots ->
            render(viewModel.onMeasurementsLoaded(snapshots))
        }
        syncStatusProvider { status ->
            if (status != null) {
                render(viewModel.onInputChanged(inputProvider().copy(syncStatus = status)))
            }
        }
    }

    fun onDestroy() {
        mapView?.onDestroy()
        mapView = null
    }

    private fun render(state: MapHomeUiState) {
        // Labelled with the destination rather than the current mode: a
        // button reading "Hex grid" while showing the hex grid is ambiguous.
        overlayButton.text = if (state.overlayMode == MapHomeOverlayMode.HEX_GRID) {
            "Show points only"
        } else {
            "Show coverage grid"
        }
        measureButton.isEnabled = state.canStartMeasurement
        measureButton.alpha = if (state.canStartMeasurement) 1f else 0.5f

        statusHolder.removeAllViews()
        statusHolder.addView(
            Components.statusCard(context, state.syncSummary, tone(state.syncStateKey)),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER },
        )
        renderFeatures(state)
    }

    private fun tone(key: MapHomeSyncStateKey) = when (key) {
        MapHomeSyncStateKey.SYNCED -> Components.StatusTone.SUCCESS
        MapHomeSyncStateKey.PENDING -> Components.StatusTone.WARNING
        else -> Components.StatusTone.NEUTRAL
    }

    private fun installMap() {
        val map = MapView(context)
        map.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        scaffold.mapContainer.addView(map)
        mapView = map

        map.mapboxMap.loadStyle(Style.STANDARD) { style ->
            // Register the pin with the style before any annotation references
            // it. This is why pins never appeared: the old code used the Maki
            // name "marker-15", and the Standard style does not carry Maki
            // icons, so the icon resolved to nothing and the annotation drew
            // nothing - silently.
            style.addImage(MARKER_IMAGE_ID, markerBitmap())
            style.addImage(COUNT_IMAGE_ID, countBadgeBitmap())
            renderFeatures(viewModel.currentState())
        }
        map.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(-84.3963, 33.7756))
                .zoom(12.5)
                .build(),
        )
        map.mapboxMap.addOnMapIdleListener {
            render(viewModel.onZoomChanged(map.mapboxMap.cameraState.zoom))
            // Reported after idle rather than on every frame: the grid is
            // recomputed from these, and doing that mid-gesture would sample
            // the lattice on every pan tick.
            val camera = map.mapboxMap.cameraState
            val bounds = map.mapboxMap.coordinateBoundsForCamera(
                CameraOptions.Builder()
                    .center(camera.center)
                    .zoom(camera.zoom)
                    .bearing(camera.bearing)
                    .pitch(camera.pitch)
                    .padding(camera.padding)
                    .build(),
            )
            render(
                viewModel.onBoundsChanged(
                    north = bounds.northeast.latitude(),
                    south = bounds.southwest.latitude(),
                    east = bounds.northeast.longitude(),
                    west = bounds.southwest.longitude(),
                ),
            )
        }
    }

    private fun renderFeatures(state: MapHomeUiState) {
        val map = mapView ?: return
        val points = pointAnnotations ?: map.annotations.createPointAnnotationManager().also {
            pointAnnotations = it
        }
        // Created below the points so a pin is never hidden under a cell fill.
        val polygons = polygonAnnotations ?: map.annotations.createPolygonAnnotationManager().also {
            polygonAnnotations = it
        }

        // Created last so the badges sit above both the fills and the pins.
        val counts = countAnnotations ?: map.annotations.createPointAnnotationManager().also {
            // Overlap allowed at the manager: suppressing a badge because a
            // neighbour is close would silently hide data. These are
            // manager-level properties in Mapbox v11, not per-annotation.
            it.iconAllowOverlap = true
            it.textAllowOverlap = true
            countAnnotations = it
        }

        polygons.deleteAll()
        points.deleteAll()
        counts.deleteAll()

        val hexMode = state.overlayMode == MapHomeOverlayMode.HEX_GRID
        if (hexMode) {
            val drawable = state.hexCells.filter { it.hasBoundary }
            if (drawable.isNotEmpty()) {
                polygons.create(
                    drawable.map { cell ->
                        PolygonAnnotationOptions()
                            .withPoints(
                                listOf(
                                    // Mapbox wants a closed ring: first vertex
                                    // repeated last, which H3 does not supply.
                                    cell.boundary.map { Point.fromLngLat(it.longitude, it.latitude) } +
                                        cell.boundary.first()
                                            .let { Point.fromLngLat(it.longitude, it.latitude) },
                                ),
                            )
                            // frozenApp's semantics: the outline draws the
                            // grid, the fill marks which cells hold data.
                            // Transparent rather than absent, so an empty cell
                            // is still a tappable, visible part of the tiling.
                            .withFillColor(
                                if (cell.hasMeasurements) HEX_FILL_COLOR else android.graphics.Color.TRANSPARENT,
                            )
                            .withFillOutlineColor(Theme.Palette.PRIMARY)
                    },
                )
            }
        }

        if (hexMode) {
            // The count on the cell, as frozenApp showed it: a filled green
            // badge with the number in white. Without it the fill says only
            // "something happened here", which is the least useful half of
            // what the overlay knows.
            counts.create(
                state.hexCells.filter { it.hasMeasurements }.map { cell ->
                    PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(cell.centerLongitude, cell.centerLatitude))
                        .withIconImage(COUNT_IMAGE_ID)
                        .withTextField(cell.measurementCount.toString())
                        .withTextColor(android.graphics.Color.WHITE)
                        .withTextSize(12.0)
                },
            )
        }

        // Points stay visible in both modes: the hexagon says how many, the
        // pins say where, and hiding them made the grid look like the only data.
        points.create(
            state.points.map { point ->
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(point.longitude, point.latitude))
                    .withIconImage(MARKER_IMAGE_ID)
                    .withIconSize(if (hexMode) 0.7 else 1.0)
            },
        )
    }



    /** The badge behind a cell's count. Green, matching frozenApp's circle. */
    private fun countBadgeBitmap(): Bitmap {
        val size = with(Theme) { context.dp(26) }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f
        canvas.drawCircle(
            radius,
            radius,
            radius - 1f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HEX_COUNT_COLOR },
        )
        return bitmap
    }

    /** Drawn rather than shipped as an asset, so it follows the palette. */
    private fun markerBitmap(): Bitmap {
        val size = with(Theme) { context.dp(18) }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f
        canvas.drawCircle(
            radius, radius, radius - 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.Palette.PRIMARY },
        )
        canvas.drawCircle(
            radius, radius, radius - 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Theme.Palette.SURFACE
                style = Paint.Style.STROKE
                strokeWidth = 2f
            },
        )
        return bitmap
    }

    private companion object {
        const val MARKER_IMAGE_ID = "cellwatch-measurement-pin"
        const val COUNT_IMAGE_ID = "cellwatch-hex-count"

        /**
         * Translucent green, as frozenApp used: `cw_green_light` with its
         * alpha halved so the basemap stays readable underneath.
         */
        val HEX_FILL_COLOR = android.graphics.Color.argb(0x80, 0x4C, 0xAF, 0x50)

        /** The badge is opaque; only the cell fill is translucent. */
        val HEX_COUNT_COLOR = android.graphics.Color.rgb(0x2E, 0x7D, 0x32)
    }
}
