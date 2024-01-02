package edu.gatech.cc.cellwatch.data.core.model

import edu.gatech.cc.cellwatch.data.local.model.DeviceEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkDevice

fun NetworkDevice.asEntity() = DeviceEntity(
    deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName
)

fun DeviceEntity.asNetworkModel() = NetworkDevice(
    deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName
)
