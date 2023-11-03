package com.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cellwatch.data.model.FccSubmission
import kotlinx.datetime.Instant
import java.util.UUID

//@Entity(indices = [Index(value = ["groupId"], unique = true)])
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
    val submitted: Boolean? = false,
    val submittedOn: Instant? = null,
    val submission: String? = null,

    //    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

    //    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null,

    // Has this record been pushed to cloud storage?
    var isSynchronized: Boolean = false
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
    deviceImei,
    deviceTac,
    simCountryCode,
    simNetworkCode,
    netCountryCode,
    netNetworkCode,
    inVehicle,
    externalAntenna
)