package edu.gatech.cc.cellwatch.data.model

import kotlinx.serialization.Serializable
@Serializable
data class MeasurementGroup(
    val latency: Measurement?,
    val download: Measurement?,
    val upload: Measurement?,
    val submission: FccSubmission?,
    val id: String = latency?.groupId ?: download?.groupId ?: upload?.groupId ?: submission?.id
        ?: throw RuntimeException("cannot create measurement group without id"),
) {
    init {
        if (latency != null) {
            if (latency.groupId != id) {
                throw RuntimeException("mismatched latency group id ${latency.groupId} != $id")
            }
            if (latency.type != "latency") {
                throw RuntimeException("expected measurement of type latency, got ${latency.type}")
            }
            if (latency.latencyData == null) {
                throw RuntimeException("missing latency data on measurement $latency")
            }
        }

        if (download != null) {
            if (download.groupId != id) {
                throw RuntimeException("mismatched download group id ${download.groupId} != $id")
            }
            if (download.type != "download") {
                throw RuntimeException("expected measurement of type download, got ${download.type}")
            }
            if (download.uploadDownloadData == null) {
                throw RuntimeException("missing download data on measurement $download")
            }
        }

        if (upload != null) {
            if (upload.groupId != id) {
                throw RuntimeException("mismatched upload group id ${upload.groupId} != $id")
            }
            if (upload.type != "upload") {
                throw RuntimeException("expected measurement of type upload, got ${upload.type}")
            }
            if (upload.uploadDownloadData == null) {
                throw RuntimeException("missing upload data on measurement $upload")
            }
        }

        if (submission != null && submission.id != id) {
            throw RuntimeException("mismatched submission group id ${submission.id} != $id")
        }
    }

    fun centerLatLon(): Pair<Double, Double>? {
        val latlons = listOfNotNull(latency?.centerLatLon(), download?.centerLatLon(), upload?.centerLatLon())
        if (latlons.size < 2) {
            return latlons.firstOrNull()
        }

        return Pair(
            latlons.map { it.first }.average(),
            latlons.map { it.second }.average(),
        )
    }
}
