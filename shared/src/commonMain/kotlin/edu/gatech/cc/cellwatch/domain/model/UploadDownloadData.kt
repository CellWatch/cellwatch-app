package edu.gatech.cc.cellwatch.domain.model

import com.benasher44.uuid.uuid4
import kotlinx.datetime.Instant

data class UploadDownloadData(
    var id: String = uuid4().toString(),
    var measurementId: String? = null,
    val warmupDuration: Long? = null,
    val warmupBytes: Long? = null,
    val duration: Long? = null,
    val bytes: Long? = null,
    val bytesPerSec: Double? = null,
    val applicationBytes: Long? = null,
    val applicationBytesPerSec: Double? = null,
    val servers: List<String>? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
)
