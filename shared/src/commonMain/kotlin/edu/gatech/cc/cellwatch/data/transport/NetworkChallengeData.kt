package edu.gatech.cc.cellwatch.data.transport

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class NetworkChallengeData(
    val id: String,
    val submissionCategory: String,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val dataSharingAcknowledgement: Boolean? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)