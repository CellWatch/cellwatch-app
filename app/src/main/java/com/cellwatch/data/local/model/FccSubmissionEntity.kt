package com.cellwatch.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cellwatch.data.model.FccSubmission
import kotlinx.datetime.Instant
import java.util.UUID

@Entity
data class FccSubmissionEntity(
    @PrimaryKey
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

    //    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

    //    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null,

    // Has this record been pushed to cloud storage?
    var isSynchronized: Boolean = false
)

fun FccSubmissionEntity.asExternalModel() = FccSubmission(
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
    externalAntenna
)