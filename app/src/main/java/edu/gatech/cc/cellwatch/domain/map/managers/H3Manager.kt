package edu.gatech.cc.cellwatch.domain.map.managers

import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import com.uber.h3core.H3Core
import com.uber.h3core.util.GeoCoord
import edu.gatech.cc.cellwatch.core.util.Log

object H3Manager {
    const val PARENT_HEX_RES = 8
    const val CHILD_HEX_RES = 9

    private val h3 = H3Core.newSystemInstance()
    /*
    Responsible for producing coordinates for the hexagon overlays.
    */

    private fun h3IndexToBoundary(h3Indexes: Collection<Long>): MutableList<MutableList<Point>> {
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

    private fun boundsToGeoCoords(bounds: CoordinateBounds): List<GeoCoord> {
        return listOf(
            GeoCoord(bounds.northeast.latitude(), bounds.northeast.longitude()),
            GeoCoord(bounds.northwest().latitude(), bounds.northwest().longitude()),
            GeoCoord(bounds.southwest.latitude(), bounds.southwest.longitude()),
            GeoCoord(bounds.southeast().latitude(), bounds.southeast().longitude()),
        )
    }

     fun getH3OverlayAddressesFromBounds(bounds: CoordinateBounds, resolution: Int): List<Long> {
         val geoListBoundaries = boundsToGeoCoords(bounds)
         return h3.polyfill(geoListBoundaries, mutableListOf(), resolution)
    }

    fun getH3BoundaryFromAddressSingleton(h3address: Long): MutableList<MutableList<Point>> {
        return h3IndexToBoundary(mutableListOf(h3address))
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

    fun getH3ResolutionFromAddress(addr: Long): Int {
        return h3.h3GetResolution(addr)
    }

    fun getH3CenterFromAddressSingleton(addr: Long): Point {
        val centerGeo = h3.h3ToGeo(addr)
        return Point.fromLngLat(centerGeo.lng, centerGeo.lat)
    }

    fun getH3Index(lat: Double, lon: Double, res: Int): Long {
        return h3.geoToH3(lat, lon, res)
    }
}
