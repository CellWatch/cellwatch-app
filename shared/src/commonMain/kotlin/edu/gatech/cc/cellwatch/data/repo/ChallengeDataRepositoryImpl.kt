package edu.gatech.cc.cellwatch.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import edu.gatech.cc.cellwatch.db.ChallengeDataQueries
import edu.gatech.cc.cellwatch.domain.model.ChallengeData
import edu.gatech.cc.cellwatch.domain.repo.ChallengeDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

class ChallengeDataRepositoryImpl(
    private val queries: ChallengeDataQueries,
    private val io: CoroutineContext,
) : ChallengeDataRepository {

    override suspend fun upsert(challengeData: ChallengeData) {
        val row = challengeData.toRow()
        queries.insertOrReplaceChallengeData(
            id = row.id,
            submissionCategory = row.submissionCategory,
            contactName = row.contactName,
            contactEmail = row.contactEmail,
            contactPhone = row.contactPhone,
            dataSharingAcknowledgement = row.dataSharingAcknowledgement,
            createdOn = row.createdOn,
            updatedOn = row.updatedOn,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteChallengeDataById(id)
    }

    override suspend fun getById(id: String): ChallengeData? =
        queries.selectChallengeDataById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun getAll(): List<ChallengeData> =
        queries.selectAllChallengeData().executeAsList().map { it.toDomain() }

    override fun observeAll(): Flow<List<ChallengeData>> =
        queries.selectAllChallengeData()
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}
