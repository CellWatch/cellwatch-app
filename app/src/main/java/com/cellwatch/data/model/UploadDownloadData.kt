package com.cellwatch.data.model

import com.cellwatch.data.local.model.UploadDownloadDataEntity
import com.cellwatch.data.network.model.NetworkUploadDownloadData
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * External data layer representation of an upload/download data record
 */
data class UploadDownloadData(
    var id: String = UUID.randomUUID().toString(),
    var measurementId: String? = null, // UUID
    val warmupDuration: Long? = null,
    val warmupBytes: Long? = null,
    val duration: Long? = null,
    val bytes: Long? = null,
    val applicationBytes: Long? = null,
    val servers: List<String>? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)

fun UploadDownloadData.asEntity() = UploadDownloadDataEntity(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes, applicationBytes, servers, createdOn, updatedOn
)

fun UploadDownloadData.asNetworkModel() = NetworkUploadDownloadData(
    id,
    measurementId,
    warmupDuration,
    warmupBytes,
    duration,
    bytes,
    applicationBytes,
    servers,
    createdOn,
    updatedOn
)