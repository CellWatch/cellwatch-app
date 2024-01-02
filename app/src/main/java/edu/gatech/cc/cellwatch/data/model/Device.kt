package edu.gatech.cc.cellwatch.data.model

import edu.gatech.cc.cellwatch.data.local.model.DeviceEntity
import java.util.UUID

data class Device(
    var deviceId: String = UUID.randomUUID().toString(),
    // other device metadata
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceOsName: String? = null,
    val deviceOsVersion: String? = null,
    val appName: String? = null
)

fun Device.asEntity() = DeviceEntity(
    deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName
)
