package com.example.ndt8.data.model

import com.example.ndt8.data.local.model.MeasurementEntity
import com.example.ndt8.data.local.model.MeasurementWithData
import com.example.ndt8.data.local.model.UploadDownloadDataEntity
import com.example.ndt8.data.network.model.NetworkMeasurement
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * External data layer representation of a measurement
 */
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
    val extraData: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
    var uploadDownloadData: UploadDownloadData? = null,
    var latencyData: LatencyData? = null,
    var locations: List<Location>? = null
)

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
    extraData,
    createdOn,
    updatedOn
)

fun Measurement.asEntityWithData() = MeasurementWithData(
    measurement = MeasurementEntity(id, groupId, campaignId, sessionId, deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName, provider, type, timestamp, duration, scheduled, success, carrierAggregation, networkConnected, networkAvailable, networkRoaming, extraData, createdOn, updatedOn),
    uploadDownloadData = uploadDownloadData?.asEntity(),
    latencyData = latencyData?.asEntity(),
    locations = locations?.map { it.asEntity() }
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
    extraData,
    createdOn,
    updatedOn
)