package edu.gatech.cc.cellwatch.data.model

import edu.gatech.cc.cellwatch.data.local.model.UploadDownloadDataEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkUploadDownloadData
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * External data layer representation of an upload/download data record
 */
@Serializable
data class UploadDownloadData(
    var id: String = UUID.randomUUID().toString(),
    var measurementId: String? = null, // UUID
    val warmupDuration: Long? = null,
    val warmupBytes: Long? = null,
    val duration: Long? = null,
    val bytes: Long? = null,
    val bytesPerSec: Double? = null,
    val applicationBytes: Long? = null,
    val applicationBytesPerSec: Double? = null,
    val servers: List<String>? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)

fun UploadDownloadData.asEntity() = UploadDownloadDataEntity(
    id,
    measurementId,
    warmupDuration,
    warmupBytes,
    duration,
    bytes,
    bytesPerSec,
    applicationBytes,
    applicationBytesPerSec,
    servers,
    createdOn,
    updatedOn,
)

fun UploadDownloadData.asNetworkModel() = NetworkUploadDownloadData(
    id,
    measurementId,
    warmupDuration,
    warmupBytes,
    duration,
    bytes,
    bytesPerSec,
    applicationBytes,
    applicationBytesPerSec,
    servers,
    createdOn,
    updatedOn
)
