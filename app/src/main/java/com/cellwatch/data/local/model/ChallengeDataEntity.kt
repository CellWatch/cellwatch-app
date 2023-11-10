package com.cellwatch.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cellwatch.data.model.ChallengeData
import kotlinx.datetime.Instant
import java.util.UUID

@Entity
data class ChallengeDataEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

    val submissionCategory: String,

    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,

    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)

fun ChallengeDataEntity.asExternalModel() = ChallengeData(
    id, submissionCategory, contactName, contactEmail, contactPhone, createdOn, updatedOn
)