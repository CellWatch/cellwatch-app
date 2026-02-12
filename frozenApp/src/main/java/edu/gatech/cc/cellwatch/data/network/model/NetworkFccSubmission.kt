package edu.gatech.cc.cellwatch.data.network.model

import edu.gatech.cc.cellwatch.data.model.FccSubmission
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkFccSubmission(
    // aliases to test_id when submitting to FCC
    var id: String = UUID.randomUUID().toString(),

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
    val sourcePort: Int? = null,

    @SerialName("device_id")
    val deviceId: String? = null,

    @SerialName("device_imei")
    val deviceImei: String? = null,

    @SerialName("device_tac")
    val deviceTac: String? = null,

    @SerialName("device_type")
    val deviceType: String? = null,

    @SerialName("device_manufacturer")
    val deviceManufacturer: String? = null,

    @SerialName("device_model")
    val deviceModel: String? = null,

    @SerialName("device_os")
    val deviceOsName: String? = null,

    @SerialName("app_name")
    val appName: String? = null,

    @SerialName("app_version")
    val appVersion: String? = null,

    @SerialName("provider")
    val provider: String? = null,

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

    val submitted: Boolean? = false,

    @SerialName("submitted_on")
    val submittedOn: Instant? = null,

    val submission: String? = null,

    @SerialName("submission_response")
    val submissionResponse: String? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null
)

fun NetworkFccSubmission.asExternalModel() = FccSubmission(
    id,
    challengeDataId,
    contactName,
    contactEmail,
    contactPhone,
    deviceTimestamp,
    serverTimestamp,
    sourceIp,
    sourcePort,
    deviceId,
    deviceImei,
    deviceTac,
    deviceType,
    deviceManufacturer,
    deviceModel,
    deviceOsName,
    appName,
    appVersion,
    provider,
    simCountryCode,
    simNetworkCode,
    netCountryCode,
    netNetworkCode,
    inVehicle,
    externalAntenna,
    submitted,
    submittedOn,
    submission,
    submissionResponse,
    createdOn,
    updatedOn
)
