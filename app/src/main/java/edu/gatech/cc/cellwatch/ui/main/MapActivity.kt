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
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.databinding.ActivityMapBinding
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.internal.toHexString

class MapActivity : AppCompatActivity() {
    private val TAG = this::class.simpleName
    private lateinit var binding: ActivityMapBinding
    private lateinit var model: MapViewModel
    private val renderedH3Addresses = HashSet<Long>()
    private val renderedMeasurementOverlays = HashSet<Long>()
    private val BIGGER_HEX_TILE_RES = 8
    private val SMALLER_HEX_TILE_RES = 9

    private val measurementPointLayerId = "measurement-points"
    private val hexGridLayerId = "hex-grid"
    private val lowResHexGridLayerId = "hex-grid-low-res"
    // based on https://github.com/mapbox/mapbox-maps-android/blob/060187f41095d23bde404401dd543ffb715ab144/plugin-annotation/src/main/java/com/mapbox/maps/plugin/annotation/AnnotationManagerImpl.kt
    private val clusterLayerIdPrefix = "mapbox-android-cluster-"
    private val clusterTextLayerId = "mapbox-android-cluster-text-layer"

    private lateinit var mapboxMap : MapboxMap
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var polygonAnnotationManager: PolygonAnnotationManager
    private lateinit var lowResPolygonAnnotationManager: PolygonAnnotationManager
    private lateinit var viewAnnotationManager: ViewAnnotationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val renderedMeasurementGroups = mutableSetOf<MeasurementGroup>()
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
                    model.selectedGroups = null
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) { /* do nothing */ }

        })

        if (model.selectedGroups == null) {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.sheetCloseBtn.setOnClickListener { sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN }
        binding.sheetContents.layoutManager = LinearLayoutManager(this)
        binding.sheetContents.adapter = MeasurementAdapter()

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
        polygonAnnotationManager = binding.mapView.annotations.createPolygonAnnotationManager(
            AnnotationConfig(layerId = hexGridLayerId)
        )
        lowResPolygonAnnotationManager = binding.mapView.annotations.createPolygonAnnotationManager(
            AnnotationConfig(layerId = lowResHexGridLayerId)
        )
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
        toggleHexGrid(binding.h3ToggleSwitch.isChecked) // initial switch function on start

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!binding.h3ToggleSwitch.isChecked) refreshMeasurementGroups()
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
                getLayer(hexGridLayerId)?.visibility(if (enabled) Visibility.VISIBLE else Visibility.NONE)
                getLayer(lowResHexGridLayerId)?.visibility(if (enabled) Visibility.VISIBLE else Visibility.NONE)
            }

            viewAnnotationManager.annotations.forEach { (view) ->  view.isVisible = enabled }

            if(enabled) {
                loadMapH3()
                mapboxMap.addOnMapClickListener(onMapClickListenerH3)
            } else {
                mapboxMap.removeOnMapClickListener(onMapClickListenerH3)
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
                lifecycleScope.launch { refreshMeasurementGroups() }
            } else {
                pointAnnotationManager.removeClickListener(onAnnotationClickListener)
                mapboxMap.removeOnMapClickListener(handlePointClusterClick)
            }
        }

    private val onPolygonClick: OnPolygonAnnotationClickListener = OnPolygonAnnotationClickListener { polygon ->
        /*
        Used when a child hexagon is clicked to display measurements associated with it.
         */
        val data = polygon.getData()
        if (data == null || data.isJsonNull || !data.isJsonObject) {
            Log.i("H3", "Skipping annotation due to null or invalid data")
            return@OnPolygonAnnotationClickListener false // Skip if data is null or not a JsonObject
        }

        val h3AddressElement = data.asJsonObject.get("h3_address")
        val h3Address = h3AddressElement?.takeIf { it.isJsonPrimitive }?.asLong

        if(h3Address?.let { H3Manager.getH3ResolutionFromAddress(it) } == SMALLER_HEX_TILE_RES) {
            val associatedGroups = h3Address.let {
                runBlocking { H3Manager.getMeasurementGroupsAssociatedWithH3Address(it, SMALLER_HEX_TILE_RES) }
            }

            if (associatedGroups.size >= 1) {
                showBottomSheet(associatedGroups, getString(R.string.hex_index, h3Address.toHexString().lowercase()))
            }
        }

        true
    }

    private val onMapClickListenerH3 = OnMapClickListener { it ->
        val associatedMeasurements = runBlocking {
            H3Manager.getMeasurementGroupsAssociatedWithLatLong(it)
        }
        val h3Address = H3Manager.getH3AddressFromPointSingleton(it, BIGGER_HEX_TILE_RES)
        if(H3Manager.getH3ResolutionFromAddress(h3Address) == BIGGER_HEX_TILE_RES && associatedMeasurements.size > 0) {
            polygonAnnotationManager.annotations.forEach { annotation ->
                val data = annotation.getData()
                if (data == null || !data.isJsonObject) {
                    return@forEach
                }

                val h3AddressElement = data.asJsonObject.get("h3_address")
                if (h3AddressElement?.takeIf { it.isJsonPrimitive }?.asLong == h3Address) {
                    polygonAnnotationManager.delete(annotation)
                }
            }
            viewAnnotationManager.removeAllViewAnnotations()
            displayRes8Hexagons(it)
        }
        true
    }

    private val onAnnotationClickListener = OnPointAnnotationClickListener { annotation ->
        showBottomSheet(
            listOf(decodeMeasurementGroup(annotation.getData() ?: throw RuntimeException("no annotation data!"))),
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
                            features.map { decodeMeasurementGroup(it.getProperty("custom_data")) },
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
                        hexGridEnabled = true // setter calls loadMapH3()
                    } else {
                        loadMapH3()
                    }
                }
            }
        }
    }

    private fun refreshMeasurementGroups() {
        lifecycleScope.launch {
            val groups = CellWatchApp.measurementRepository.getMeasurementGroups()
            val newGroups = mutableSetOf<MeasurementGroup>()
            groups.forEach { if (renderedMeasurementGroups.add(it)) newGroups.add(it) }
            renderMeasurementGroups(newGroups)
        }
    }

    private fun renderMeasurementGroups(groups: Set<MeasurementGroup>) {
        val icon = AppCompatResources.getDrawable(this, R.drawable.fa_solid_location_pin)?.toBitmap()
            ?: throw RuntimeException("no icon!")

        for (group in groups) {
            val location = group.latency?.locations?.firstOrNull()
                ?: group.download?.locations?.firstOrNull()
                ?: group.upload?.locations?.firstOrNull()

            if (location == null) {
                Log.e(TAG, "group with no location: $group")
                continue
            }

            val options = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(location.lon, location.lat))
                .withIconImage(icon)
                .withData(encodeMeasurementGroup(group))

            pointAnnotationManager.create(options)
        }
    }

    private fun encodeMeasurementGroup(group: MeasurementGroup): JsonElement {
        // MapBox requires a GSON JsonElement, but I can't get GSON to encode Kotlin Instants
        // properly. Encode using Kotlin's JSON instead and then return as a GSON JsonString.
        return JsonPrimitive(Json.encodeToString(group))
    }

    private fun decodeMeasurementGroup(data: JsonElement): MeasurementGroup {
        return Json.decodeFromString(data.asString)
    }

    private fun loadMapH3() = CoroutineScope(Dispatchers.Default).launch {
        val center = withContext(Dispatchers.Main) {
            mapboxMap.cameraState.center
        }

        val scale = 1.5
        val delta = 0.0180625 * scale // Rough estimation of 2.5 miles in lat/long

        // Create a bounding box using the rough estimation
        val ne = Point.fromLngLat(center.latitude() + delta, center.longitude() + delta)
        val sw = Point.fromLngLat(center.latitude() - delta, center.longitude() - delta)
        val nw = Point.fromLngLat(center.latitude() - delta, center.longitude() + delta)
        val se = Point.fromLngLat(center.latitude() + delta, center.longitude() - delta)

        //Convert camera boundaries to h3 boundaries
        val h3Addresses = H3Manager.getH3OverlayAddressesFromCoordinates(mutableListOf(ne, nw, sw, se), BIGGER_HEX_TILE_RES)

        // Get all of the non rendered H3 addresses; used for when the map is moved so we don't re-render hexagons.
        val nonRenderedH3Addresses = h3Addresses.filterNot { it in renderedH3Addresses }.toMutableList()

        // Get all of the hex boundaries that we need to render.
        val h3Boundaries = H3Manager.getH3BoundariesFromAddressList(nonRenderedH3Addresses)

        withContext(Dispatchers.Main) {
            val reusablePolygonOptions = PolygonAnnotationOptions()
                .withFillColor("rgba(0, 0, 0, 0)") // Transparent fill color
                .withFillOutlineColor("#0000FF") // Blue outline color


            // Display h3 boundaries
            h3Boundaries.forEach { boundary ->
                val address = H3Manager.getH3AddressFromPointSingleton(boundary.first(), BIGGER_HEX_TILE_RES)

                val data = JsonObject()
                data.addProperty("h3_address", address)

                renderedH3Addresses.add(address)
                reusablePolygonOptions.withPoints(listOf(boundary))
                polygonAnnotationManager.create(reusablePolygonOptions.withData(data))
            }

            // Display overlays on hexagons with > 1 point within them
            h3Addresses.forEach { address ->
                val data = JsonObject()
                data.addProperty("h3_address", address)

                val groups = H3Manager.getMeasurementGroupsAssociatedWithH3Address(address, BIGGER_HEX_TILE_RES)
                if (groups.size >= 1 && !renderedMeasurementOverlays.contains(address)) {
                    val addressBoundary = H3Manager.getH3BoundaryFromAddressSingleton(address)

                    polygonAnnotationManager.create(reusablePolygonOptions
                        .withPoints(addressBoundary)
                        .withFillColor("#22B14C")
                        .withFillOpacity(.5)
                        .withData(data))

                    val hexCenter = H3Manager.getH3CenterFromAddressSingleton(address)

                    val view = layoutInflater.inflate(R.layout.view_map_annotaton_layout, binding.mapView, false)
                    val textViewMeasurements = view.findViewById<TextView>(R.id.textView_measurements)
                    textViewMeasurements.text = groups.size.toString()

                    val options = viewAnnotationOptions {
                        geometry(hexCenter)
                        allowOverlap(true)
                        allowOverlapWithPuck(true)
                    }
                    viewAnnotationManager.addViewAnnotation(view, options)
                    renderedMeasurementOverlays.add(address)
                }
            }
        }
    }

    private fun displayRes8Hexagons(point: Point) {
        val h3Address = H3Manager.getH3AddressFromPointSingleton(point, BIGGER_HEX_TILE_RES)
        val h3HexChildren = H3Manager.getRelatedH3Hex(h3Address, SMALLER_HEX_TILE_RES)
        val h3Boundaries = H3Manager.getH3BoundariesFromAddressList(h3HexChildren)

        val reusablePolygonOptions = PolygonAnnotationOptions()
            .withFillColor("rgba(0, 0, 0, 0)") // Transparent fill color
            .withFillOutlineColor("#0000FF") // Blue outline color

        // Display h3 boundaries
        h3Boundaries.forEach { boundary ->
            reusablePolygonOptions.withPoints(listOf(boundary))
            lowResPolygonAnnotationManager.create(reusablePolygonOptions)
        }

        // Display overlays on hexagons with > 1 point within them
        h3HexChildren.forEach { address ->
            val data = JsonObject()
            data.addProperty("h3_address", address)

            val groups = runBlocking {
                H3Manager.getMeasurementGroupsAssociatedWithH3Address(address, SMALLER_HEX_TILE_RES)
            }
            val addressBoundary = H3Manager.getH3BoundaryFromAddressSingleton(address)
            reusablePolygonOptions
                .withPoints(addressBoundary)
                .withFillColor("#22B14C") // Green fill color
                .withData(data)

            if (groups.size >= 1) {
                reusablePolygonOptions.withFillOpacity(.5)
                lowResPolygonAnnotationManager.create(reusablePolygonOptions)

                val hexCenter = H3Manager.getH3CenterFromAddressSingleton(address)

                // Inflate the custom view
                val view = layoutInflater.inflate(R.layout.view_map_annotaton_layout, binding.mapView, false)
                val textViewMeasurements = view.findViewById<TextView>(R.id.textView_measurements)
                textViewMeasurements.text = groups.size.toString()

                // Add the view as an annotation at the hexagon's center
                val options = viewAnnotationOptions {
                    geometry(hexCenter)
                    allowOverlap(true)
                    allowOverlapWithPuck(true)
                }
                viewAnnotationManager.addViewAnnotation(view, options)
            }
        }

        lowResPolygonAnnotationManager.addClickListener(onPolygonClick)
        mapboxMap.removeOnMapClickListener(onMapClickListenerH3)
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
            mapboxMap.addOnMapClickListener(onMapClickListenerH3)

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
            this.enabled = true
            this.puckBearingEnabled = true
            this.locationPuck = LocationPuck2D(
                bearingImage = ImageHolder.Companion.from(R.drawable.mapbox_user_puck_icon),
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

    private fun showBottomSheet(groups: List<MeasurementGroup>, title: String) {
        if (model.selectedGroups == groups) {
            return // it's already showing the correct groups
        }

        model.selectedGroups = groups
        val sheetBehavior = BottomSheetBehavior.from(binding.sheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        binding.sheetTitle.text = title
        (binding.sheetContents.adapter as MeasurementAdapter).setGroups(groups)
    }
}