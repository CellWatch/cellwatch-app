package com.cellwatch.data.core.model

import com.cellwatch.data.local.model.LatencyDataEntity
import com.cellwatch.data.network.model.NetworkLatencyData

fun NetworkLatencyData.asEntity() = LatencyDataEntity(
    id, measurementId, rtt, jitter, sent, received,
    servers = ArrayList(servers),
    createdOn, updatedOn
)

fun LatencyDataEntity.asNetworkModel() = NetworkLatencyData(
    id, measurementId, rtt, jitter, sent, received,
    servers = ArrayList(servers),
    createdOn, updatedOn
)