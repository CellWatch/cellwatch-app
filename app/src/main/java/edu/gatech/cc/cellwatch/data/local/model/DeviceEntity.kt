package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import edu.gatech.cc.cellwatch.data.model.Device
import java.util.UUID

@Entity
data class DeviceEntity(
    @PrimaryKey
    var deviceId: String = UUID.randomUUID().toString(),

    // other device metadata
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceOsName: String? = null,
    val deviceOsVersion: String? = null,
    val appName: String? = null
)

fun DeviceEntity.asExternalModel() = Device(
    deviceId, deviceManufacturer, deviceModel, deviceOsName, deviceOsVersion, appName
)
