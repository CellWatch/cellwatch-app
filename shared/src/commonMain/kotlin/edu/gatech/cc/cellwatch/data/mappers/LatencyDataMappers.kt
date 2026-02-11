package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.db.LatencyDataEntity
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import kotlinx.datetime.Instant

fun LatencyDataEntity.toDomain(): LatencyData = LatencyData(
    id = id,
    measurementId = measurementId,
    rtt = rtt?.toInt(),
    jitter = jitter?.toInt(),
    sent = sent?.toInt(),
    received = received?.toInt(),
    servers = servers.toStringListOrNull(),
    createdOn = createdOn?.let(Instant::fromEpochMilliseconds),
    updatedOn = updatedOn?.let(Instant::fromEpochMilliseconds),
)

fun LatencyData.toRow(): LatencyDataEntity = LatencyDataEntity(
    id = id,
    measurementId = measurementId,
    rtt = rtt?.toLong(),
    jitter = jitter?.toLong(),
    sent = sent?.toLong(),
    received = received?.toLong(),
    servers = servers.toSqlStringList(),
    createdOn = createdOn?.toEpochMilliseconds(),
    updatedOn = updatedOn?.toEpochMilliseconds(),
)
