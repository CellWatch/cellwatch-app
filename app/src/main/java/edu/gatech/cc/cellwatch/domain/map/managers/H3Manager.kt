package edu.gatech.cc.cellwatch.domain.map.managers

import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng
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
            val geoBoundary = h3.cellToBoundary(index).toMutableList()
            // GeoJSON requires the first and last points of a polygon to be the same
            geoBoundary.add(geoBoundary.first())
            h3Boundaries.add(latLngListToMapboxPointList(geoBoundary))
        }

        return h3Boundaries
    }

    private fun latLngListToMapboxPointList(latLngList: MutableList<LatLng>): MutableList<Point> {
        /*
        Takes in a list of Uber LatLngs, converts to list of MapBox Points.
         */
        val h3Boundaries: MutableList<Point> = mutableListOf()

        latLngList.forEach { latLng ->
            h3Boundaries.add(Point.fromLngLat(latLng.lng, latLng.lat))

        }
        return h3Boundaries
    }

    private fun boundsToLatLngs(bounds: CoordinateBounds): List<LatLng> {
        return listOf(
            LatLng(bounds.northeast.latitude(), bounds.northeast.longitude()),
            LatLng(bounds.northwest().latitude(), bounds.northwest().longitude()),
            LatLng(bounds.southwest.latitude(), bounds.southwest.longitude()),
            LatLng(bounds.southeast().latitude(), bounds.southeast().longitude()),
        )
    }

     fun getH3OverlayAddressesFromBounds(bounds: CoordinateBounds, resolution: Int): List<Long> {
         val geoListBoundaries = boundsToLatLngs(bounds)
         return h3.polygonToCells(geoListBoundaries, mutableListOf(), resolution)
    }

    fun getH3BoundaryFromAddressSingleton(h3address: Long): MutableList<MutableList<Point>> {
        return h3IndexToBoundary(mutableListOf(h3address))
    }

    fun getRelatedH3Hex(addr: Long, relatedRes: Int): MutableList<Long> {
        return if(h3.getResolution(addr) < relatedRes) {
            h3.cellToChildren(addr, relatedRes)
        } else if (h3.getResolution(addr) > relatedRes) {
            mutableListOf(h3.cellToParent(addr, relatedRes))
        } else {
            mutableListOf(addr)
        }
    }

    fun getH3ResolutionFromAddress(addr: Long): Int {
        return h3.getResolution(addr)
    }

    fun getH3CenterFromAddressSingleton(addr: Long): Point {
        val centerGeo = h3.cellToLatLng(addr)
        return Point.fromLngLat(centerGeo.lng, centerGeo.lat)
    }

    fun getH3Index(lat: Double, lon: Double, res: Int): Long {
        return h3.latLngToCell(lat, lon, res)
    }

    fun isInH3Hex(lat: Double, lon: Double, addr: Long): Boolean {
        val ret = getH3Index(lat, lon, getH3ResolutionFromAddress(addr)) == addr
        return ret
    }
}
