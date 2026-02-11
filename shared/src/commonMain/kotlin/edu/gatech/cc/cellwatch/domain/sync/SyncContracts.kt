package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.datetime.Instant

interface MeasurementSyncLocalStore {
    suspend fun getUnsyncedMeasurements(): List<Measurement>
    suspend fun markMeasurementUploaded(id: String, uploadedAt: Instant)
    suspend fun getUnsyncedSubmissions(): List<FccSubmission>
    suspend fun markSubmissionUploaded(id: String, uploadedAt: Instant)
}

interface MeasurementSyncRemoteDataSource {
    suspend fun insertMeasurement(measurement: Measurement): Measurement

    suspend fun getMeasurementById(id: String): Measurement

    suspend fun insertFccSubmission(submission: FccSubmission): FccSubmission
}

interface TcpTupleProvider {
    suspend fun getPublicTcpTuple(): TcpTuple
}

class NetworkError(cause: Throwable? = null) : Exception(cause)
class DuplicateKeyError(cause: Throwable? = null) : Exception(cause)
class NotFoundError(cause: Throwable? = null) : Exception(cause)
