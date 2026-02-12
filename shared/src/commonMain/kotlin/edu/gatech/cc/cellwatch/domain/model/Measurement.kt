package edu.gatech.cc.cellwatch.domain.model

import com.benasher44.uuid.uuid4
import kotlinx.datetime.Instant

data class Measurement(
    var id: String = uuid4().toString(),
    val groupId: String? = null,
    val campaignId: String? = null,
    val sessionId: String? = null,
    val deviceId: String? = null,
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceOsName: String? = null,
    val deviceOsVersion: String? = null,
    val appName: String? = null,
    val provider: String? = null,
    val type: String,
    val timestamp: Instant? = null,
    val duration: Long? = null,
    val scheduled: Boolean? = null,
    val success: Boolean? = null,
    val carrierAggregation: Boolean? = null,
    val networkConnected: Boolean? = null,
    val networkAvailable: Boolean? = null,
    val networkRoaming: Boolean? = null,
    val simMcc: String? = null,
    val simMnc: String? = null,
    val netMcc: String? = null,
    val netMnc: String? = null,
    val telephonySupport: String? = null,
    val networkSupport: String? = null,
    val locationSupport: String? = null,
    val deviceSupport: String? = null,
    val capabilityNotes: String? = null,
    val extraData: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
    var uploadDownloadData: UploadDownloadData? = null,
    var latencyData: LatencyData? = null,
    var locations: List<Location>? = null,
    var cells: List<Cell>? = null,
    val connectionType: NetworkConnectionType? = null,
    val cellularDataEnabled: Boolean? = null,
    var uploadTime: Instant? = null,
    val appVersion: String? = null,
) {
    fun centerLatLon(): Pair<Double, Double>? {
        val locs = locations ?: return null
        val start = locs.getOrNull(0) ?: return null
        val end = locs.getOrNull(1) ?: return Pair(start.lat, start.lon)
        return Pair((start.lat + end.lat) / 2, (start.lon + end.lon) / 2)
    }
}
