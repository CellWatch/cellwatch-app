package com.example.ndt8.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.ndt8.data.model.Measurement

data class MeasurementWithData(
    @Embedded val measurement: MeasurementEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "measurementId"
    )
    val uploadDownloadData: UploadDownloadDataEntity?, // either upload or download data

    @Relation(
        parentColumn = "id",
        entityColumn = "measurementId"
    )
    val latencyData: LatencyDataEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "measurementId"
    )
    val locations: List<LocationEntity>?
)

fun MeasurementWithData.asExternalModel() = Measurement(
    id = measurement.id,
    groupId = measurement.groupId,
    campaignId = measurement.campaignId,
    sessionId = measurement.sessionId,
    deviceId = measurement.deviceId,
    deviceManufacturer = measurement.deviceManufacturer,
    deviceModel = measurement.deviceModel,
    deviceOsName = measurement.deviceOsName,
    deviceOsVersion = measurement.deviceOsVersion,
    appName = measurement.appName,
    provider = measurement.provider,
    type = measurement.type,
    timestamp = measurement.timestamp,
    duration = measurement.duration,
    scheduled = measurement.scheduled,
    success = measurement.success,
    carrierAggregation = measurement.carrierAggregation,
    networkConnected = measurement.networkConnected,
    networkAvailable = measurement.networkAvailable,
    networkRoaming = measurement.networkRoaming,
    extraData = measurement.extraData,
    createdOn = measurement.createdOn,
    updatedOn = measurement.updatedOn,
    uploadDownloadData = uploadDownloadData?.asExternalModel(),
    latencyData = latencyData?.asExternalModel(),
    locations = locations?.map(LocationEntity::asExternalModel)
)