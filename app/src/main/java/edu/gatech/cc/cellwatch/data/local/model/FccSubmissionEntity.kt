package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import kotlinx.datetime.Instant
import java.util.UUID

@Entity
data class FccSubmissionEntity(
    // AKA groupId
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

//    @ColumnInfo(index = true)
//    val groupId: String? = null,

    @ColumnInfo(index = true)
    val challengeDataId: String? = null,

    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val deviceTimestamp: Instant? = null,
    var serverTimestamp: Instant? = null,
    var sourceIp: String? = null,
    var sourcePort: String? = null,
    val deviceId: String? = null,
    val deviceImei: String? = null,
    val deviceTac: String? = null,
    val deviceType: String? = null,

    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceOsName: String? = null,
    val appName: String? = null,
    val appVersion: String? = null,
    val provider: String? = null,

    val simCountryCode: String? = null,
    val simNetworkCode: String? = null,
    val netCountryCode: String? = null,
    val netNetworkCode: String? = null,
    val inVehicle: Boolean? = null,
    val externalAntenna: Boolean? = null,
    val submitted: Boolean? = false,
    val submittedOn: Instant? = null,
    val submission: String? = null,

    //    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

    //    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null,

    // When was this record pushed to cloud storage?
    var uploadTime: Instant? = null,
)

fun FccSubmissionEntity.asExternalModel() = FccSubmission(
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
    externalAntenna
)
