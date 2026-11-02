package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager

class MapViewModel: ViewModel() {
    // map group ID to MeasurementGroup
    private val measurementGroups = mutableMapOf<String, MeasurementGroup>()

    // map hex ID to group IDs, for both sizes of hexagon the map draws
    private val hexMeasurementGroupIds = mutableMapOf<Long, MutableSet<String>>()

    var selectedMeasurementGroupIds = setOf<String>()

    suspend fun refreshMeasurementGroups(): Set<String> {
        val groups = CellWatchApp.measurementRepository.getMeasurementGroups()
        groups.forEach { group ->
            val id = group.id
            if (id !in measurementGroups) {
                listOfNotNull(
                    group.latency?.centerLatLon(),
                    group.download?.centerLatLon(),
                    group.upload?.centerLatLon(),
                ).forEach { latlon ->
                    // Each size comes from the location itself. H3's small hexagons don't nest
                    // exactly in the large ones, so near an edge a small hexagon's parent isn't the
                    // large hexagon the location is in.
                    for (res in listOf(H3Manager.PARENT_HEX_RES, H3Manager.CHILD_HEX_RES)) {
                        val address = H3Manager.getH3Index(latlon.first, latlon.second, res)
                        hexMeasurementGroupIds.getOrPut(address) { mutableSetOf() }.add(id)
                    }
                }
            }
            measurementGroups[group.id] = group
        }
        return measurementGroups.keys
    }

    fun getMeasurementGroups(ids: Collection<String>): Collection<MeasurementGroup> {
        return ids.map { measurementGroups[it] ?: throw RuntimeException("missing group $it") }
    }

    fun getHexMeasurementGroupIds(address: Long): Collection<String> {
        val res = H3Manager.getH3ResolutionFromAddress(address)
        if (res != H3Manager.PARENT_HEX_RES && res != H3Manager.CHILD_HEX_RES) {
            throw RuntimeException("need res ${H3Manager.PARENT_HEX_RES} or ${H3Manager.CHILD_HEX_RES}, got $res")
        }

        return hexMeasurementGroupIds[address] ?: setOf()
    }
}