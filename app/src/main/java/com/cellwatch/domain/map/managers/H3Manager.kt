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

    private fun getAllCoordinates(): List<Measurement> {
        val measurementRepository = CellWatchApp.measurementRepository

        return runBlocking { measurementRepository.getMeasurementsWithData() };
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
            h3IndexList.add(h3.geoToH3Address(coord.lat, coord.long, 4))
        }

        return h3IndexList
    }

    private fun h3IndexToBoundary(h3Indexes: List<Long>): MutableList<MutableList<Point>> {
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

     fun getH3OverlayAddressesFromCoordinates(coordinates: MutableList<Point>, resolution: Int): MutableList<Long> {
        /*
        Takes in a list of coordinates as a boundary (eg: the camera on a map), returns all h3 hexagon boundaries within those coordinates.
         */

         val geoListBoundaries = pointListToGeoCoordList(coordinates)

         return h3.polyfill(geoListBoundaries, mutableListOf(), resolution)
    }

    fun getH3BoundariesFromAddressList(h3Addresses: MutableList<Long>): MutableList<MutableList<Point>> {
        return h3IndexToBoundary(h3Addresses)
    }

    fun getH3BoundaryFromAddressSingleton(h3address: Long): MutableList<MutableList<Point>> {
        return h3IndexToBoundary(mutableListOf(h3address))
    }

    fun getH3AddressFromPointSingleton(point: Point, res: Int): Long {
        return h3.geoToH3(point.latitude(), point.longitude(), res)
    }

    fun getRelatedH3Hex(addr: Long, childRes: Int): MutableList<Long> {
        return h3.h3ToChildren(addr, childRes)
    }

    fun getMeasurementsAssociatedWithLatLong(coord: Point): MutableList<Measurement> {
        /*
        Takes in a mapbox lat/long point, returns all measurements associated with the 5 res H3 hexagon that contains the point.
         */
        val h3IndexFromPoint = h3.geoToH3Address(coord.latitude(), coord.longitude(), 5)
        val allMeasurements = getAllCoordinates()
        val associatedMeasurementList: MutableList<Measurement> = mutableListOf()

        for (measurement in allMeasurements) {
            measurement.locations?.forEach { location ->
                if ((location.lat != null) && (location.lon != null) && (h3.geoToH3Address(
                        location.lat,
                        location.lon,
                        5
                    ) == h3IndexFromPoint)
                ) {
                    associatedMeasurementList.add(measurement)
                }
            }
        }

        return associatedMeasurementList
    }

    fun getMeasurementsAssociatedWithH3Address(address: Long, res: Int): MutableList<Measurement> {
        /*
        Takes in a h3 address, returns all measurements associated with the 5 res H3 hexagon that contains the point.
         */
        val allMeasurements = getAllCoordinates()
        val associatedMeasurementList: MutableList<Measurement> = mutableListOf()

        for (measurement in allMeasurements) {
            measurement.locations?.forEach { location ->
                if ((location.lat != null) && (location.lon != null) && (h3.geoToH3(
                        location.lat,
                        location.lon,
                        res
                    ) == address)
                ) {
                    associatedMeasurementList.add(measurement)
                }
            }
        }

        return associatedMeasurementList
    }

    fun getH3ResolutionFromAddress(addr: Long): Int {
        return h3.h3GetResolution(addr)
    }

    /*
    fun isPointInPolygon(polygon: List<List<Point>>, point: Point): Boolean {
        var intersectCount = 0
        for (j in polygon.indices) {
            val current = polygon[j]
            val next = polygon[(j + 1) % polygon.size]
            if ((current.latitude() > point.latitude()) != (next.latitude() > point.latitude()) &&
                (point.longitude() < (next.longitude() - current.longitude()) *
                        (point.latitude() - current.latitude()) / (next.latitude() - current.latitude()) + current.longitude())
            ) {
                intersectCount++
            }
        }
        // If the number of intersections is odd, the point is inside the polygon
        return (intersectCount % 2 == 1)
    }

     */

}