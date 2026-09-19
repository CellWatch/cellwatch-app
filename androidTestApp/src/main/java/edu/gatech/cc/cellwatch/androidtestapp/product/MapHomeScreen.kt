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
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.MapScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeInput
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeMeasurementLocationSnapshot
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
    onMeasure: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {

    private val scaffold = MapScreenScaffold(context)
    private val statusHolder = FrameLayout(context)
    private val measureButton = Components.primaryButton(context, "Measure")
    private var mapView: MapView? = null
    private var pointAnnotations: PointAnnotationManager? = null

    val view: View get() = scaffold

    init {
        measureButton.setOnClickListener { onMeasure() }
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

        scaffold.addToPanel(statusHolder, measureButton, secondaryRow)
        installMap()
        refresh()
    }

    /** Re-read on every appearance, so returning shows new counts and points. */
    fun refresh() {
        render(viewModel.onInputChanged(inputProvider()))
        measurementLocationProvider { snapshots ->
            render(viewModel.onMeasurementsLoaded(snapshots))
        }
    }

    fun onDestroy() {
        mapView?.onDestroy()
        mapView = null
    }

    private fun render(state: MapHomeUiState) {
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
        }
    }

    private fun renderFeatures(state: MapHomeUiState) {
        val map = mapView ?: return
        val manager = pointAnnotations ?: map.annotations.createPointAnnotationManager().also {
            pointAnnotations = it
        }
        manager.deleteAll()
        manager.create(
            state.points.map { point ->
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(point.longitude, point.latitude))
                    .withIconImage(MARKER_IMAGE_ID)
                    .withIconSize(1.0)
            },
        )
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
    }
}
