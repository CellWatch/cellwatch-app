package com.cellwatch.data.model

import com.cellwatch.data.local.model.FccSubmissionEntity
import com.cellwatch.data.local.model.FccSubmissionWithMeasurements
import com.cellwatch.data.network.model.NetworkFccSubmission
import com.cellwatch.data.network.model.NetworkFccSubmissionWithMeasurements
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import java.util.UUID

data class FccSubmission(
    // aliases to test_id when submitting to FCC
    var id: String = UUID.randomUUID().toString(),
//    val groupId: String? = null,
    val challengeDataId: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val deviceTimestamp: Instant? = null,
    val serverTimestamp: Instant? = null,
    val sourceIp: String? = null,
    val sourcePort: String? = null,
    val deviceImei: String? = null,
    val deviceTac: String? = null,
    var simCountryCode: String? = null,
    var simNetworkCode: String? = null,
    var netCountryCode: String? = null,
    var netNetworkCode: String? = null,
    val inVehicle: Boolean? = null,
    val externalAntenna: Boolean? = null,
    var submitted: Boolean? = false,
    var submittedOn: Instant? = null,
    var submission: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
    var measurements: List<Measurement>? = null
)

fun FccSubmission.asEntity() = FccSubmissionEntity(
    id,
//    groupId,
    challengeDataId,
    contactName,
    contactEmail,
    contactPhone,
    deviceTimestamp,
    serverTimestamp,
    sourceIp,
    sourcePort,
    deviceImei,
    deviceTac,
    simCountryCode,
    simNetworkCode,
    netCountryCode,
    netNetworkCode,
    inVehicle,
    externalAntenna,
    submitted,
    submittedOn,
    submission,
    createdOn,
    updatedOn
)

//fun FccSubmission.asEntityWithMeasurements() = FccSubmissionWithMeasurements(
//    fccSubmission = FccSubmissionEntity(
//        id,
//        groupId,
//        challengeDataId,
//        contactName,
//        contactEmail,
//        contactPhone,
//        deviceTimestamp,
//        serverTimestamp,
//        sourceIp,
//        sourcePort,
//        deviceImei,
//        deviceTac,
//        simCountryCode,
//        simNetworkCode,
//        netCountryCode,
//        netNetworkCode,
//        inVehicle,
//        externalAntenna,
//        submitted,
//        submittedOn,
//        submission,
//        createdOn,
//        updatedOn
//    ),
//    measurements = m
////    measurementsWithData = measurements?.map { measurement -> measurement.asEntityWithData() }
//)

fun FccSubmission.asNetworkModelWithMeasurements() = NetworkFccSubmissionWithMeasurements(
    fccSubmission = NetworkFccSubmission(
        id,
//        groupId,
        challengeDataId,
        contactName,
        contactEmail,
        contactPhone,
        deviceTimestamp,
        serverTimestamp,
        sourceIp,
        sourcePort,
        deviceImei,
        deviceTac,
        simCountryCode,
        simNetworkCode,
        netCountryCode,
        netNetworkCode,
        inVehicle,
        externalAntenna,
        submitted,
        submittedOn,
        submission,
        createdOn,
        updatedOn
    ),
    measurements = measurements?.map { measurement -> measurement.asNetworkModelWithData() }
)

fun FccSubmission.asNetworkModel() = NetworkFccSubmission(
    id,
//    groupId,
    challengeDataId,
    contactName,
    contactEmail,
    contactPhone,
    deviceTimestamp,
    serverTimestamp,
    sourceIp,
    sourcePort,
    deviceImei,
    deviceTac,
    simCountryCode,
    simNetworkCode,
    netCountryCode,
    netNetworkCode,
    inVehicle,
    externalAntenna,
    submitted,
    submittedOn,
    submission,
    createdOn,
    updatedOn
)