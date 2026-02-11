package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType

data class FccSubmissionMetadataSnapshot(
    val deviceId: String?,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceOsVersion: String?,
    val appName: String?,
    val simMcc: String?,
    val simMnc: String?,
    val netMcc: String?,
    val netMnc: String?,
)

object FccSubmissionPolicy {
    fun shouldCreateSubmission(
        mode: CollectionMode,
        latencyMeasurement: Measurement,
        downloadMeasurement: Measurement,
        uploadMeasurement: Measurement,
    ): Boolean {
        if (mode != CollectionMode.FCC_CHALLENGE) return false

        return listOf(latencyMeasurement, downloadMeasurement, uploadMeasurement).all { measurement ->
            measurement.connectionType != NetworkConnectionType.WIFI &&
                measurement.cellularDataEnabled != false
        }
    }

    fun metadataSnapshot(
        latencyMeasurement: Measurement,
        downloadMeasurement: Measurement,
        uploadMeasurement: Measurement,
    ): FccSubmissionMetadataSnapshot {
        val ordered = listOf(latencyMeasurement, downloadMeasurement, uploadMeasurement)

        return FccSubmissionMetadataSnapshot(
            deviceId = ordered.firstNotNullOfOrNull { it.deviceId },
            deviceManufacturer = ordered.firstNotNullOfOrNull { it.deviceManufacturer },
            deviceModel = ordered.firstNotNullOfOrNull { it.deviceModel },
            deviceOsVersion = ordered.firstNotNullOfOrNull { it.deviceOsVersion },
            appName = ordered.firstNotNullOfOrNull { it.appName },
            simMcc = ordered.firstNotNullOfOrNull { it.simMcc },
            simMnc = ordered.firstNotNullOfOrNull { it.simMnc },
            netMcc = ordered.firstNotNullOfOrNull { it.netMcc },
            netMnc = ordered.firstNotNullOfOrNull { it.netMnc },
        )
    }
}
