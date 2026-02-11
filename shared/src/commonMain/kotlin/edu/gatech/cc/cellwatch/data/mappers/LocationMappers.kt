package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.db.LocationEntity
import edu.gatech.cc.cellwatch.domain.model.Location
import kotlinx.datetime.Instant

fun LocationEntity.toDomain(): Location = Location(
    id = id,
    timestamp = timestamp?.let(Instant::fromEpochMilliseconds),
    lat = lat,
    lon = lon,
    accuracy = accuracy,
    speed = speed,
    speedAccuracy = speedAccuracy,
    heading = heading,
    measurementId = measurementId,
    createdOn = createdOn?.let(Instant::fromEpochMilliseconds),
    updatedOn = updatedOn?.let(Instant::fromEpochMilliseconds),
)

fun Location.toRow(): LocationEntity = LocationEntity(
    id = id,
    timestamp = timestamp?.toEpochMilliseconds(),
    lat = lat,
    lon = lon,
    accuracy = accuracy,
    speed = speed,
    speedAccuracy = speedAccuracy,
    heading = heading,
    measurementId = measurementId,
    createdOn = createdOn?.toEpochMilliseconds(),
    updatedOn = updatedOn?.toEpochMilliseconds(),
)
