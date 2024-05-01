package edu.gatech.cc.cellwatch.data.model

import kotlinx.serialization.Serializable
@Serializable
data class MeasurementGroup(
    val latency: Measurement?,
    val download: Measurement?,
    val upload: Measurement?,
    val submission: FccSubmission?,
) {
    init {
        if (latency != null) {
            if (latency.type != "latency") {
                throw RuntimeException("expected measurement of type latency, got ${latency.type}")
            }
            if (latency.latencyData == null) {
                throw RuntimeException("missing latency data on measurement $latency")
            }
        }

        if (download != null) {
            if (download.type != "download") {
                throw RuntimeException("expected measurement of type download, got ${download.type}")
            }
            if (download.uploadDownloadData == null) {
                throw RuntimeException("missing download data on measurement $download")
            }
        }

        if (upload != null) {
            if (upload.type != "upload") {
                throw RuntimeException("expected measurement of type upload, got ${upload.type}")
            }
            if (upload.uploadDownloadData == null) {
                throw RuntimeException("missing upload data on measurement $upload")
            }
        }
    }
}
