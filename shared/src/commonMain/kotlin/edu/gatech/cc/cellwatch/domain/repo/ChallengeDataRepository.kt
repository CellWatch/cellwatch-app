package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.ChallengeData
import kotlinx.coroutines.flow.Flow

interface ChallengeDataRepository {
    suspend fun upsert(challengeData: ChallengeData)
    suspend fun delete(id: String)
    suspend fun getById(id: String): ChallengeData?
    suspend fun getAll(): List<ChallengeData>
    fun observeAll(): Flow<List<ChallengeData>>
}
