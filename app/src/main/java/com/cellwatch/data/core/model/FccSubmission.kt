package com.cellwatch.data.core.model

import com.cellwatch.data.local.model.FccSubmissionEntity
import com.cellwatch.data.network.model.NetworkFccSubmission

fun NetworkFccSubmission.asEntity() = FccSubmissionEntity(
    id,
    groupId,
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
    createdOn,
    updatedOn
)

fun FccSubmissionEntity.asNetworkModel() = NetworkFccSubmission(
    id,
    groupId,
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
    createdOn,
    updatedOn
)