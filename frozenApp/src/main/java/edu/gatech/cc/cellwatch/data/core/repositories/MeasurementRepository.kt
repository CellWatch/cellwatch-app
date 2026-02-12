package edu.gatech.cc.cellwatch.data.core.repositories

import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.local.dao.FccSubmissionDao
import edu.gatech.cc.cellwatch.data.local.dao.MeasurementDao
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementWithData
import edu.gatech.cc.cellwatch.data.local.model.asExternalModel
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.data.model.TcpTuple
import edu.gatech.cc.cellwatch.data.model.asEntity
import edu.gatech.cc.cellwatch.data.model.asEntityWithData
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
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
 * database. MeasurementRepository also contains the uploadMeasurements method which uploads all
 * unsynchronized [Measurement] records from the Room database to the remote Supabase database and
 * marks the local records synchronized by setting the uploadTime field to a timestamp.
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

    @WorkerThread
    suspend fun tryUploadMeasurements() {
        val unsynchronizedMeasurements = getUnsynchronizedMeasurementsWithData()
        if (unsynchronizedMeasurements.isEmpty()) {
            Log.d(TAG, "no measurements to upload")
            return
        }

        Log.d(TAG, "attempting to upload ${unsynchronizedMeasurements.size} measurements")

        coroutineScope {
            unsynchronizedMeasurements.map { measurement ->
                async(Dispatchers.IO) {
                    try {
                        val entity = networkDataSource.insertMeasurement(measurement).asEntity()
                        entity.uploadTime = Clock.System.now()
                        measurementDao.updateMeasurement(entity)
                        Log.i(TAG, "measurement ${measurement.id} uploaded")
                    } catch (e: NetworkMeasurementDatasource.NetworkError) {
                        Log.i(TAG, "network error uploading measurement ${measurement.id}", e)
                    } catch (e: NetworkMeasurementDatasource.DuplicateKeyError) {
                        if (canMarkUploaded(measurement)) {
                            val entity = measurement.asEntity()
                            entity.uploadTime = Clock.System.now()
                            measurementDao.updateMeasurement(entity)
                            Log.i(TAG, "marked measurement ${measurement.id} as previously uploaded")
                        } else {
                            Log.i(TAG, "duplicate key error uploading measurement and can't mark as previously uploaded; will retry later", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "unexpected error uploading measurement ${measurement.id}", e)
                    }
                }
            }.awaitAll()
        }
    }

        @WorkerThread
    suspend fun canMarkUploaded(measurement: Measurement): Boolean {
        try {
            val networkMeasurement = withContext(Dispatchers.IO) {
                networkDataSource.getMeasurementById(measurement.id)
            }

            if (networkMeasurement.timestamp == measurement.timestamp) {
                return true
            } else {
                Log.e(TAG, "duplicate measurement ids with mismatched timestamps: $networkMeasurement $measurement")
            }
        } catch (e: NetworkMeasurementDatasource.NotFoundError) {
            Log.e(TAG, "duplicate key error for measurement ${measurement.id}, but measurement not found", e)
        } catch (e: NetworkMeasurementDatasource.NetworkError) {
            Log.i(TAG, "network error fetching existing measurement ${measurement.id}", e)
        } catch (e: Exception) {
            Log.e(TAG, "unexpected error fetching existing measurement ${measurement.id}", e)
        }

        return false
    }

    @WorkerThread
    suspend fun tryUploadFccSubmissions() {
        val fccSubmissions = getUnsynchronizedFccSubmissions()
        if (fccSubmissions.isEmpty()) {
            Log.d(TAG, "no submissions to upload")
            return
        }

        val tuple = try {
            withContext(Dispatchers.IO) { getPubicTCPTuple() }
        } catch (e: IOException) {
            Log.i(TAG, "network error getting tcp tuple", e)
            return
        } catch (e: Exception) {
            Log.e(TAG, "unexpected error getting tcp tuple", e)
            return
        }

        Log.d(TAG, "adding tcp tuple to submissions $tuple")
        fccSubmissions.forEach {
            it.sourceIp = tuple.remoteAddress
            it.sourcePort = tuple.remotePort
            it.serverTimestamp = Instant.fromEpochMilliseconds(tuple.timestamp)
        }

        Log.d(TAG, "attempting to upload ${fccSubmissions.size} submissions")

        coroutineScope {
            fccSubmissions.map { submission ->
                async(Dispatchers.IO) {
                    try {
                        val entity = networkDataSource.insertFccSubmission(submission).asEntity()
                        entity.uploadTime = Clock.System.now()
                        submissionDao.updateFccSubmission(entity)
                        Log.i(TAG, "submission ${submission.id} uploaded")
                    } catch (e: NetworkMeasurementDatasource.NetworkError) {
                        Log.i(TAG, "network error uploading submission ${submission.id}", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "unexpected error uploading submission ${submission.id}", e)
                    }
                }
            }.awaitAll()
        }
    }

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
