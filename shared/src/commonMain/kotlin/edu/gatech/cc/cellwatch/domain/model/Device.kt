package edu.gatech.cc.cellwatch.domain.model

import com.benasher44.uuid.uuid4

data class Device(
    var deviceId: String = uuid4().toString(),
    // other device metadata
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceOsName: String? = null,
    val deviceOsVersion: String? = null,
    val appName: String? = null
)
