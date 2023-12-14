package edu.gatech.cc.cellwatch.data.core.model

import edu.gatech.cc.cellwatch.data.local.model.MeasurementEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement

fun NetworkMeasurement.asEntity() = MeasurementEntity(
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
    extraData,
    createdOn,
    updatedOn
)

fun MeasurementEntity.asNetworkModel() = NetworkMeasurement(
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
    extraData,
    createdOn,
    updatedOn
)
