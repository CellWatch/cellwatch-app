package edu.gatech.cc.cellwatch.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationOptions
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.OnPolygonAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.removeOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.ActivityMapBinding
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import edu.gatech.cc.cellwatch.domain.map.managers.MapAnnotationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MapActivity : AppCompatActivity() {
    private val TAG = this::class.simpleName
    private lateinit var binding: ActivityMapBinding
    private val renderedH3Addresses = HashSet<Long>()
    private val renderedMeasurementOverlays = HashSet<Long>()
    private val BIGGER_HEX_TILE_RES = 8
    private val SMALLER_HEX_TILE_RES = 9
    private lateinit var mapboxMap : MapboxMap
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var polygonAnnotationManager: PolygonAnnotationManager? = null
    private var lowResPolygonAnnotationManager: PolygonAnnotationManager? = null
    private var viewAnnotationManager: ViewAnnotationManager? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var annotations: MutableList<PointAnnotation> = mutableListOf()
    private var debounceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val onboardingComplete = runBlocking { CellWatchApp.settingsRepository.getOnboardingComplete() }
        if (!onboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapboxMap = binding.mapView.getMapboxMap()
        mapboxMap.loadStyleUri(Style.LIGHT)
        onMapReady()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.measureButton.setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }

        binding.centerUserButton.setOnClickListener{
            centerCameraOnUser()
        }

        binding.h3ToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                pointAnnotationManager?.deleteAll()
                loadMapH3()
                mapboxMap.addOnCameraChangeListener(onCameraChangeListener)
                mapboxMap.addOnMapClickListener(onMapClickListenerH3)
            } else {
                polygonAnnotationManager?.deleteAll()
                lowResPolygonAnnotationManager?.deleteAll()
                viewAnnotationManager?.removeAllViewAnnotations()
                renderedH3Addresses.clear()
                renderedMeasurementOverlays.clear()
                loadMapAnnotations()
                mapboxMap.removeOnCameraChangeListener(onCameraChangeListener)
                mapboxMap.removeOnMapClickListener(onMapClickListenerH3)
            }
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

        // Initial switch function on start
        if (binding.h3ToggleSwitch.isChecked) {
            loadMapH3()
        } else {
            loadMapAnnotations()
        }
    }

    private val onPolygonClick: OnPolygonAnnotationClickListener = OnPolygonAnnotationClickListener { polygon ->
        /*
        Used when a child hexagon is clicked to display measurements associated with it.
         */
        val data = polygon.getData()
        Log.i("onPolygonClick", data.toString())
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
                val bottomSheetFragment = MeasurementListBottomSheetFragment.newInstance(h3Address)
                bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
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
            polygonAnnotationManager?.annotations?.forEach { annotation ->
                val data = annotation.getData()
                if (data == null || !data.isJsonObject) {
                    return@forEach
                }

                val h3AddressElement = data.asJsonObject.get("h3_address")
                if (h3AddressElement?.takeIf { it.isJsonPrimitive }?.asLong == h3Address) {
                    polygonAnnotationManager?.delete(annotation)
                }
            }
            viewAnnotationManager?.removeAllViewAnnotations()
            displayRes8Hexagons(it)
        }
        true
    }

    private val onAnnotationClickListener = OnPointAnnotationClickListener { annotation ->
        val point = annotation.geometry
        val h3Address = H3Manager.getH3AddressFromPointSingleton(point, 8)

        val bottomSheetFragment = MeasurementListBottomSheetFragment.newInstance(h3Address)
        bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
        true
    }

    private val onCameraChangeListener = OnCameraChangeListener {
        debounceJob?.cancel()
        debounceJob = CoroutineScope(Dispatchers.Main).launch {
            delay(100)

            val currentZoom = mapboxMap.cameraState.zoom

            //TODO Adjust as needed
            if (currentZoom < 12.0) {
                hideMapH3Content()
            } else {
                mapboxMap.addOnMapClickListener(onMapClickListenerH3)
                loadMapH3()
            }
        }
    }

    private fun bitmapFromDrawableRes(@DrawableRes resourceId: Int, count: Int) =
        convertDrawableToBitmap(getDrawable(resourceId), count)

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?, count: Int): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            val drawableBitMap = sourceDrawable.bitmap

            val modifiedBitmap = drawableBitMap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(modifiedBitmap)

            if (count != 1) {
                val paint = Paint()
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.isAntiAlias = true

                val textSize: Float = canvas.width * 0.5f
                paint.textSize = textSize

                canvas.drawText(
                    count.toString(),
                    (canvas.width / 2).toFloat(),
                    (canvas.height / 2) + (textSize / 3),
                    paint
                )
            }

            modifiedBitmap

        } else {
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            //Draw text overlay on bitmap if there's more than one averaged point
            if (count != 1) {
                val paint = Paint()
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.isAntiAlias = true

                val textSize: Float = canvas.width * 0.5f
                paint.textSize = textSize

                canvas.drawText(
                    count.toString(),
                    (canvas.width / 2).toFloat(),
                    (canvas.height / 2) + (textSize / 3),
                    paint
                )
            }
            bitmap
        }
    }

    private fun loadMapAnnotations() {
        if(this.pointAnnotationManager == null) {
            val annotationApi = binding.mapView.annotations

            pointAnnotationManager = annotationApi.createPointAnnotationManager()
            Log.d(TAG, "loadMapAnnotations initialize pointAnnotationManager")
        }

        val coordinates = MapAnnotationManager.getAllCoordinates()
        Log.d(TAG, "loadMapAnnotations got ${coordinates.size} coordinates")
        for (coordinate in coordinates) {
            Log.d(TAG, "coordinate = $coordinate")
            bitmapFromDrawableRes(R.drawable.fa_solid_location_pin, coordinate.count)?.let { bitmap ->
                val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(coordinate.long, coordinate.lat))
                    .withIconImage(bitmap)
                val pointAnnotation = pointAnnotationManager?.create(pointAnnotationOptions)
                pointAnnotation.let {
                    if (it != null) {
                        annotations.add(it)
                    }
                }
            }
        }

        pointAnnotationManager?.addClickListener(onAnnotationClickListener)
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

        Log.i(TAG, "nonRenderedH3Addresses: $nonRenderedH3Addresses")
        Log.i(TAG, "renderedH3Addresses: $renderedH3Addresses")

        // Get all of the hex boundaries that we need to render.
        val h3Boundaries = H3Manager.getH3BoundariesFromAddressList(nonRenderedH3Addresses)

        withContext(Dispatchers.Main) {
            if (polygonAnnotationManager == null) {
                val annotationApi = binding.mapView.annotations
                polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            }
            if (lowResPolygonAnnotationManager == null) {
                val annotationApi = binding.mapView.annotations
                lowResPolygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            }
            if (viewAnnotationManager == null) {
                viewAnnotationManager = binding.mapView.viewAnnotationManager
            }

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
                Log.i("MapFragment", "polygonAnnotationManager: ${polygonAnnotationManager.toString()}")
                polygonAnnotationManager?.create(reusablePolygonOptions.withData(data))
            }

            // Display overlays on hexagons with > 1 point within them
            h3Addresses.forEach { address ->
                val data = JsonObject()
                data.addProperty("h3_address", address)

                val groups = H3Manager.getMeasurementGroupsAssociatedWithH3Address(address, BIGGER_HEX_TILE_RES)
                Log.i(TAG, "$groups")
                Log.i(TAG, "Determining if we have a match: ${groups.size > 1} and ${renderedMeasurementOverlays.contains(address)}")
                if (groups.size >= 1 && !renderedMeasurementOverlays.contains(address)) {
                    Log.i(TAG, "Address boundary precursor")
                    val addressBoundary = H3Manager.getH3BoundaryFromAddressSingleton(address)

                    Log.i(TAG, "address boundary: $addressBoundary")

                    polygonAnnotationManager?.create(reusablePolygonOptions
                        .withPoints(addressBoundary)
                        .withFillColor("#22B14C")
                        .withFillOpacity(.5)
                        .withData(data))

                    val hexCenter = H3Manager.getH3CenterFromAddressSingleton(address)

                    val view = layoutInflater.inflate(R.layout.view_map_annotaton_layout, binding.mapView, false)
                    val textViewMeasurements = view.findViewById<TextView>(R.id.textView_measurements)
                    textViewMeasurements.text = groups.size.toString()

                    val viewAnnotationOptions = ViewAnnotationOptions.Builder()
                        .geometry(hexCenter)
                        .build()
                    viewAnnotationManager?.addViewAnnotation(view, viewAnnotationOptions)
                    Log.i("MapFragment", "viewAnnotationManager: ${viewAnnotationManager.toString()}")

                    renderedMeasurementOverlays.add(address)
                }
            }
        }
    }

    private fun hideMapH3Content() {
        //TODO Need to figure out some way just to hide them and keep the polygons stored within the manager.
        polygonAnnotationManager?.deleteAll()
        lowResPolygonAnnotationManager?.deleteAll()
        renderedH3Addresses.clear()
        renderedMeasurementOverlays.clear()
        viewAnnotationManager?.removeAllViewAnnotations()
    }

    private fun displayRes8Hexagons(point: Point) {
        val h3Address = H3Manager.getH3AddressFromPointSingleton(point, BIGGER_HEX_TILE_RES)
        val h3HexChildren = H3Manager.getRelatedH3Hex(h3Address, SMALLER_HEX_TILE_RES)
        val h3Boundaries = H3Manager.getH3BoundariesFromAddressList(h3HexChildren)

        Log.i("H3 map click", "H3address: $h3Address, H3boundaries: $h3Boundaries")

        if(lowResPolygonAnnotationManager == null) {
            val annotationApi = binding.mapView.annotations
            lowResPolygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
        }
        if(viewAnnotationManager == null) {
            viewAnnotationManager = binding.mapView.viewAnnotationManager
        }

        val reusablePolygonOptions = PolygonAnnotationOptions()
            .withFillColor("rgba(0, 0, 0, 0)") // Transparent fill color
            .withFillOutlineColor("#0000FF") // Blue outline color

        // Display h3 boundaries
        h3Boundaries.forEach { boundary ->
            reusablePolygonOptions.withPoints(listOf(boundary))
            lowResPolygonAnnotationManager?.create(reusablePolygonOptions)
        }

        // Display overlays on hexagons with > 1 point within them
        h3HexChildren.forEach { address ->
            val data = JsonObject()
            data.addProperty("h3_address", address)
            Log.i("H3 Child Data", "$data")


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
                Log.i("MapFragment", "LowResPolygonAnnotationManager: ${lowResPolygonAnnotationManager.toString()}")
                lowResPolygonAnnotationManager?.create(reusablePolygonOptions)

                val hexCenter = H3Manager.getH3CenterFromAddressSingleton(address)

                // Inflate the custom view
                val view = layoutInflater.inflate(R.layout.view_map_annotaton_layout, binding.mapView, false)
                val textViewMeasurements = view.findViewById<TextView>(R.id.textView_measurements)
                textViewMeasurements.text = groups.size.toString()

                // Add the view as an annotation at the hexagon's center
                val viewAnnotationOptions = ViewAnnotationOptions.Builder()
                    .geometry(hexCenter)
                    .build()
                viewAnnotationManager?.addViewAnnotation(view, viewAnnotationOptions)
            }
        }

        lowResPolygonAnnotationManager?.addClickListener(onPolygonClick)
        mapboxMap.removeOnMapClickListener(onMapClickListenerH3)
    }

    private fun onMapReady() {
        binding.mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(13.0)
                .build()
        )

        binding.mapView.getMapboxMap().loadStyleUri(
            Style.LIGHT
        ) {
            initLocationComponent()
            mapboxMap.addOnCameraChangeListener(onCameraChangeListener)
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
                        Log.i(TAG, "centerCameraOnUser setCamera")
                        binding.mapView.getMapboxMap().setCamera(
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
            this.locationPuck = LocationPuck2D(
                bearingImage = getDrawable(R.drawable.mapbox_user_puck_icon),
                shadowImage = getDrawable(R.drawable.mapbox_user_icon_shadow),
                scaleExpression = interpolate {
                    linear()
                    zoom()
                    stop {
                        literal(0.0)
                        literal(0.6)
                    }
                    stop {
                        literal(20.0)
                        literal(1.0)
                    }
                }.toJson()
            )
        }
    }
}