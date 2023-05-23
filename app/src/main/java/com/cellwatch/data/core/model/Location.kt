package com.cellwatch.data.core.model

import com.cellwatch.data.local.model.LocationEntity
import com.cellwatch.data.network.model.NetworkLocation

fun NetworkLocation.asEntity() = LocationEntity(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId, createdOn, updatedOn
)

fun LocationEntity.asNetworkModel() = NetworkLocation(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId, createdOn, updatedOn
)