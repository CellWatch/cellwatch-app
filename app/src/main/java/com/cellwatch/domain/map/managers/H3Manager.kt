package com.cellwatch.domain.map.managers

import android.util.Log
import com.cellwatch.CellWatchApp
import com.cellwatch.data.model.Measurement
import com.cellwatch.domain.map.model.Coordinate
import com.mapbox.geojson.Point
import com.uber.h3core.H3Core
import com.uber.h3core.util.GeoCoord
import kotlinx.coroutines.runBlocking

object H3Manager {

    private val h3 = H3Core.newSystemInstance()
    /*
    Responsible for producing coordinates for the hexagon overlays.
    */
    private val TAG = this::class.simpleName

    private fun getAllCoordinates(): MutableList<Coordinate> {
        val measurementRepository = CellWatchApp.measurementRepository

        Log.d(TAG,"Getting stored measurements");
        val measurements = runBlocking { measurementRepository.getMeasurementsWithData() };
        Log.d(TAG, "*** Got ${measurements.size} measurements ***")

        val coords: MutableList<Coordinate> =
            measurementsToCoordinates(measurements)

        Log.d(TAG, "Got ${coords.size} measurements")

        val threshold = 1.0 //in miles
        val groupedCoordinates = MapAnnotationManager.groupCoordinates(coords, threshold)
        for (coord in groupedCoordinates) {
            Log.i(TAG, "coord = $coord")
        }

        return groupedCoordinates
    }

    private fun measurementsToCoordinates(measurements: List<Measurement>): MutableList<Coordinate> {
        val coords: MutableList<Coordinate> = mutableListOf()

        for (measurement in measurements) {
            if (measurement.locations != null) {
                val measurementCoords = measurement.locations?.map { location ->
                    Coordinate(location.lat!!, location.lon!!, 1)
                }!!.toList()
                coords.addAll(measurementCoords)
            } else {
                Log.e(TAG, "!!!!! measurement ${measurement.id} has no locations !!!!!!")
            }
        }

        return coords
    }

    private fun latLongToH3Index(coords: MutableList<Coordinate>): MutableList<String> {
        /*
        Takes a list of lat/long coordinates, converts to a list of h3 indexes associated with those coordinates.
         */
        val h3IndexList: MutableList<String> = mutableListOf()

        coords.forEach { coord ->
            h3IndexList.add(h3.geoToH3Address(coord.lat, coord.long, 5))
        }

        return h3IndexList
    }

    private fun h3IndexToBoundary(h3Indexes: MutableList<Long>): MutableList<MutableList<Point>> {
        /*
        Takes in a list of h3 Indexes (longs), creates a list of lists of Mapbox point coordinates associated with each of those indexes.
         */
        val h3Boundaries: MutableList<MutableList<Point>> = mutableListOf()

        h3Indexes.forEach { index ->
            h3Boundaries.add(geoCoordListToMapboxPointList(h3.h3ToGeoBoundary(index)))
        }

        return h3Boundaries
    }

    private fun geoCoordListToMapboxPointList(geoCoordList: MutableList<GeoCoord>): MutableList<Point> {
        /*
        Takes in a list of Uber GeoCoords, converts to list of MapBox Points.
         */
        val h3Boundaries: MutableList<Point> = mutableListOf()

        geoCoordList.forEach { geoCoord ->
            h3Boundaries.add(Point.fromLngLat(geoCoord.lng, geoCoord.lat))

        }
        return h3Boundaries
    }

    private fun pointListToGeoCoordList(pointList: MutableList<Point>): MutableList<GeoCoord> {
        /*
        Takes in a list of Mapbox Points, converts to list of Uber Geocoords.
         */
        val geoCoordList: MutableList<GeoCoord> = mutableListOf()

        pointList.forEach { point ->
            geoCoordList.add(GeoCoord(point.longitude(), point.latitude()))

        }
        return geoCoordList
    }

     fun getH3OverlayFromCoordinates(coordinates: MutableList<Point>, resolution: Int): MutableList<MutableList<Point>> {
        /*
        Takes in a list of coordinates as a boundary (eg: the camera on a map), returns all h3 hexagon boundaries within those coordinates.
         */

         val geoListBoundaries = pointListToGeoCoordList(coordinates)
         val h3HexIndexes = h3.polyfill(geoListBoundaries, mutableListOf(), resolution)

         return h3IndexToBoundary(h3HexIndexes)
    }
}