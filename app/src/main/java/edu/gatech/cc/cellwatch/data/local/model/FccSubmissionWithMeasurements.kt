package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import edu.gatech.cc.cellwatch.data.model.FccSubmission

data class FccSubmissionWithMeasurements (
    @Embedded val fccSubmission: FccSubmissionEntity,

    @Relation(
        entity = MeasurementEntity::class,
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val measurementsWithData: List<MeasurementWithData>?
)

fun FccSubmissionWithMeasurements.asExternalModel() = FccSubmission(
    id = fccSubmission.id,
//    groupId = fccSubmission.groupId,
    challengeDataId = fccSubmission.challengeDataId,
    contactName = fccSubmission.contactName,
    contactEmail = fccSubmission.contactEmail,
    contactPhone = fccSubmission.contactPhone,
    deviceTimestamp = fccSubmission.deviceTimestamp,
    serverTimestamp = fccSubmission.serverTimestamp,
    sourceIp = fccSubmission.sourceIp,
    sourcePort = fccSubmission.sourcePort,
    deviceImei = fccSubmission.deviceImei,
    deviceTac = fccSubmission.deviceTac,
    simCountryCode = fccSubmission.simCountryCode,
    simNetworkCode = fccSubmission.simNetworkCode,
    netCountryCode = fccSubmission.netCountryCode,
    netNetworkCode = fccSubmission.netNetworkCode,
    inVehicle = fccSubmission.inVehicle,
    externalAntenna = fccSubmission.externalAntenna,
    submitted = fccSubmission.submitted,
    submittedOn = fccSubmission.submittedOn,
    submission = fccSubmission.submission,
    createdOn = fccSubmission.createdOn,
    updatedOn = fccSubmission.updatedOn,
    measurements = measurementsWithData?.map(MeasurementWithData::asExternalModel)

)
