package com.example.ndt8.domain.ndt8.model

import kotlinx.serialization.Serializable

@Serializable
data class Ndt8TestMetrics(
    val bytesPerSec: Double,
    val bytes: Long,
    val usecs: Long,
) {
    override fun toString(): String {
        return "${"%.2f".format(8 * bytesPerSec / 1e6)} Mbps, ${bytes} bytes, ${usecs / 1e6} secs"
    }
}