package edu.gatech.cc.cellwatch.domain.fcc

import kotlinx.datetime.Clock

data class MsakMeasurementExecutorConfig(
    val userAgent: String,
    val throughputStreams: Int = 2,
    val throughputDurationMs: Long = 5_000,
    val throughputDelayMs: Long = 0,
    val latencyDurationMs: Long = 3_000,
)

expect object MsakMeasurementExecutorPlatform {
    fun create(
        config: MsakMeasurementExecutorConfig,
        clock: Clock = Clock.System,
    ): MeasurementExecutor
}
