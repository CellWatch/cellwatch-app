package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import edu.gatech.cc.cellwatch.data.model.Measurement

data class MeasurementWithData(
    @Embedded val measurement: MeasurementEntity,

    @Relation(
        entity = UploadDownloadDataEntity::class,
        parentColumn = "id",
        entityColumn = "measurementId"
    )
    val uploadDownloadData: UploadDownloadDataEntity?, // either upload or download data

    @Relation(
        entity = LatencyDataEntity::class,
        parentColumn = "id",
        entityColumn = "measurementId"
    )
    val latencyData: LatencyDataEntity?,

    @Relation(
        entity = LocationEntity::class,
        parentColumn = "id",
        entityColumn = "measurementId"
    )
    val locations: List<LocationEntity>?,

    @Relation(
        entity = CellEntity::class,
        parentColumn = "id",
        entityColumn = "measurementId"
    )
    val cells: List<CellEntity>?
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
    simMcc = measurement.simMcc,
    simMnc = measurement.simMnc,
    netMcc = measurement.netMcc,
    netMnc= measurement.netMnc,
    extraData = measurement.extraData,
    createdOn = measurement.createdOn,
    updatedOn = measurement.updatedOn,
    uploadDownloadData = uploadDownloadData?.asExternalModel(),
    latencyData = latencyData?.asExternalModel(),
    cells = cells?.map(CellEntity::asExternalModel),
    locations = locations?.map(LocationEntity::asExternalModel),
    connectionType = measurement.connectionType,
    cellularDataEnabled = measurement.cellularDataEnabled,
    uploadTime = measurement.uploadTime,
)
