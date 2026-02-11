package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.db.UploadDownloadDataEntity
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant

fun UploadDownloadDataEntity.toDomain(): UploadDownloadData = UploadDownloadData(
    id = id,
    measurementId = measurementId,
    warmupDuration = warmupDuration,
    warmupBytes = warmupBytes,
    duration = duration,
    bytes = bytes,
    bytesPerSec = bytesPerSec,
    applicationBytes = applicationBytes,
    applicationBytesPerSec = applicationBytesPerSec,
    servers = servers.toStringListOrNull(),
    createdOn = createdOn?.let(Instant::fromEpochMilliseconds),
    updatedOn = updatedOn?.let(Instant::fromEpochMilliseconds),
)

fun UploadDownloadData.toRow(): UploadDownloadDataEntity = UploadDownloadDataEntity(
    id = id,
    measurementId = measurementId,
    warmupDuration = warmupDuration,
    warmupBytes = warmupBytes,
    duration = duration,
    bytes = bytes,
    bytesPerSec = bytesPerSec,
    applicationBytes = applicationBytes,
    applicationBytesPerSec = applicationBytesPerSec,
    servers = servers.toSqlStringList(),
    createdOn = createdOn?.toEpochMilliseconds(),
    updatedOn = updatedOn?.toEpochMilliseconds(),
)
