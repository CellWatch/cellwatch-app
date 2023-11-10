package com.cellwatch.data.network.model

import com.cellwatch.data.model.FccSubmission
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import java.util.UUID

data class NetworkFccSubmission(
    var id: String = UUID.randomUUID().toString(),

    @SerialName("group_id")
    val groupId: String? = null,

    @SerialName("challenge_data_id")
    val challengeDataId: String? = null,

    @SerialName("contact_name")
    val contactName: String? = null,

    @SerialName("contact_email")
    val contactEmail: String? = null,

    @SerialName("contact_phone")
    val contactPhone: String? = null,

    @SerialName("device_timestamp")
    val deviceTimestamp: Instant? = null,

    @SerialName("server_timestamp")
    val serverTimestamp: Instant? = null,

    @SerialName("source_ip")
    val sourceIp: String? = null,

    @SerialName("source_port")
    val sourcePort: String? = null,

    @SerialName("device_imei")
    val deviceImei: String? = null,

    @SerialName("device_tac")
    val deviceTac: String? = null,

    @SerialName("sim_country_code")
    val simCountryCode: String? = null,

    @SerialName("sim_network_code")
    val simNetworkCode: String? = null,

    @SerialName("net_country_code")
    val netCountryCode: String? = null,

    @SerialName("net_network_code")
    val netNetworkCode: String? = null,

    @SerialName("in_vehicle")
    val inVehicle: Boolean? = null,

    @SerialName("external_antenna")
    val externalAntenna: Boolean? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null
)

fun NetworkFccSubmission.asExternalModel() = FccSubmission(
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