package com.cellwatch.data.model

import com.cellwatch.data.local.model.FccSubmissionEntity
import kotlinx.datetime.Instant
import java.util.UUID

data class FccSubmission(
    var id: String = UUID.randomUUID().toString(),
    val groupId: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val deviceTimestamp: Instant? = null,
    val serverTimestamp: Instant? = null,
    val sourceIp: String? = null,
    val sourcePort: String? = null,
    val deviceImei: String? = null,
    val deviceTac: String? = null,
    val simCountryCode: String? = null,
    val simNetworkCode: String? = null,
    val netCountryCode: String? = null,
    val netNetworkCode: String? = null,
    val inVehicle: Boolean? = null,
    val externalAntenna: Boolean? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null,
    var latencyData: LatencyData? = null,
    var locations: List<Location>? = null,)

fun FccSubmission.asEntity() = FccSubmissionEntity(
    id,
    groupId,
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