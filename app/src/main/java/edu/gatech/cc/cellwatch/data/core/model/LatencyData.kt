package edu.gatech.cc.cellwatch.data.core.model

import edu.gatech.cc.cellwatch.data.local.model.LatencyDataEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkLatencyData

fun NetworkLatencyData.asEntity() = LatencyDataEntity(
    id, measurementId, rtt, jitter, sent, received,
    servers = servers?.let { ArrayList(it) },
    createdOn, updatedOn
)

fun LatencyDataEntity.asNetworkModel() = NetworkLatencyData(
    id, measurementId, rtt, jitter, sent, received,
    servers = servers?.let { ArrayList(it) },
    createdOn, updatedOn
)
