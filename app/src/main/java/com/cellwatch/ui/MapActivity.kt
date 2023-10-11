package com.cellwatch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.cellwatch.R
import com.mapbox.maps.MapView
import com.mapbox.maps.Style

import androidx.appcompat.content.res.AppCompatResources
import com.cellwatch.domain.map.managers.MapAnnotationManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
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

class MapActivity : AppCompatActivity() {
    private val TAG = "MapActivity"

    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
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
        Toast.makeText(this, "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
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
        }

        for (coordinate in MapAnnotationManager.getAllCoordinates()) {
            bitmapFromDrawableRes(
                this@MapActivity,
                R.drawable.blue_marker_transparent,
                coordinate.count
            )?.let {
                val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(coordinate.long, coordinate.lat))
                    .withIconImage(it)
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
                    this@MapActivity,
                    R.drawable.mapbox_user_puck_icon,
                ),
                shadowImage = AppCompatResources.getDrawable(
                    this@MapActivity,
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
        locationComponentPlugin.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        locationComponentPlugin.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        mapView = findViewById(R.id.mapView)
        mapboxMap = mapView.getMapboxMap()
        mapboxMap.loadStyleUri(Style.LIGHT)
        onMapReady()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val hamburgerButton = findViewById<ImageButton>(R.id.sideMenuButton)
        val exitButton = findViewById<ImageButton>(R.id.menuCloseButton)
        val h3ToggleSwitch = findViewById<SwitchCompat>(R.id.h3ToggleSwitch)
        val measureButton = findViewById<Button>(R.id.measureButton)
        val centerButton = findViewById<Button>(R.id.centerUserButton)

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
            if(isChecked) {
                loadMapAnnotations()
            } else {
                pointAnnotationManager.deleteAll()
            }
        }

        measureButton.setOnClickListener {
            Toast.makeText(this@MapActivity, "Measure taken1", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }
}