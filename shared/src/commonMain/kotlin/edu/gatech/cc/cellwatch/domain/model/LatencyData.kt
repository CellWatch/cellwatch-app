package edu.gatech.cc.cellwatch.domain.model

import com.benasher44.uuid.uuid4
import kotlinx.datetime.Instant

data class LatencyData(
    var id: String = uuid4().toString(),
    var measurementId: String? = null,
    val rtt: Int? = null,
    val jitter: Int? = null,
    val sent: Int? = null,
    val received: Int? = null,
    val servers: List<String>? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
)
