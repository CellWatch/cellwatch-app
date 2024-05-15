package edu.gatech.cc.cellwatch.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonPrimitive
import com.mapbox.common.Cancelable
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraChangedCallback
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.QueriedFeature
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.OnPolygonAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.removeOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.databinding.ActivityMapBinding
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.internal.toHexString
import kotlin.math.max

class MapActivity : AppCompatActivity() {
    private val TAG = this::class.simpleName
    private lateinit var binding: ActivityMapBinding
    private lateinit var model: MapViewModel
    private var hexFillColor = 0
    private val renderedHexAddresses = mutableSetOf<Long>()

    private val measurementPointLayerId = "measurement-points"
    private val parentHexGridLayerId = "hex-grid"
    private val childHexGridLayerId = "hex-grid-low-res"
    // based on https://github.com/mapbox/mapbox-maps-android/blob/060187f41095d23bde404401dd543ffb715ab144/plugin-annotation/src/main/java/com/mapbox/maps/plugin/annotation/AnnotationManagerImpl.kt
    private val clusterLayerIdPrefix = "mapbox-android-cluster-"
    private val clusterTextLayerId = "mapbox-android-cluster-text-layer"

    private lateinit var mapboxMap : MapboxMap
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var parentHexAnnotationManager: PolygonAnnotationManager
    private lateinit var childHexAnnotationManager: PolygonAnnotationManager
    private lateinit var viewAnnotationManager: ViewAnnotationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val renderedMeasurementGroupIds = mutableSetOf<String>()
    private var debounceJob: Job? = null
    private var cameraChangeSubscription: Cancelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val onboardingComplete = runBlocking { CellWatchApp.settingsRepository.getOnboardingComplete() }
        if (!onboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        model = ViewModelProvider(this)[MapViewModel::class.java]
        val sheetBehavior = BottomSheetBehavior.from(binding.sheet)
        sheetBehavior.saveFlags = BottomSheetBehavior.SAVE_ALL

        sheetBehavior.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                binding.sheetHeader.isVisible = newState == BottomSheetBehavior.STATE_EXPANDED
                binding.sheetHandle.isVisible = newState != BottomSheetBehavior.STATE_EXPANDED
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    model.selectedMeasurementGroupIds = setOf()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) { /* do nothing */ }

        })

        if (model.selectedMeasurementGroupIds.isEmpty()) {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.sheetCloseBtn.setOnClickListener { sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN }
        binding.sheet.setOnClickListener { } // prevent click events from going to UI elements underneath
        binding.sheetContents.layoutManager = LinearLayoutManager(this)
        binding.sheetContents.adapter = MeasurementAdapter()

        hexFillColor = getColor(R.color.cw_green_light) - (0x80000000).toInt()
        mapboxMap = binding.mapView.mapboxMap
        mapboxMap.loadStyle(Style.LIGHT)
        pointAnnotationManager = binding.mapView.annotations.createPointAnnotationManager(
            AnnotationConfig(
                layerId = measurementPointLayerId,
                annotationSourceOptions = AnnotationSourceOptions(
                    clusterOptions = ClusterOptions(
                        textColor = getColor(R.color.cw_white),
                        textSize = 16.0,
                        colorLevels = listOf(Pair(0, getColor(R.color.cw_blue))),
                        clusterMaxZoom = Long.MAX_VALUE,
                    )
                )
            )
        )
        pointAnnotationManager.addClickListener(onAnnotationClickListener)

        childHexAnnotationManager = binding.mapView.annotations.createPolygonAnnotationManager(
            AnnotationConfig(layerId = childHexGridLayerId)
        )
        childHexAnnotationManager.addClickListener(childHexClickListener)

        parentHexAnnotationManager = binding.mapView.annotations.createPolygonAnnotationManager(
            AnnotationConfig(layerId = parentHexGridLayerId, belowLayerId = childHexGridLayerId)
        )
        parentHexAnnotationManager.addClickListener(parentHexClickListener)

        viewAnnotationManager = binding.mapView.viewAnnotationManager
        onMapReady()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.measureButton.setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }

        binding.centerUserButton.setOnClickListener{
            centerCameraOnUser()
        }

        binding.navDrawer.setOnCloseListener { binding.root.closeDrawer(GravityCompat.START) }
        binding.navDrawer.setActiveActivity(this)
        binding.sideMenuButton.setOnClickListener {
            if (binding.root.isDrawerOpen(GravityCompat.START)) {
                binding.root.closeDrawer(GravityCompat.START)
            } else {
                binding.root.openDrawer(GravityCompat.START)
            }
        }

        binding.h3ToggleSwitch.setOnCheckedChangeListener { _, isChecked -> toggleHexGrid(isChecked) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                toggleHexGrid(binding.h3ToggleSwitch.isChecked)
            }
        }

        lifecycleScope.launch {
            try {
                CellWatchApp.measurementRepository.tryUploadMeasurements()
                CellWatchApp.measurementRepository.tryUploadFccSubmissions()
            } catch(e: Exception) {
                Log.d(TAG, "failed to upload measurements and submissions", e)
            }
        }
    }

    private fun toggleHexGrid(enabled: Boolean) {
        hexGridEnabled = enabled
        measurementPointsEnabled = !enabled
    }

    private var hexGridEnabled = true
        set(enabled) {
            field = enabled

            mapboxMap.style?.apply {
                getLayer(parentHexGridLayerId)?.visibility(if (enabled) Visibility.VISIBLE else Visibility.NONE)
                getLayer(childHexGridLayerId)?.visibility(if (enabled) Visibility.VISIBLE else Visibility.NONE)
            }

            viewAnnotationManager.annotations.forEach { (view) -> updateCountViewVisible(view) }

            if(enabled) {
                refreshHexGrid()
            }
        }

    private var measurementPointsEnabled = false
        set(enabled) {
            field = enabled
            mapboxMap.style?.apply {
                getLayer(measurementPointLayerId)?.visibility(if (enabled) Visibility.VISIBLE else Visibility.NONE)
                styleLayers.filter { it.id.startsWith(clusterLayerIdPrefix) }.forEach {
                    getLayer(it.id)?.visibility(if (enabled) Visibility.VISIBLE else Visibility.NONE)
                }
            }

            if(enabled) {
                pointAnnotationManager.addClickListener(onAnnotationClickListener)
                mapboxMap.addOnMapClickListener(handlePointClusterClick)
                refreshMeasurementGroups()
            } else {
                pointAnnotationManager.removeClickListener(onAnnotationClickListener)
                mapboxMap.removeOnMapClickListener(handlePointClusterClick)
            }
        }

    private var selectedParentHex: Pair<Long, PolygonAnnotation>? = null
        set(value) {
            if (field == value) return
            val old = field
            field = value

            if (old != null) {
                removeChildHexes(old.first)
                old.second.fillColorInt = hexFillColor
                parentHexAnnotationManager.update(old.second)
                viewAnnotationManager.annotations.keys.find { it.tag == old.first }?.let { updateCountViewVisible(it) }
            }

            if (value != null) {
                value.second.fillColorInt = 0
                parentHexAnnotationManager.update(value.second)
                viewAnnotationManager.annotations.keys.find { it.tag == value.first }?.let { updateCountViewVisible(it) }
                lifecycleScope.launch { renderChildHexes(value.first) }
            }
        }

    private val parentHexClickListener = OnPolygonAnnotationClickListener { annotation ->
        val address = annotation.getData()?.asLong ?: throw RuntimeException("no data for parent annotation $annotation")
        val groups = model.getHexMeasurementGroupIds(address)
        selectedParentHex = if (groups.isEmpty()) null else Pair(address, annotation)
        true
    }

    private val childHexClickListener = OnPolygonAnnotationClickListener { annotation ->
        val address = annotation.getData()?.asLong ?: throw RuntimeException("no data for child annotation $annotation")
        val groups = model.getHexMeasurementGroupIds(address)
        if (groups.isNotEmpty()) {
            showBottomSheet(groups, getString(R.string.hex_index, address.toHexString().lowercase()))
        }
        true
    }

    private val onAnnotationClickListener = OnPointAnnotationClickListener { annotation ->
        showBottomSheet(
            setOf(annotation.getData()?.asString ?: throw RuntimeException("no data for $annotation")),
            getString(R.string.one_measurement)
        )
        true
    }

    private val handlePointClusterClick = OnMapClickListener {  point ->
        fun withClusterFeatures(cluster: QueriedFeature, fn: (features: List<Feature>) -> Unit) {
            mapboxMap.getGeoJsonClusterLeaves( cluster.source, cluster.feature, Long.MAX_VALUE, 0 ) { res ->
                res.onError { Log.e(TAG, "error querying for leaves: $it") }
                res.onValue { it.featureCollection?.let { features -> fn(features) } }
            }
        }

        mapboxMap.queryRenderedFeatures(
            RenderedQueryGeometry(mapboxMap.pixelForCoordinate(point)),
            RenderedQueryOptions(listOf(clusterTextLayerId), null)
        ) { res ->
            res.onError { Log.e(TAG, "error querying for cluster: $it") }
            res.onValue { queriedFeatures ->
                queriedFeatures.firstOrNull()?.let { cluster ->
                    withClusterFeatures(cluster.queriedFeature) { features ->
                        showBottomSheet(
                            features.map { it.getProperty("custom_data").asString },
                            getString(R.string.x_measurements, features.size)
                        )
                    }
                }
            }
        }

        true
    }

    private val cameraChangedCallback = CameraChangedCallback {
        debounceJob?.cancel()
        if (binding.h3ToggleSwitch.isChecked) {
            debounceJob = CoroutineScope(Dispatchers.Main).launch {
                delay(100)

                val currentZoom = mapboxMap.cameraState.zoom

                //TODO Adjust as needed
                if (currentZoom < 12.0) {
                    if (hexGridEnabled) {
                        hexGridEnabled = false
                    }
                } else {
                    if (!hexGridEnabled) {
                        hexGridEnabled = true // setter calls refreshHexGrid()
                    } else {
                        refreshHexGrid()
                    }
                }
            }
        }
    }

    private fun refreshHexGrid() {
        lifecycleScope.launch {
            model.refreshMeasurementGroups()
            val visibleAddresses = getH3AddressesInView()
            val newAddresses = mutableSetOf<Long>()
            val oldAddresses = mutableSetOf<Long>()
            visibleAddresses.forEach {
                if (renderedHexAddresses.add(it)) {
                    newAddresses.add(it)
                } else {
                    oldAddresses.add(it)
                }
            }

            renderParentHexes(newAddresses)
            updateParentHexes(oldAddresses)
            selectedParentHex?.let {
                if (it.first in oldAddresses) {
                    updateChildHexes(it.first)

                    val groups = model.getHexMeasurementGroupIds(it.first)
                    if (groups.isNotEmpty()) {
                        (binding.sheetContents.adapter as MeasurementAdapter).setGroups(model.getMeasurementGroups(groups))
                    }
                }
            }
        }
    }

    private fun getH3AddressesInView(): Collection<Long> {
        val options = CameraOptions.Builder()
            // pretend we're a bit more zoomed out than we are to pre-load nearby hexes
            .zoom(max(mapboxMap.cameraState.zoom - 0.5, 0.0))
            .center(mapboxMap.cameraState.center)
            .build()

        val bounds = mapboxMap.coordinateBoundsForCamera(options)
        return H3Manager.getH3OverlayAddressesFromBounds(bounds, H3Manager.PARENT_HEX_RES)
    }

    private fun renderParentHexes(addresses: Collection<Long>) {
        addresses.forEach { renderHex(it, parentHexAnnotationManager) }
    }

    private fun updateParentHexes(addresses: Collection<Long>) {
        addresses.forEach { updateHex(it, parentHexAnnotationManager) }
    }

    private fun renderChildHexes(parentAddress: Long) {
        H3Manager.getRelatedH3Hex(parentAddress, H3Manager.CHILD_HEX_RES).forEach {
            renderHex(it, childHexAnnotationManager)
        }
    }

    private fun updateChildHexes(parentAddress: Long) {
        H3Manager.getRelatedH3Hex(parentAddress, H3Manager.CHILD_HEX_RES).forEach {
            updateHex(it, childHexAnnotationManager)
        }
    }

    private fun renderHex(address: Long, annotationManager: PolygonAnnotationManager) {
        val boundary = H3Manager.getH3BoundaryFromAddressSingleton(address)
        val groups = model.getHexMeasurementGroupIds(address)

        val options = PolygonAnnotationOptions()
            .withPoints(boundary)
            .withData(JsonPrimitive(address))
            .withFillColor(if (groups.isEmpty()) 0 else hexFillColor)
            .withFillOutlineColor(getColor(R.color.cw_blue))

        annotationManager.create(options)

        if (groups.isNotEmpty()) {
            createHexCountView(address, groups.size)
        }
    }

    private fun updateHex(address: Long, annotationManager: PolygonAnnotationManager) {
        val annotation = annotationManager.annotations.find { it.getData()?.asLong == address }
        if (annotation == null) {
            Log.e(TAG, "no annotation found to update for $address")
            return
        }

        val groups = model.getHexMeasurementGroupIds(address)
        val fillColor = if (groups.isEmpty() || address == selectedParentHex?.first) 0 else hexFillColor
        if (fillColor != annotation.fillColorInt) {
            annotation.fillColorInt = fillColor
            annotationManager.update(annotation)
        }

        if (groups.isNotEmpty()) {
            val view = viewAnnotationManager.annotations.keys.find { it.tag == address }
            if (view == null) {
                createHexCountView(address, groups.size)
            } else {
                view.findViewById<TextView>(R.id.textView_measurements).text = groups.size.toString()
            }
        }
    }

    private fun createHexCountView(address: Long, count: Int) {
        val hexCenter = H3Manager.getH3CenterFromAddressSingleton(address)

        val view = layoutInflater.inflate(R.layout.view_map_annotaton_layout, binding.mapView, false)
        val textViewMeasurements = view.findViewById<TextView>(R.id.textView_measurements)
        textViewMeasurements.text = count.toString()
        view.tag = address
        updateCountViewVisible(view)

        val viewOptions = viewAnnotationOptions {
            geometry(hexCenter)
            allowOverlap(true)
            allowOverlapWithPuck(true)
        }
        viewAnnotationManager.addViewAnnotation(view, viewOptions)
    }

    private fun updateCountViewVisible(view: View) {
        view.isVisible = hexGridEnabled && view.tag != selectedParentHex?.first
    }

    private fun removeChildHexes(parentAddress: Long) {
        val childAddresses = H3Manager.getRelatedH3Hex(parentAddress, H3Manager.CHILD_HEX_RES)

        childHexAnnotationManager.delete(childHexAnnotationManager.annotations.filter {
            (it.getData()?.asLong ?: throw RuntimeException("no data for $it")) in childAddresses
        })

        viewAnnotationManager.annotations.keys
            .filter { it.tag in childAddresses }
            .forEach { viewAnnotationManager.removeViewAnnotation(it) }
    }

    private fun refreshMeasurementGroups() {
        lifecycleScope.launch {
            val groupIds = model.refreshMeasurementGroups()
            val newGroupIds = mutableSetOf<String>()
            groupIds.forEach { if (renderedMeasurementGroupIds.add(it)) newGroupIds.add(it) }
            renderMeasurementGroups(newGroupIds)
        }
    }

    private fun renderMeasurementGroups(ids: Set<String>) {
        val icon = AppCompatResources.getDrawable(this, R.drawable.fa_solid_location_pin)?.toBitmap()
            ?: throw RuntimeException("no icon!")

        for (group in model.getMeasurementGroups(ids)) {
            val location = group.location()
            if (location == null) {
                Log.e(TAG, "group with no location: $group")
                continue
            }

            val options = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(location.lon, location.lat))
                .withIconImage(icon)
                .withData(JsonPrimitive(group.id()))

            pointAnnotationManager.create(options)
        }
    }

    private fun onMapReady() {
        mapboxMap.setCamera(
            CameraOptions.Builder()
                .zoom(13.0)
                .build()
        )

        mapboxMap.loadStyle(Style.LIGHT) {
            initLocationComponent()
            cameraChangeSubscription?.cancel()
            cameraChangeSubscription = mapboxMap.subscribeCameraChanged(cameraChangedCallback)

            binding.mapView.compass.enabled = false
            binding.mapView.scalebar.enabled = false

            centerCameraOnUser()
        }
    }

    private fun centerCameraOnUser() {
        //Permissions check required by fusedLocationClient
        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastKnownLocation->
                    if (lastKnownLocation != null) {
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .zoom(14.0)
                                .center(Point.fromLngLat(lastKnownLocation.longitude, lastKnownLocation.latitude))
                                .build()
                        )
                    }
                }
        }

    }

    private fun initLocationComponent() {
        val locationComponentPlugin = binding.mapView.location
        locationComponentPlugin.updateSettings {
            enabled = true
            puckBearingEnabled = true
            locationPuck = LocationPuck2D(
                topImage = ImageHolder.from(R.drawable.empty), // MapBox complains in the logs if we don't provide something
                bearingImage = ImageHolder.from(R.drawable.mapbox_user_puck_icon),
                shadowImage = ImageHolder.from(R.drawable.mapbox_user_icon_shadow),
                scaleExpression = interpolate {
                    linear()
                    zoom()
                    stop(0.0, 0.6)
                    stop(20.0, 1.0)
                }.toJson()
            )
        }
    }

    private fun showBottomSheet(groupIds: Collection<String>, title: String) {
        if (model.selectedMeasurementGroupIds == groupIds) {
            return // it's already showing the correct groups
        }

        model.selectedMeasurementGroupIds = groupIds.toSet()
        (binding.sheetContents.adapter as MeasurementAdapter).setGroups(model.getMeasurementGroups(groupIds))
        val sheetBehavior = BottomSheetBehavior.from(binding.sheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        binding.sheetTitle.text = title
    }
}