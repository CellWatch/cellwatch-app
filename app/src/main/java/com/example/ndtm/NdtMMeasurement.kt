package com.example.ndtm

data class NdtMMeasurement(
    val Origin: String?,
    val AppInfo: AppInfo?,
    val ConnectionInfo: ConnectionInfo? = null,
    // TODO: add BBRInfo and TCPInfo
)

data class AppInfo(
    val NumBytes: Long,
    val ElapsedTime : Long,
)

data class ConnectionInfo(
    val Client: String,
    val Server: String,
    val UUID: String,
    val CC: String,
)