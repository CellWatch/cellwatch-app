package com.cellwatch.data.core.model

import com.cellwatch.data.local.model.DeviceEntity
import com.cellwatch.data.network.model.NetworkDevice

fun NetworkDevice.asEntity() = DeviceEntity(
    deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName
)

fun DeviceEntity.asNetworkModel() = NetworkDevice(
    deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName
)