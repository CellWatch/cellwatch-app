package com.example.ndt8.data.core.model

import com.example.ndt8.data.local.model.LocationEntity
import com.example.ndt8.data.network.model.NetworkLocation

fun NetworkLocation.asEntity() = LocationEntity(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId, createdOn, updatedOn
)

fun LocationEntity.asNetworkModel() = NetworkLocation(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId, createdOn, updatedOn
)