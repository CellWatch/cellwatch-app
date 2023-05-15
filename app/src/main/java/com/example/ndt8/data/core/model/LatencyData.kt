package com.example.ndt8.data.core.model

import com.example.ndt8.data.local.model.LatencyDataEntity
import com.example.ndt8.data.network.model.NetworkLatencyData

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