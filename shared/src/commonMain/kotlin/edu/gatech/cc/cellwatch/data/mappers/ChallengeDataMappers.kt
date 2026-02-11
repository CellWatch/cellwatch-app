package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.domain.model.ChallengeData
import edu.gatech.cc.cellwatch.data.transport.NetworkChallengeData
import com.benasher44.uuid.uuidFrom

fun ChallengeData.toNetwork(): NetworkChallengeData =
    NetworkChallengeData(
        id = id.toString(),
        submissionCategory = submissionCategory,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        dataSharingAcknowledgement = dataSharingAcknowledgement,
        createdOn = createdOn,
        updatedOn = updatedOn
    )

fun NetworkChallengeData.toDomain(): ChallengeData =
    ChallengeData(
        id = uuidFrom(id),
        submissionCategory = submissionCategory,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        dataSharingAcknowledgement = dataSharingAcknowledgement,
        createdOn = createdOn,
        updatedOn = updatedOn
    )