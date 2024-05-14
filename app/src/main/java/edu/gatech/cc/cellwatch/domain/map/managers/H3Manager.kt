package edu.gatech.cc.cellwatch.domain.map.managers

import com.mapbox.geojson.Point
import com.uber.h3core.H3Core
import com.uber.h3core.util.GeoCoord
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup

object H3Manager {

    private val h3 = H3Core.newSystemInstance()
    /*
    Responsible for producing coordinates for the hexagon overlays.
    */

    private suspend fun getAllCoordinates(): List<MeasurementGroup> {
        val measurementRepository = CellWatchApp.measurementRepository
        return measurementRepository.getMeasurementGroups()
    }

    private fun h3IndexToBoundary(h3Indexes: List<Long>): MutableList<MutableList<Point>> {
        /*
        Takes in a list of h3 Indexes (longs), creates a list of lists of Mapbox point coordinates associated with each of those indexes.
         */
        val h3Boundaries: MutableList<MutableList<Point>> = mutableListOf()

        h3Indexes.forEach { index ->
            Log.i("H3Manager", getH3ResolutionFromAddress(index).toString())
            val geoBoundary = h3.h3ToGeoBoundary(index)
            // GeoJSON requires the first and last points of a polygon to be the same
            geoBoundary.add(geoBoundary.first())
            h3Boundaries.add(geoCoordListToMapboxPointList(geoBoundary))
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

    fun getRelatedH3Hex(addr: Long, relatedRes: Int): MutableList<Long> {
        return if(h3.h3GetResolution(addr) < relatedRes) {
            h3.h3ToChildren(addr, relatedRes)
        } else if (h3.h3GetResolution(addr) > relatedRes) {
            mutableListOf(h3.h3ToParent(addr, relatedRes))
        } else {
            mutableListOf(addr)
        }
    }

    suspend fun getMeasurementGroupsAssociatedWithLatLong(coord: Point): MutableList<MeasurementGroup> {
        return getMeasurementGroupsAssociatedWithH3Address(
            h3.geoToH3(coord.latitude(), coord.longitude(), 8),
            8,
        )
    }

    suspend fun getMeasurementGroupsAssociatedWithH3Address(address: Long, res: Int): MutableList<MeasurementGroup> {
        // Takes in a h3 address, returns all measurements associated H3 hexagon that contains the point.
        val allMeasurements = getAllCoordinates()
        val associatedGroupList: MutableList<MeasurementGroup> = mutableListOf()

        for (group in allMeasurements) {
            for (measurement in listOfNotNull(group.latency, group.download, group.upload)) {
                if (measurement.locations?.any { location ->
                    location.lat != null && location.lon != null
                    && h3.geoToH3(location.lat, location.lon, res) == address
                } == true) {
                    associatedGroupList.add(group)
                    break
                }
            }
        }

        return associatedGroupList
    }

    fun getH3ResolutionFromAddress(addr: Long): Int {
        return h3.h3GetResolution(addr)
    }

    fun getH3CenterFromAddressSingleton(addr: Long): Point {
        val centerGeo = h3.h3ToGeo(addr)
        return Point.fromLngLat(centerGeo.lng, centerGeo.lat)
    }

    fun getH3Index(lat: Double, lon: Double, res: Int = 8): String {
        return h3.geoToH3Address(lat, lon, res)
    }
}
