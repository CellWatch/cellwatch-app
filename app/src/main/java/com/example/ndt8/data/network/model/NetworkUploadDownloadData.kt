package com.example.ndt8.data.network.model

import com.example.ndt8.data.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkUploadDownloadData(
    var id: String = UUID.randomUUID().toString(),
    
    @SerialName("measurement_id")
    var measurementId: String? = null, // UUID

    @SerialName("warmup_duration")
    val warmupDuration: Long? = null,

    @SerialName("warmup_bytes")
    val warmupBytes: Long? = null,

    val duration: Long? = null,

    val bytes: Long? = null,

    val servers: List<String>? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null
)

fun NetworkUploadDownloadData.asExternalModel() = UploadDownloadData(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes, servers, createdOn, updatedOn
)