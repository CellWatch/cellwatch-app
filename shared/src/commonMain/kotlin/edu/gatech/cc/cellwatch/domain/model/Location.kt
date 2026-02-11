package edu.gatech.cc.cellwatch.domain.model

import com.benasher44.uuid.uuid4
import kotlinx.datetime.Instant

data class Location(
    var id: String = uuid4().toString(),
    val timestamp: Instant? = null,
    val lat: Double,
    val lon: Double,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val speedAccuracy: Double? = null,
    val heading: Double? = null,
    var measurementId: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
)
