package com.cellwatch.data.model

import com.cellwatch.data.local.model.ChallengeDataEntity
import com.cellwatch.data.network.model.NetworkChallengeData
import kotlinx.datetime.Instant
import java.util.UUID

data class ChallengeData(
    var id: String = UUID.randomUUID().toString(),

    val submissionCategory: String,

    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val dataSharingAcknowledgement: Boolean? = null,

    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)

fun ChallengeData.asEntity() = ChallengeDataEntity(
    id, submissionCategory, contactName, contactEmail, contactPhone, dataSharingAcknowledgement, createdOn, updatedOn
)

fun ChallengeData.asNetworkModel() = NetworkChallengeData(
    id, submissionCategory, contactName, contactEmail, contactPhone, dataSharingAcknowledgement, createdOn, updatedOn
)