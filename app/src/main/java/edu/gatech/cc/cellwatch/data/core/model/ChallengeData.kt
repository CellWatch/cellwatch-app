package edu.gatech.cc.cellwatch.data.core.model

import edu.gatech.cc.cellwatch.data.local.model.ChallengeDataEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkChallengeData

fun NetworkChallengeData.asEntity() = ChallengeDataEntity(
    id, submissionCategory, contactName, contactEmail, contactPhone, dataSharingAcknowledgement, createdOn, updatedOn
)

fun ChallengeDataEntity.asNetworkModel() = NetworkChallengeData(
    id, submissionCategory, contactName, contactEmail, contactPhone, dataSharingAcknowledgement, createdOn, updatedOn
)
