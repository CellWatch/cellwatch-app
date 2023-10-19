package com.cellwatch.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.GravityCompat
import androidx.navigation.Navigation
import com.cellwatch.R
import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.Location
import com.cellwatch.databinding.FragmentMapBinding
import com.cellwatch.domain.fcc.LatencyResult
import com.cellwatch.domain.fcc.ThroughputResult
import com.cellwatch.domain.map.managers.MapAnnotationManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import kotlin.math.roundToInt

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
//private const val ARG_PARAM1 = "param1"
//private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MapFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MapFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var _binding: FragmentMapBinding? = null

    // TODO: Rename and change types of parameters
//    private var param1: String? = null
//    private var param2: String? = null

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        Log.d(TAG, "onCreate")
//        arguments?.let {
//            param1 = it.getString(ARG_PARAM1)
//            param2 = it.getString(ARG_PARAM2)
//        }
//    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate")

//        setContentView(R.layout.activity_map)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")
        // Inflate the layout for this fragment
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated!")

        mapView = binding.mapView //findViewById(R.id.mapView)
        mapboxMap = mapView.getMapboxMap()
        mapboxMap.loadStyleUri(Style.LIGHT)
        onMapReady()

        val drawerLayout = binding.drawerLayout //findViewById<DrawerLayout>(R.id.drawerLayout)
        val hamburgerButton = binding.sideMenuButton //findViewById<ImageButton>(R.id.sideMenuButton)
        val exitButton = binding.menuCloseButton //findViewById<ImageButton>(R.id.menuCloseButton)
        val h3ToggleSwitch = binding.h3ToggleSwitch //findViewById<SwitchCompat>(R.id.h3ToggleSwitch)
        val measureButton = binding.measureButton //findViewById<Button>(R.id.measureButton)
        val centerButton = binding.centerUserButton //findViewById<Button>(R.id.centerUserButton)

        measureButton.setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.navigateToMeasurementFragment)
        }

        centerButton.setOnClickListener{
            mapView.location.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
            mapView.location.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        }

        hamburgerButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        exitButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        h3ToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "H3 Toggle Switch = ${isChecked.toString()}")
            if(isChecked) {
                loadMapAnnotations()
            } else {
                pointAnnotationManager.deleteAll()
            }
        }

        fun locationToString(l: Location?): String {
            if (l == null) {
                return ""
            }

            return String.format("lat=%f, lon=%f, speed=%.2fm/s", l.lat, l.lon, l.speed)
        }

        fun handleLatencyComplete(r: LatencyResult, l: List<Location>, c: List<Cell>) {
            if (!r.success) {
//                binding.latencyContent.text = "failed"
                return
            }

//            binding.latencyContent.text = "success"
            val mean = "Mean RTT: ${r.meanRtt / 1e3}ms"
            val jitter = "Jitter: ${r.jitter / 1e3}ms"
            val received = "Received: ${r.packetsReceived}/${r.packetsSent}"
            val start = "Start time: ${r.start}"
            val duration = "Duration: ${r.usecs / 1e6}s"
            val target = "Target host: ${r.targetHost}"
            val startLoc = "Start location: ${locationToString(l.getOrNull(0))}"
            val endLoc = "End location: ${locationToString(l.getOrNull(1))}"
            val cells = "Cells: $c"
            Log.d(TAG, "$mean\n$jitter\n$received\n$start\n$duration\n$target\n$startLoc\n$endLoc\n$cells")

//            binding.latencyDetails.text = "$mean\n$jitter\n$received\n$start\n$duration\n$target\n$startLoc\n$endLoc\n$cells"
        }

        fun handleThroughputComplete(r: ThroughputResult, l: List<Location>, c: List<Cell>) {
            if (!r.success || r.activeMetrics == null) {
                return
            }

            val speed = "Speed: ${(r.activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()} Mbps"
            val start = "Start time: ${r.start}"
            val duration = "Duration: ${(r.activeMetrics.usecs + (r.warmupMetrics?.usecs ?: 0)) / 1e6}s"
            val target = "Target host: ${r.targetHost}"
            val startLoc = "Start location: ${locationToString(l.getOrNull(0))}"
            val endLoc = "End location: ${locationToString(l.getOrNull(1))}"
            val cells = "Cells: ${c}"
            Log.d(TAG, "$speed\n$start\n$duration\n$target\n$startLoc\n$endLoc\n$cells")
        }

//
    }

    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
//        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
        Log.d(TAG, "Bearing changed to $it")
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
    }

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }

    private lateinit var mapView: MapView
    private lateinit var mapboxMap : MapboxMap
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private var annotations: MutableList<PointAnnotation> = mutableListOf()


    private fun onCameraTrackingDismissed() {
        Toast.makeText(context, "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }
    private fun setupGesturesListener() {
        mapView.gestures.addOnMoveListener(onMoveListener)
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
        if(!this::pointAnnotationManager.isInitialized) {
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
                R.drawable.blue_marker_transparent,
                coordinate.count
            )?.let { bitmap ->
                val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(coordinate.long, coordinate.lat))
                    .withIconImage(bitmap)
                val pointAnnotation = pointAnnotationManager.create(pointAnnotationOptions)
                pointAnnotation.let { annotations.add(it) }
            }
        }
    }

    private fun onMapReady() {
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(14.0)
                .build()
        )
        mapView.getMapboxMap().loadStyleUri(
            Style.LIGHT
        ) {
            initLocationComponent()
            setupGesturesListener()
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
        Log.d(TAG, "Register map callbacks")
        locationComponentPlugin.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        locationComponentPlugin.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MapFragment.
         */
        // TODO: Rename and change types and number of parameters
//        @JvmStatic
//        fun newInstance(param1: String, param2: String) =
//            MapFragment().apply {
//                arguments = Bundle().apply {
//                    putString(ARG_PARAM1, param1)
//                    putString(ARG_PARAM2, param2)
//                }
//            }
    }
}