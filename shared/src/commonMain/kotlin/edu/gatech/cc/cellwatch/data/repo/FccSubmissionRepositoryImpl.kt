package edu.gatech.cc.cellwatch.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import edu.gatech.cc.cellwatch.db.FccSubmissionQueries
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

class FccSubmissionRepositoryImpl(
    private val queries: FccSubmissionQueries,
    private val io: CoroutineContext,
) : FccSubmissionRepository {

    override suspend fun upsert(submission: FccSubmission) {
        val row = submission.toRow()
        queries.insertOrReplaceFccSubmission(
            id = row.id,
            challengeDataId = row.challengeDataId,
            contactName = row.contactName,
            contactEmail = row.contactEmail,
            contactPhone = row.contactPhone,
            deviceTimestamp = row.deviceTimestamp,
            serverTimestamp = row.serverTimestamp,
            sourceIp = row.sourceIp,
            sourcePort = row.sourcePort,
            deviceId = row.deviceId,
            deviceImei = row.deviceImei,
            deviceTac = row.deviceTac,
            deviceType = row.deviceType,
            deviceManufacturer = row.deviceManufacturer,
            deviceModel = row.deviceModel,
            deviceOsName = row.deviceOsName,
            appName = row.appName,
            appVersion = row.appVersion,
            provider = row.provider,
            simCountryCode = row.simCountryCode,
            simNetworkCode = row.simNetworkCode,
            netCountryCode = row.netCountryCode,
            netNetworkCode = row.netNetworkCode,
            inVehicle = row.inVehicle,
            externalAntenna = row.externalAntenna,
            submitted = row.submitted,
            submittedOn = row.submittedOn,
            submission = row.submission,
            submissionResponse = row.submissionResponse,
            createdOn = row.createdOn,
            updatedOn = row.updatedOn,
            uploadTime = row.uploadTime,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteFccSubmissionById(id)
    }

    override suspend fun getById(id: String): FccSubmission? =
        queries.selectFccSubmissionById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun getUnsubmitted(): List<FccSubmission> =
        queries.selectUnsubmittedFccSubmissions().executeAsList().map { it.toDomain() }

    override suspend fun getUnsynced(): List<FccSubmission> =
        queries.selectUnsyncedFccSubmissions().executeAsList().map { it.toDomain() }

    override suspend fun markUploaded(id: String, uploadedAt: Instant) {
        queries.markFccSubmissionUploaded(
            uploadTime = uploadedAt.toEpochMilliseconds(),
            id = id,
        )
    }

    override fun observeUnsubmitted(): Flow<List<FccSubmission>> =
        queries.selectUnsubmittedFccSubmissions()
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}
