package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.domain.model.Device
import edu.gatech.cc.cellwatch.db.DeviceEntity

fun DeviceEntity.toDomain(): Device =
    Device(
        deviceId = deviceId,
        deviceManufacturer = deviceManufacturer ?: "",
        deviceModel = deviceModel ?: "",
        deviceOsName = deviceOsName ?: "",
        deviceOsVersion = deviceOsVersion ?: "",
        appName = appName ?: ""
    )

fun Device.toRow(): DeviceEntity =
    DeviceEntity(
        deviceId = deviceId,
        deviceManufacturer = deviceManufacturer,
        deviceModel = deviceModel,
        deviceOsName = deviceOsName,
        deviceOsVersion = deviceOsVersion,
        appName = appName
    )