package edu.gatech.cc.cellwatch.domain.model

import kotlinx.datetime.Instant
import com.benasher44.uuid.uuid4

data class ChallengeData(
    val id: String = uuid4().toString(),
    val submissionCategory: String,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val dataSharingAcknowledgement: Boolean? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)
