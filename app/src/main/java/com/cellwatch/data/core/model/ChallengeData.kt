package com.cellwatch.data.core.model

import com.cellwatch.data.local.model.ChallengeDataEntity
import com.cellwatch.data.network.model.NetworkChallengeData

fun NetworkChallengeData.asEntity() = ChallengeDataEntity(
    id, submissionCategory, contactName, contactEmail, contactPhone, createdOn, updatedOn
)

fun ChallengeDataEntity.asNetworkModel() = NetworkChallengeData(
    id, submissionCategory, contactName, contactEmail, contactPhone, createdOn, updatedOn
)