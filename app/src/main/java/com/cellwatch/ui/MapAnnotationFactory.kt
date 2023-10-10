package com.cellwatch.ui

import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(val lat: Double, val long: Double, val count: Int)

fun distance(coord1: Coordinate, coord2: Coordinate): Double {
    val dLat = Math.toRadians(coord2.lat - coord1.lat)
    val dLng = Math.toRadians(coord2.long - coord1.long)


    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(coord1.lat)) * cos(Math.toRadians(coord2.lat)) *
            sin(dLng / 2).pow(2.0)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return 3958.8 * c
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
                Log.i("Point merging", "Merging coordinates " + coordinates[i] + " " + coordinates[j] + " into " + averageLat + " " +averageLong)

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

fun groupCoords(): MutableList<Coordinate> {
    val coords = mutableListOf(
        Coordinate(40.7128, -74.0060, 1),
        Coordinate(40.7033, -74.0170, 1),
        Coordinate(40.7851, -73.9683, 1),
        Coordinate(37.7749, -122.4194, 1),
        Coordinate(37.8080, -122.4177, 1),
        Coordinate(34.0522, -118.2437, 1),
        Coordinate(19.43, -99.133, 1),
        Coordinate(46.8771, -96.0, 1),
        Coordinate(46.8771, -96.0, 1)
    )
    val threshold = 1.0 //in miles
    val result = groupCoordinates(coords, threshold)
    for (coord in result) {
        Log.i("Point Groupings", "$coord")
    }

    return result

    /*
    return mutableListOf(
        Coordinate(40.7128, -74.0060, 1),
        Coordinate(40.7033, -74.0170, 1),
        Coordinate(40.7851, -73.9683, 1),
        Coordinate(37.7749, -122.4194, 1),
        Coordinate(37.8080, -122.4177, 1),
        Coordinate(34.0522, -118.2437, 1)
    )

     */
}