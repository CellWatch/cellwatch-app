package com.cellwatch.domain.map.managers

import android.util.Log
import com.cellwatch.data.model.Measurement
import com.cellwatch.domain.map.model.Coordinate
import kotlinx.coroutines.runBlocking
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object MapAnnotationManager {
    private val TAG = this::class.simpleName

    fun getAllCoordinates(): MutableList<Coordinate> {
        val measurementRepository = com.cellwatch.CellWatchApp.measurementRepository

        Log.d(TAG,"Getting stored measurements");
        var measurements = runBlocking { measurementRepository.getMeasurementsWithData() };
        val coords: MutableList<Coordinate> = measurementsToCoordinates(measurements)

        Log.d(TAG, "Got ${coords.size} measurements")

        val threshold = 1.0 //in miles
        val groupedCoordinates = groupCoordinates(coords, threshold)
        for (coord in groupedCoordinates) {
            Log.i(TAG, "coord = $coord")
        }

        return groupedCoordinates
    }

    fun groupCoordinates(coordinates: MutableList<Coordinate>, threshold: Double): MutableList<Coordinate> {
        var i = 0
        while (i < coordinates.size) {
            var j = i + 1
            while (j < coordinates.size) {
                if (distance(coordinates[i], coordinates[j]) < threshold) {
                    val averageLat = (coordinates[i].lat + coordinates[j].lat) / 2
                    val averageLong = (coordinates[i].long + coordinates[j].long) / 2
                    val count = max(coordinates[i].count, coordinates[j].count) + 1

                    coordinates[i] = Coordinate(averageLat, averageLong, count)
                    Log.i(TAG, "Merging coordinates " + coordinates[i] + " " + coordinates[j] + " into " + averageLat + " " +averageLong)

                    coordinates.removeAt(j)
                    i = 0  // Reset i to check previously checked coordinates with the new merged one
                    break  // Exit the inner loop to restart with the updated coordinates list
                } else {
                    j++
                }
            }
            if (j == coordinates.size) {
                i++  // Increment i only if the inner loop completes without merging
            }
        }

        return coordinates
    }

    private fun measurementsToCoordinates(measurements: List<Measurement>): MutableList<Coordinate> {
        var coords: MutableList<Coordinate> = mutableListOf()

        for (measurement in measurements) {
            if (measurement.locations != null) {
                coords = measurement.locations?.map { location ->
                    Coordinate(location.lat!!, location.lon!!, 1)
                }!!.toMutableList()
            }
        }

        return coords
    }

    private fun distance(coord1: Coordinate, coord2: Coordinate): Double {
        val dLat = Math.toRadians(coord2.lat - coord1.lat)
        val dLng = Math.toRadians(coord2.long - coord1.long)

        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(coord1.lat)) * cos(Math.toRadians(coord2.lat)) *
                sin(dLng / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return 3958.8 * c
    }
}