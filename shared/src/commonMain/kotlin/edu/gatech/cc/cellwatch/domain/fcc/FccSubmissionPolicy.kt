package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.datetime.Instant

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

data class FccSubmissionBuildContext(
    val groupId: String,
    val deviceTimestamp: Instant,
    val inVehicle: Boolean,
    val externalAntenna: Boolean = false,
    val deviceType: String,
    val deviceOsName: String?,
    val appVersion: String?,
    val provider: String?,
    val contactName: String?,
    val contactEmail: String?,
    val contactPhone: String?,
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

    fun buildSubmission(
        context: FccSubmissionBuildContext,
        metadata: FccSubmissionMetadataSnapshot,
    ): FccSubmission {
        return FccSubmission(
            id = context.groupId,
            deviceId = metadata.deviceId,
            deviceTimestamp = context.deviceTimestamp,
            inVehicle = context.inVehicle,
            externalAntenna = context.externalAntenna,
            deviceType = context.deviceType,
            deviceManufacturer = metadata.deviceManufacturer,
            deviceModel = metadata.deviceModel,
            deviceOsName = context.deviceOsName,
            appName = metadata.appName,
            appVersion = context.appVersion,
            provider = context.provider,
            simCountryCode = metadata.simMcc,
            simNetworkCode = metadata.simMnc,
            netCountryCode = metadata.netMcc,
            netNetworkCode = metadata.netMnc,
            contactName = context.contactName,
            contactEmail = context.contactEmail,
            contactPhone = context.contactPhone,
        )
    }
}
