package com.cellwatch.data.network.model

import com.cellwatch.data.model.FccSubmission
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkFccSubmissionWithMeasurements(
    @SerialName("in_fcc_submission")
    val fccSubmission: NetworkFccSubmission,

    @SerialName("in_measurements")
    val measurements: List<NetworkMeasurementWithData>?
)

fun NetworkFccSubmissionWithMeasurements.asExternalModel() = FccSubmission(
    fccSubmission.id,
//    fccSubmission.groupId,
    fccSubmission.challengeDataId,
    fccSubmission.contactName,
    fccSubmission.contactEmail,
    fccSubmission.contactPhone,
    fccSubmission.deviceTimestamp,
    fccSubmission.serverTimestamp,
    fccSubmission.sourceIp,
    fccSubmission.sourcePort,
    fccSubmission.deviceImei,
    fccSubmission.deviceTac,
    fccSubmission.simCountryCode,
    fccSubmission.simNetworkCode,
    fccSubmission.netCountryCode,
    fccSubmission.netNetworkCode,
    fccSubmission.inVehicle,
    fccSubmission.externalAntenna,
    fccSubmission.submitted,
    fccSubmission.submittedOn,
    fccSubmission.submission,
    fccSubmission.createdOn,
    fccSubmission.updatedOn,
    measurements?.map { measurement -> measurement.asExternalModel() }
)