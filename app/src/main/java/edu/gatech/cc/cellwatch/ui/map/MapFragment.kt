package edu.gatech.cc.cellwatch.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.FragmentMapBinding
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import edu.gatech.cc.cellwatch.domain.map.managers.MapAnnotationManager
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
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
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.removeOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.mapbox.maps.plugin.gestures.gestures
import kotlinx.coroutines.*

/**
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
class MapFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var _binding: FragmentMapBinding? = null

    private var drawerToggleListener: DrawerToggleListener? = null

    interface DrawerToggleListener {
        fun toggleDrawer()
    }
    interface OnMapFragmentInteractionListener {
        fun onMeasureButtonPressed()
    }

    private var measurementButtonListener: OnMapFragmentInteractionListener? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        // Inflate the layout for this fragment
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated!")

        if (context is DrawerToggleListener) {
            drawerToggleListener = context as DrawerToggleListener
        } else {
            throw RuntimeException(context.toString() + " must implement DrawerToggleListener")
        }

        if (context is OnMapFragmentInteractionListener) {
            measurementButtonListener = context as OnMapFragmentInteractionListener
        } else {
            throw RuntimeException(context.toString() + " must implement OnMapFragmentInteractionListener")
        }

        mapView = binding.mapView
        mapboxMap = mapView.getMapboxMap()
        mapboxMap.loadStyleUri(Style.LIGHT)
        onMapReady()

        val hamburgerButton = binding.sideMenuButton
        val h3ToggleSwitch = binding.h3ToggleSwitch
        val measureButton = binding.measureButton
        val centerButton = binding.centerUserButton

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context!!)

        measureButton.setOnClickListener {
            measurementButtonListener?.onMeasureButtonPressed()
        }

        centerButton.setOnClickListener{
            centerCameraOnUser()
        }

        h3ToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                pointAnnotationManager?.deleteAll()
                loadMapH3()
                mapboxMap.addOnCameraChangeListener(onCameraChangeListener)
                mapboxMap.addOnMapClickListener(onMapClickListenerH3)
            } else {
                polygonAnnotationManager?.deleteAll()
                lowResPolygonAnnotationManager?.deleteAll()
                viewAnnotationManager?.removeAllViewAnnotations()
                loadMapAnnotations()
                mapboxMap.removeOnCameraChangeListener(onCameraChangeListener)
                mapboxMap.removeOnMapClickListener(onMapClickListenerH3)
            }
        }

        hamburgerButton.setOnClickListener {
            drawerToggleListener?.toggleDrawer()
            Log.i("MapFragment", "toggleDrawer")
        }

        // Initial switch function on start
        if (h3ToggleSwitch.isChecked) {
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
        if (data == null || data.isJsonNull || !data.isJsonObject) {
            Log.i("H3", "Skipping annotation due to null or invalid data")
            return@OnPolygonAnnotationClickListener false // Skip if data is null or not a JsonObject
        }

        val h3AddressElement = data.asJsonObject.get("h3_address")
        val h3Address = h3AddressElement?.takeIf { it.isJsonPrimitive }?.asLong

        if(h3Address?.let { H3Manager.getH3ResolutionFromAddress(it) } == 6) {
            val associatedGroups = h3Address.let {
                runBlocking { H3Manager.getMeasurementGroupsAssociatedWithH3Address(it, 6) }
            }

            if (associatedGroups.size > 1) {
                val bottomSheetFragment = MeasurementListBottomSheetFragment.newInstance(h3Address)
                if (isAdded) {
                    bottomSheetFragment.show(
                        parentFragmentManager,
                        bottomSheetFragment.tag
                    )
                }
            }
        }

        true
    }

    private var debounceJob: Job? = null

    private val onMapClickListenerH3 = OnMapClickListener { it ->
        val associatedMeasurements = runBlocking {
            H3Manager.getMeasurementGroupsAssociatedWithLatLong(it)
        }
        val h3Address = H3Manager.getH3AddressFromPointSingleton(it, 8)
        if(H3Manager.getH3ResolutionFromAddress(h3Address) == 8 && associatedMeasurements.size > 0) {
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
        if (isAdded) {
            bottomSheetFragment.show(
                parentFragmentManager,
                bottomSheetFragment.tag
            )
        }
        true
    }

    private lateinit var mapView: MapView
    private lateinit var mapboxMap : MapboxMap
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var polygonAnnotationManager: PolygonAnnotationManager? = null
    private var lowResPolygonAnnotationManager: PolygonAnnotationManager? = null
    private var viewAnnotationManager: ViewAnnotationManager? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var annotations: MutableList<PointAnnotation> = mutableListOf()

    private val onCameraChangeListener = OnCameraChangeListener {
        debounceJob?.cancel() // Cancel the previous job if the camera is still moving
        debounceJob = CoroutineScope(Dispatchers.Main).launch {
            delay(100) // Wait for 500ms of no camera movement before loading H3
            polygonAnnotationManager?.deleteAll()
            lowResPolygonAnnotationManager?.deleteAll()
            lowResPolygonAnnotationManager?.removeClickListener(onPolygonClick)

            viewAnnotationManager?.removeAllViewAnnotations()
            Log.i(TAG, "Removing all views")
            mapboxMap.addOnMapClickListener(onMapClickListenerH3)
            loadMapH3()
        }
    }


    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int, count: Int) =
        convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId), count)
    private fun convertDrawableToBitmap(sourceDrawable: Drawable?, count: Int): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            val drawableBitMap = sourceDrawable.bitmap

            val modifiedBitmap = drawableBitMap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(modifiedBitmap)

            //Draw text overlay on bitmap if there's more than one averaged point
            if (count != 1) {
                val paint = Paint()
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.isAntiAlias = true

                val textSize: Float = canvas.width * 0.5f
                paint.textSize = textSize

                // Draw the count onto the Bitmap
                canvas.drawText(
                    count.toString(),
                    (canvas.width / 2).toFloat(),
                    (canvas.height / 2) + (textSize / 3), // Adjust the + (textSize / 3) part to vertically center the text
                    paint
                )
            }

            modifiedBitmap

        } else {
            //Create pin map bitmap

            // copying drawable object to not manipulate on the same reference
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

                val textSize: Float = canvas.width * 0.5f // Adjust this size accordingly
                paint.textSize = textSize
                Log.i("Count Draw", "Textsize: $textSize")

                // Draw the count onto the Bitmap
                canvas.drawText(
                    count.toString(),
                    (canvas.width / 2).toFloat(),
                    (canvas.height / 2) + (textSize / 3), // Adjust the + (textSize / 3) part to vertically center the text
                    paint
                )
            }

            //return pin map with text overlay, if needed
            bitmap
        }
    }

    private fun loadMapAnnotations() {
        if(this.pointAnnotationManager == null) {
            val annotationApi = mapView.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()
            Log.d(TAG, "loadMapAnnotations initialize pointAnnotationManager")
        }

        val coordinates = MapAnnotationManager.getAllCoordinates()
        Log.d(TAG, "loadMapAnnotations got ${coordinates.size} coordinates")
        for (coordinate in coordinates) {
            Log.d(TAG, "coordinate = $coordinate")
            bitmapFromDrawableRes(
                requireContext(),
                R.drawable.fa_solid_location_pin,
                coordinate.count
            )?.let { bitmap ->
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
        val h3Addresses = H3Manager.getH3OverlayAddressesFromCoordinates(mutableListOf(ne, nw, sw, se), 8)
        val h3Boundaries = H3Manager.getH3BoundariesFromAddressList(h3Addresses)

        withContext(Dispatchers.Main) {
            if (polygonAnnotationManager == null) {
                val annotationApi = mapView.annotations
                polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            }
            if (lowResPolygonAnnotationManager == null) {
                val annotationApi = mapView.annotations
                lowResPolygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            }
            if (viewAnnotationManager == null) {
                viewAnnotationManager = mapView.viewAnnotationManager
            }


            val reusablePolygonOptions = PolygonAnnotationOptions()
                .withFillColor("rgba(0, 0, 0, 0)") // Transparent fill color
                .withFillOutlineColor("#0000FF") // Blue outline color


            // Display h3 boundaries
            h3Boundaries.forEach { boundary ->
                val address = H3Manager.getH3AddressFromPointSingleton(boundary.first(), 8)

                val data = JsonObject()
                data.addProperty("h3_address", address)
                Log.i("H3 Child Data", "$data")


                reusablePolygonOptions.withPoints(listOf(boundary))
                polygonAnnotationManager?.create(reusablePolygonOptions.withData(data))
            }

            // Display overlays on hexagons with > 1 point within them
            h3Addresses.forEach { address ->
                val data = JsonObject()
                data.addProperty("h3_address", address)
                Log.i("H3 Child Data", "$data")


                val groups = runBlocking {
                    H3Manager.getMeasurementGroupsAssociatedWithH3Address(address, 8)
                }
                if (groups.size > 1) {
                    val addressBoundary = H3Manager.getH3BoundaryFromAddressSingleton(address)
                    reusablePolygonOptions
                        .withPoints(addressBoundary)
                        .withFillColor("#22B14C") // Green fill color
                        .withFillOpacity(.5)
                        .withData(data)
                    polygonAnnotationManager?.create(reusablePolygonOptions)

                    val hexCenter = H3Manager.getH3CenterFromAddressSingleton(address)

                    val view = LayoutInflater.from(context).inflate(R.layout.view_map_annotaton_layout, mapView, false)
                    val textViewMeasurements = view.findViewById<TextView>(R.id.textView_measurements)
                    textViewMeasurements.text = groups.size.toString()

                    val viewAnnotationOptions = ViewAnnotationOptions.Builder()
                        .geometry(hexCenter)
                        .build()
                    viewAnnotationManager?.addViewAnnotation(view, viewAnnotationOptions)
                }
            }
        }
    }



    private fun displayRes8Hexagons(point: Point) {
        val h3Address = H3Manager.getH3AddressFromPointSingleton(point, 8)
        val h3HexChildren = H3Manager.getRelatedH3Hex(h3Address, 9)
        val h3Boundaries = H3Manager.getH3BoundariesFromAddressList(h3HexChildren)

        Log.i("H3 map click", "H3address: $h3Address, H3boundaries: $h3Boundaries")

        if(lowResPolygonAnnotationManager == null) {
            val annotationApi = mapView.annotations
            lowResPolygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
        }
        if(viewAnnotationManager == null) {
            viewAnnotationManager = mapView.viewAnnotationManager
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
                H3Manager.getMeasurementGroupsAssociatedWithH3Address(address, 6)
            }
            val addressBoundary = H3Manager.getH3BoundaryFromAddressSingleton(address)
            reusablePolygonOptions
                .withPoints(addressBoundary)
                .withFillColor("#22B14C") // Green fill color
                .withData(data)

            if (groups.size > 1) {
                reusablePolygonOptions.withFillOpacity(.5)
                lowResPolygonAnnotationManager?.create(reusablePolygonOptions)

                val hexCenter = H3Manager.getH3CenterFromAddressSingleton(address)

                // Inflate the custom view
                val view = LayoutInflater.from(context).inflate(R.layout.view_map_annotaton_layout, mapView, false)
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
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(13.0)
                .build()
        )

        mapView.gestures.apply {
            pinchToZoomEnabled = false
            quickZoomEnabled = false
        }
        mapView.getMapboxMap().loadStyleUri(
            Style.LIGHT
        ) {
            initLocationComponent()
            mapboxMap.addOnCameraChangeListener(onCameraChangeListener)
            mapboxMap.addOnMapClickListener(onMapClickListenerH3)
        }
    }

    private fun centerCameraOnUser() {
        //Permissions check required by fusedLocationClient
        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } == PackageManager.PERMISSION_GRANTED && context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastKnownLocation->
                    if (lastKnownLocation != null) {
                        Log.i(TAG, "centerCameraOnUser setCamera")
                        mapView.getMapboxMap().setCamera(
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
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                bearingImage = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.mapbox_user_puck_icon,
                ),
                shadowImage = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.mapbox_user_icon_shadow,
                ),
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

    override fun onPause() {
        super.onPause()

        pointAnnotationManager?.deleteAll()
        this.pointAnnotationManager = null
        this.lowResPolygonAnnotationManager = null
        this.polygonAnnotationManager = null
        this.viewAnnotationManager = null
    }
}