package com.example.ndt8.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity
data class Device(
    @SerialName("device_id")
    @PrimaryKey
    var deviceId: String = UUID.randomUUID().toString(),
    // other device metadata

    @SerialName("device_manufacturer")
    @ColumnInfo(name = "device_manufacturer")
    val deviceManufacturer: String? = null,

    @SerialName("device_model")
    @ColumnInfo(name = "device_model")
    val deviceModel: String? = null,

    @SerialName("device_os_name")
    @ColumnInfo(name = "device_os_name")
    val deviceOsName: String? = null,

    @SerialName("device_os_version")
    @ColumnInfo(name = "device_os_version")
    val deviceOsVersion: String? = null,

    @SerialName("app_name")
    @ColumnInfo(name = "app_name")
    val appName: String? = null
)