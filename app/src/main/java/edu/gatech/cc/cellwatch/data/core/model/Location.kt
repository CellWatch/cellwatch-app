package edu.gatech.cc.cellwatch.data.core.model

import edu.gatech.cc.cellwatch.data.local.model.LocationEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkLocation

fun NetworkLocation.asEntity() = LocationEntity(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId //, createdOn, updatedOn
)

fun LocationEntity.asNetworkModel() = NetworkLocation(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId //, createdOn, updatedOn
)
