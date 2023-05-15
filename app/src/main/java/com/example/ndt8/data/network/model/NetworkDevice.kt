package com.example.ndt8.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkDevice(
    @SerialName("device_id")
    var deviceId: String = UUID.randomUUID().toString(),
    // other device metadata

    @SerialName("device_manufacturer")
    val deviceManufacturer: String? = null,

    @SerialName("device_model")
    val deviceModel: String? = null,

    @SerialName("device_os_name")
    val deviceOsName: String? = null,

    @SerialName("device_os_version")
    val deviceOsVersion: String? = null,

    @SerialName("app_name")
    val appName: String? = null
)
