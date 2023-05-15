package com.example.ndt8.data.core.model

import com.example.ndt8.data.local.model.MeasurementEntity
import com.example.ndt8.data.network.model.NetworkMeasurement

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
    extraData,
    createdOn,
    updatedOn
)