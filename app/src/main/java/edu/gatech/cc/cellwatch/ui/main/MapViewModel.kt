package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager

class MapViewModel: ViewModel() {
    // map group ID to MeasurementGroup
    private val measurementGroups = mutableMapOf<String, MeasurementGroup>()

    // map hex ID to group IDs
    private val childHexMeasurementGroupIds = mutableMapOf<Long, MutableSet<String>>()

    var selectedMeasurementGroupIds = setOf<String>()

    suspend fun refreshMeasurementGroups(): Set<String> {
        val groups = CellWatchApp.measurementRepository.getMeasurementGroups()
        groups.forEach { group ->
            val id = group.id()
            if (id !in measurementGroups) {
                listOfNotNull(
                    group.latency?.centerLatLon(),
                    group.download?.centerLatLon(),
                    group.upload?.centerLatLon(),
                ).forEach { latlon ->
                    val address = H3Manager.getH3Index(latlon.first, latlon.second, H3Manager.CHILD_HEX_RES)
                    val ids = childHexMeasurementGroupIds[address] ?: mutableSetOf()
                    ids.add(id)
                    childHexMeasurementGroupIds[address] = ids
                }
            }
            measurementGroups[group.id()] = group
        }
        return measurementGroups.keys
    }

    fun getMeasurementGroups(ids: Collection<String>): Collection<MeasurementGroup> {
        return ids.map { measurementGroups[it] ?: throw RuntimeException("missing group $it") }
    }

    fun getHexMeasurementGroupIds(address: Long): Collection<String> {
        val res = H3Manager.getH3ResolutionFromAddress(address)
        if (res > H3Manager.CHILD_HEX_RES) {
            throw RuntimeException("need res <= ${H3Manager.CHILD_HEX_RES}, got $res")
        }

        if (res == H3Manager.CHILD_HEX_RES) {
            return childHexMeasurementGroupIds[address] ?: setOf()
        }

        return H3Manager.getRelatedH3Hex(address, H3Manager.CHILD_HEX_RES)
            .mapNotNull { childHexMeasurementGroupIds[it] }
            .flatten()
            .toSet()
    }
}