package edu.gatech.cc.cellwatch.data.transport

import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkUploadDownloadData(
    var id: String = uuid4().toString(),
    @SerialName("measurement_id")
    var measurementId: String? = null,
    @SerialName("warmup_duration")
    val warmupDuration: Long? = null,
    @SerialName("warmup_bytes")
    val warmupBytes: Long? = null,
    val duration: Long? = null,
    val bytes: Long? = null,
    @SerialName("bytes_per_sec")
    val bytesPerSec: Double? = null,
    @SerialName("application_bytes")
    val applicationBytes: Long? = null,
    @SerialName("application_bytes_per_sec")
    val applicationBytesPerSec: Double? = null,
    val servers: List<String>? = null,
    @SerialName("created_on")
    val createdOn: Instant? = null,
    @SerialName("updated_on")
    val updatedOn: Instant? = null,
)

fun UploadDownloadData.toNetwork(): NetworkUploadDownloadData = NetworkUploadDownloadData(
    id = id,
    measurementId = measurementId,
    warmupDuration = warmupDuration,
    warmupBytes = warmupBytes,
    duration = duration,
    bytes = bytes,
    bytesPerSec = bytesPerSec,
    applicationBytes = applicationBytes,
    applicationBytesPerSec = applicationBytesPerSec,
    servers = servers,
    createdOn = createdOn,
    updatedOn = updatedOn,
)

fun NetworkUploadDownloadData.toDomain(): UploadDownloadData = UploadDownloadData(
    id = id,
    measurementId = measurementId,
    warmupDuration = warmupDuration,
    warmupBytes = warmupBytes,
    duration = duration,
    bytes = bytes,
    bytesPerSec = bytesPerSec,
    applicationBytes = applicationBytes,
    applicationBytesPerSec = applicationBytesPerSec,
    servers = servers,
    createdOn = createdOn,
    updatedOn = updatedOn,
)
