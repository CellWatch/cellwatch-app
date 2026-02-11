package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface FccSubmissionRepository {
    suspend fun upsert(submission: FccSubmission)
    suspend fun delete(id: String)
    suspend fun getById(id: String): FccSubmission?
    suspend fun getUnsubmitted(): List<FccSubmission>
    suspend fun getUnsynced(): List<FccSubmission>
    suspend fun markUploaded(id: String, uploadedAt: Instant)
    fun observeUnsubmitted(): Flow<List<FccSubmission>>
}
