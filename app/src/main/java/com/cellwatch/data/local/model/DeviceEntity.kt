package com.cellwatch.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cellwatch.data.model.Device
import java.util.UUID

@Entity
data class DeviceEntity(
    @PrimaryKey
    var deviceId: String = UUID.randomUUID().toString(),
    // other device metadata

//    @ColumnInfo(name = "device_manufacturer")
    val deviceManufacturer: String? = null,

//    @ColumnInfo(name = "device_model")
    val deviceModel: String? = null,

//    @ColumnInfo(name = "device_os_name")
    val deviceOsName: String? = null,

//    @ColumnInfo(name = "device_os_version")
    val deviceOsVersion: String? = null,

//    @ColumnInfo(name = "app_name")
    val appName: String? = null
)

fun DeviceEntity.asExternalModel() = Device(
    deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName
)