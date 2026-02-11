package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import kotlinx.coroutines.flow.Flow

interface FccSubmissionRepository {
    suspend fun upsert(submission: FccSubmission)
    suspend fun delete(id: String)
    suspend fun getById(id: String): FccSubmission?
    suspend fun getUnsubmitted(): List<FccSubmission>
    fun observeUnsubmitted(): Flow<List<FccSubmission>>
}
