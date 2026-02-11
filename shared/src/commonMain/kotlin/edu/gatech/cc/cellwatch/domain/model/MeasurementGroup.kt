package edu.gatech.cc.cellwatch.domain.model

data class MeasurementGroup(
    val latency: Measurement?,
    val download: Measurement?,
    val upload: Measurement?,
    val submission: FccSubmission?,
    val id: String = latency?.groupId ?: download?.groupId ?: upload?.groupId ?: submission?.id
        ?: error("cannot create measurement group without id"),
) {
    init {
        if (latency != null) {
            if (latency.groupId != id) {
                error("mismatched latency group id ${latency.groupId} != $id")
            }
            if (latency.type != "latency") {
                error("expected measurement of type latency, got ${latency.type}")
            }
            if (latency.latencyData == null) {
                error("missing latency data on measurement $latency")
            }
        }

        if (download != null) {
            if (download.groupId != id) {
                error("mismatched download group id ${download.groupId} != $id")
            }
            if (download.type != "download") {
                error("expected measurement of type download, got ${download.type}")
            }
            if (download.uploadDownloadData == null) {
                error("missing download data on measurement $download")
            }
        }

        if (upload != null) {
            if (upload.groupId != id) {
                error("mismatched upload group id ${upload.groupId} != $id")
            }
            if (upload.type != "upload") {
                error("expected measurement of type upload, got ${upload.type}")
            }
            if (upload.uploadDownloadData == null) {
                error("missing upload data on measurement $upload")
            }
        }

        if (submission != null && submission.id != id) {
            error("mismatched submission group id ${submission.id} != $id")
        }
    }

    fun centerLatLon(): Pair<Double, Double>? {
        val latlons = listOfNotNull(latency?.centerLatLon(), download?.centerLatLon(), upload?.centerLatLon())
        if (latlons.size < 2) return latlons.firstOrNull()
        return Pair(latlons.map { it.first }.average(), latlons.map { it.second }.average())
    }
}
