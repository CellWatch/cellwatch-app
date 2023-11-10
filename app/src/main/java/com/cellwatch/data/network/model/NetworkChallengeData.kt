package com.cellwatch.data.network.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import java.util.UUID

data class NetworkChallengeData(
    var id: String = UUID.randomUUID().toString(),

    @SerialName("submission_category")
    val submissionCategory: String,

    @SerialName("contact_name")
    val contactName: String? = null,

    @SerialName("contact_email")
    val contactEmail: String? = null,

    @SerialName("contact_phone")
    val contactPhone: String? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null
)
