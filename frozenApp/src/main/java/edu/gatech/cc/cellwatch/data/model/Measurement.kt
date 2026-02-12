package edu.gatech.cc.cellwatch.data.model

import android.content.Context
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.local.model.MeasurementEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementWithData
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurementWithData
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

/**
 * External data layer representation of a measurement
 */
@Serializable
data class Measurement(
    var id: String = UUID.randomUUID().toString(),
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
    val extraData: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
    var uploadDownloadData: UploadDownloadData? = null,
    var latencyData: LatencyData? = null,
    var locations: List<Location>? = null,
    var cells: List<Cell>? = null,
    val connectionType: NetworkConnectionType?,
    val cellularDataEnabled: Boolean?,
    // When was this record pushed to cloud storage?
    var uploadTime: Instant? = null,
    val appVersion: String? = null,
) {
    fun centerLatLon(): Pair<Double, Double>? {
        val locs = locations ?: return null
        val start = locs.getOrNull(0) ?: return null
        val end = locs.getOrNull(1) ?: return Pair(start.lat, start.lon)
        return Pair((start.lat + end.lat) / 2, (start.lon + end.lon) / 2)
    }

    fun displayValue(context: Context): CharSequence {
        return if (success == true) {
            if (type == "latency") {
                latencyData?.let {
                    context.getString(R.string.latency_ms, ((it.rtt ?: 0) / 1e3).roundToInt())
                } ?: throw RuntimeException("missing latency data on measurement $this")
            } else {
                uploadDownloadData?.let {
                    val bytesPerSec = it.bytesPerSec ?: if (it.bytes != null && it.duration != null && it.duration > 0) {
                        it.bytes.toDouble() / (it.duration.toDouble() / 1e6)
                    } else {
                        0.0
                    }

                    context.getString(R.string.speed_mbps, (bytesPerSec * 8 / 1e6).roundToInt())
                } ?: throw RuntimeException("missing upload/download data on measurement $this")
            }
        } else {
            context.getString(R.string.failed)
        }
    }
}

fun Measurement.asEntity() = MeasurementEntity(
    id,
    groupId,
    campaignId,
    sessionId,
    deviceId,
    deviceManufacturer,
    deviceModel,
    deviceOsName,
    deviceOsVersion,
    appName,
    provider,
    type,
    timestamp,
    duration,
    scheduled,
    success,
    carrierAggregation,
    networkConnected,
    networkAvailable,
    networkRoaming,
    simMcc,
    simMnc,
    netMcc,
    netMnc,
    connectionType,
    cellularDataEnabled,
    extraData,
    createdOn,
    updatedOn,
    uploadTime,
    appVersion,
)

fun Measurement.asEntityWithData() = MeasurementWithData(
    measurement = MeasurementEntity(
        id,
        groupId,
        campaignId,
        sessionId,
        deviceId,
        deviceManufacturer,
        deviceModel,
        deviceOsName,
        deviceOsVersion,
        appName,
        provider,
        type,
        timestamp,
        duration,
        scheduled,
        success,
        carrierAggregation,
        networkConnected,
        networkAvailable,
        networkRoaming,
        simMcc,
        simMnc,
        netMcc,
        netMnc,
        connectionType,
        cellularDataEnabled,
        extraData,
        createdOn,
        updatedOn,
        uploadTime,
        appVersion,
    ),
    uploadDownloadData = uploadDownloadData?.asEntity(),
    latencyData = latencyData?.asEntity(),
    locations = locations?.map { it.asEntity() },
    cells = cells?.map { it.asEntity() }
)

fun Measurement.asNetworkModel() = NetworkMeasurement(
    id,
    groupId,
    campaignId,
    sessionId,
    deviceId,
    deviceManufacturer,
    deviceModel,
    deviceOsName,
    deviceOsVersion,
    appName,
    provider,
    type,
    timestamp,
    duration,
    scheduled,
    success,
    carrierAggregation,
    networkConnected,
    networkAvailable,
    networkRoaming,
    simMcc,
    simMnc,
    netMcc,
    netMnc,
    connectionType,
    cellularDataEnabled,
    extraData,
    createdOn,
    updatedOn,
    appVersion = appVersion,
)

fun Measurement.asNetworkModelWithData() = NetworkMeasurementWithData(
    measurement = NetworkMeasurement(
        id,
        groupId,
        campaignId,
        sessionId,
        deviceId,
        deviceManufacturer,
        deviceModel,
        deviceOsName,
        deviceOsVersion,
        appName,
        provider,
        type,
        timestamp,
        duration,
        scheduled,
        success,
        carrierAggregation,
        networkConnected,
        networkAvailable,
        networkRoaming,
        simMcc,
        simMnc,
        netMcc,
        netMnc,
        connectionType,
        cellularDataEnabled,
        extraData,
        createdOn,
        updatedOn,
        appVersion = appVersion,
    ),
    measurementData = uploadDownloadData?.asNetworkModel(),
    latencyData = latencyData?.asNetworkModel(),
    locations = locations?.map { location -> location.asNetworkModel() },
    cells = cells?.map { cell -> cell.asNetworkModel() }
)