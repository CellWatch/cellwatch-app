package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.domain.model.ChallengeData
import edu.gatech.cc.cellwatch.data.transport.NetworkChallengeData
import edu.gatech.cc.cellwatch.db.ChallengeDataEntity
import kotlinx.datetime.Instant

fun ChallengeData.toNetwork(): NetworkChallengeData =
    NetworkChallengeData(
        id = id,
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
        id = id,
        submissionCategory = submissionCategory,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        dataSharingAcknowledgement = dataSharingAcknowledgement,
        createdOn = createdOn,
        updatedOn = updatedOn
    )

fun ChallengeDataEntity.toDomain(): ChallengeData =
    ChallengeData(
        id = id,
        submissionCategory = submissionCategory,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        dataSharingAcknowledgement = dataSharingAcknowledgement.toBooleanOrNull(),
        createdOn = createdOn?.let(Instant::fromEpochMilliseconds),
        updatedOn = updatedOn?.let(Instant::fromEpochMilliseconds),
    )

fun ChallengeData.toRow(): ChallengeDataEntity =
    ChallengeDataEntity(
        id = id,
        submissionCategory = submissionCategory,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        dataSharingAcknowledgement = dataSharingAcknowledgement.toSqlBoolean(),
        createdOn = createdOn?.toEpochMilliseconds(),
        updatedOn = updatedOn?.toEpochMilliseconds(),
    )
