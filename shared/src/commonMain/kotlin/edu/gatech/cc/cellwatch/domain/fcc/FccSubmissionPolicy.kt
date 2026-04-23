package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.datetime.Instant

data class FccSubmissionProfile(
    val appName: String? = null,
    val appVersion: String? = null,
    val deviceId: String? = null,
    val provider: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
)

data class FccSubmissionMetadataSnapshot(
    val deviceId: String?,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceOsName: String?,
    val deviceOsVersion: String?,
    val appName: String?,
    val appVersion: String?,
    val provider: String?,
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
    val submissionProfile: FccSubmissionProfile? = null,
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
            deviceOsName = ordered.firstNotNullOfOrNull { it.deviceOsName },
            deviceOsVersion = ordered.firstNotNullOfOrNull { it.deviceOsVersion },
            appName = ordered.firstNotNullOfOrNull { it.appName },
            appVersion = ordered.firstNotNullOfOrNull { it.appVersion },
            provider = ordered.firstNotNullOfOrNull { it.provider },
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
        val profile = context.submissionProfile
        return FccSubmission(
            id = context.groupId,
            deviceId = metadata.deviceId ?: profile?.deviceId,
            deviceTimestamp = context.deviceTimestamp,
            inVehicle = context.inVehicle,
            externalAntenna = context.externalAntenna,
            deviceType = context.deviceType,
            deviceManufacturer = metadata.deviceManufacturer,
            deviceModel = metadata.deviceModel,
            deviceOsName = context.deviceOsName,
            appName = metadata.appName ?: profile?.appName,
            appVersion = metadata.appVersion ?: profile?.appVersion,
            provider = metadata.provider ?: profile?.provider,
            simCountryCode = metadata.simMcc,
            simNetworkCode = metadata.simMnc,
            netCountryCode = metadata.netMcc,
            netNetworkCode = metadata.netMnc,
            contactName = profile?.contactName,
            contactEmail = profile?.contactEmail,
            contactPhone = profile?.contactPhone,
        )
    }
}
