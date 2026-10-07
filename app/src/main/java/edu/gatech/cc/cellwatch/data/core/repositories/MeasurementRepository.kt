package edu.gatech.cc.cellwatch.data.core.repositories

import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.core.sync.MeasurementUploader
import edu.gatech.cc.cellwatch.data.core.sync.UploadWorker
import edu.gatech.cc.cellwatch.data.local.dao.FccSubmissionDao
import edu.gatech.cc.cellwatch.data.local.dao.MeasurementDao
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementWithData
import edu.gatech.cc.cellwatch.data.local.model.asExternalModel
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.data.model.TcpTuple
import edu.gatech.cc.cellwatch.data.model.asEntity
import edu.gatech.cc.cellwatch.data.model.asEntityWithData
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Instant
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


/**
 * A MeasurementRepository contains methods to read/write [Measurement] records to the local Room
 * database. MeasurementRepository also contains the uploadPending method which uploads all
 * unsynchronized [Measurement] and [FccSubmission] records from the Room database to the remote
 * Supabase database and marks the local records synchronized by setting the uploadTime field to a
 * timestamp. Callers should use [UploadWorker.enqueue] rather than calling it from a screen.
 */

class MeasurementRepository(
    private val measurementDao: MeasurementDao,
    private val submissionDao: FccSubmissionDao,
    private val networkDataSource: NetworkMeasurementDatasource
) {
    private val TAG = this::class.simpleName

    @WorkerThread
    suspend fun getMeasurementGroups(): List<MeasurementGroup> {
        val measurements = getMeasurementsWithData()
        val submissionList = submissionDao.getFccSubmissions().map(FccSubmissionEntity::asExternalModel)
        val latency = mutableMapOf<String, Measurement>()
        val download = mutableMapOf<String, Measurement>()
        val upload = mutableMapOf<String, Measurement>()

        measurements.forEach {
            if (it.groupId == null) {
                throw RuntimeException("measurement ${it.id} missing group id")
            }

            when (it.type) {
                "latency" -> latency[it.groupId] = it
                "download" -> download[it.groupId] = it
                "upload" -> upload[it.groupId] = it
                else -> throw RuntimeException("unknown measurement type ${it.type}")
            }
        }

        val submissions = mutableMapOf<String, FccSubmission>()
        submissionList.forEach { submissions[it.id] = it }

        val groups = mutableMapOf<String, MeasurementGroup>()
        measurements.forEach {
            if (it.groupId == null) {
                throw RuntimeException("measurement ${it.id} missing group id")
            }

            if (groups[it.groupId] == null) {
                groups[it.groupId] = MeasurementGroup(
                    latency[it.groupId],
                    download[it.groupId],
                    upload[it.groupId],
                    submissions[it.groupId],
                )
            }
        }

        return groups.values.toList()
    }

    @WorkerThread
    suspend fun getMeasurementsWithData(): List<Measurement> =
        measurementDao.getMeasurementsWithData().map(MeasurementWithData::asExternalModel)

    @WorkerThread
    private suspend fun getUnsynchronizedMeasurementsWithData(): List<Measurement> =
        measurementDao.getUnsynchronizedMeasurementsWithData().map(MeasurementWithData::asExternalModel)

    @WorkerThread
    suspend fun insertMeasurement(measurement: Measurement) {
        val measurementWithDataEntity = measurement.asEntityWithData()
        measurementDao.insertMeasurementWithData(measurementWithDataEntity)
    }

    @WorkerThread
    suspend fun getUnsynchronizedFccSubmissions(): List<FccSubmission> =
        submissionDao.getUnsynchronizedFccSubmissions().map(FccSubmissionEntity::asExternalModel)

    @WorkerThread
    suspend fun insertFccSubmission(fccSubmission: FccSubmission) {
        val fccSubmissionEntity = fccSubmission.asEntity()
        submissionDao.insertFccSubmission(fccSubmissionEntity)
    }

    @WorkerThread
    suspend fun getUploadTime(group: MeasurementGroup): Instant? {
        val measurementId = group.latency?.id ?: group.download?.id ?: group.upload?.id
        val submissionId = group.submission?.id

        val measurementTime = measurementId?.let { measurementDao.getMeasurementById(it).uploadTime }
        val submissionTime = submissionId?.let { submissionDao.getFccSubmissionById(it).uploadTime }

        if (measurementId == null) {
            return submissionTime
        }

        if (submissionId == null) {
            return measurementTime
        }

        if (measurementTime == null || submissionTime == null) {
            return null
        }

        return maxOf(measurementTime, submissionTime)
    }

    /**
     * Emits the group's upload time whenever local upload state changes, which is null until the
     * background upload has finished with every record in the group.
     */
    fun uploadTimeFlow(group: MeasurementGroup): Flow<Instant?> =
        combine(measurementDao.getMeasurementsFlow(), submissionDao.getFccSubmissionsFlow()) { _, _ ->
            getUploadTime(group)
        }.distinctUntilChanged()

    @WorkerThread
    suspend fun uploadPending(): MeasurementUploader.Summary = uploader.uploadPending()

    private val uploader = MeasurementUploader(
        local = object : MeasurementUploader.Local {
            override suspend fun unsyncedMeasurements() = getUnsynchronizedMeasurementsWithData()

            override suspend fun markMeasurementUploaded(measurement: Measurement, uploadTime: Instant) {
                val entity = measurement.asEntity()
                entity.uploadTime = uploadTime
                measurementDao.updateMeasurement(entity)
            }

            override suspend fun unsyncedSubmissions() = getUnsynchronizedFccSubmissions()

            override suspend fun measurementsInGroup(groupId: String) =
                measurementDao.getMeasurementsInGroup(groupId).map(MeasurementEntity::asExternalModel)

            override suspend fun markSubmissionUploaded(submission: FccSubmission, uploadTime: Instant) {
                val entity = submission.asEntity()
                entity.uploadTime = uploadTime
                submissionDao.updateFccSubmission(entity)
            }
        },
        remote = object : MeasurementUploader.Remote {
            override suspend fun insertMeasurement(measurement: Measurement) =
                networkDataSource.insertMeasurement(measurement)

            override suspend fun insertFccSubmission(submission: FccSubmission) =
                networkDataSource.insertFccSubmission(submission)

            override suspend fun getTcpTuple() = getPubicTCPTuple()
        },
        log = object : MeasurementUploader.Log {
            override fun info(message: String, throwable: Throwable?) = Log.i(TAG, message, throwable)
            override fun error(message: String, throwable: Throwable?) = Log.e(TAG, message, throwable)
        },
    )

    private suspend fun getPubicTCPTuple(
        serviceUrl: String = BuildConfig.TCP_TUPLE_URL,
    ): TcpTuple = suspendCoroutine { continuation ->
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(serviceUrl)
            .header("User-Agent", CellWatchApp.userAgent)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.i(TAG, "tcp tuple request failure: $call", e)
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (response.code != 200 || body == null) {
                    Log.e(TAG, "tcp tuple request $request failed: $response")
                    continuation.resumeWithException(Exception("tcp tuple request $request failed: $response"))
                    return
                }

                val initialMessage = try {
                    Gson().fromJson(body.charStream(), TcpTuple::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "tcp tuple response deserialization failed: $body", e)
                    continuation.resumeWithException(e)
                    return
                }

                continuation.resume(initialMessage)
            }
        })
    }
}
