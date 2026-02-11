package edu.gatech.cc.cellwatch.data.sync

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import edu.gatech.cc.cellwatch.data.local.dao.FccSubmissionDao
import edu.gatech.cc.cellwatch.data.local.dao.MeasurementDao
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementWithData
import edu.gatech.cc.cellwatch.data.local.model.asExternalModel
import edu.gatech.cc.cellwatch.data.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncLocalStore
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import kotlinx.coroutines.flow.first
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

class AndroidDeviceAuthStore(
    private val localDataStore: LocalDataStore,
) : edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore {
    override suspend fun getDeviceId(): String = localDataStore.getDeviceId.first()

    override suspend fun getDeviceSecret(): String? = localDataStore.getDeviceSecret.first()

    override suspend fun saveDeviceSecret(secret: String) {
        localDataStore.saveDeviceSecret(secret)
    }
}

class RoomMeasurementSyncLocalStore(
    private val measurementDao: MeasurementDao,
    private val submissionDao: FccSubmissionDao,
) : MeasurementSyncLocalStore {

    override suspend fun getUnsyncedMeasurements(): List<Measurement> =
        measurementDao.getUnsynchronizedMeasurementsWithData()
            .map(MeasurementWithData::asExternalModel)
            .map { it.toShared() }

    override suspend fun markMeasurementUploaded(id: String, uploadedAt: Instant) {
        val entity = measurementDao.getMeasurementById(id)
        entity.uploadTime = uploadedAt
        measurementDao.updateMeasurement(entity)
    }

    override suspend fun getUnsyncedSubmissions(): List<FccSubmission> =
        submissionDao.getUnsynchronizedFccSubmissions()
            .map(FccSubmissionEntity::asExternalModel)
            .map { it.toShared() }

    override suspend fun markSubmissionUploaded(id: String, uploadedAt: Instant) {
        val entity = submissionDao.getFccSubmissionById(id)
        entity.uploadTime = uploadedAt
        submissionDao.updateFccSubmission(entity)
    }
}

class AndroidTcpTupleProvider(
    private val serviceUrl: String,
    private val userAgent: String,
) : TcpTupleProvider {
    override suspend fun getPublicTcpTuple(): edu.gatech.cc.cellwatch.domain.model.TcpTuple =
        suspendCoroutine { continuation ->
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder()
                .url(serviceUrl)
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body
                    if (response.code != 200 || body == null) {
                        continuation.resumeWithException(
                            IOException("tcp tuple request failed: $response"),
                        )
                        return
                    }

                    val tuple = try {
                        Gson().fromJson(body.charStream(), TcpTuple::class.java)
                    } catch (e: JsonSyntaxException) {
                        continuation.resumeWithException(e)
                        return
                    }

                    continuation.resume(
                        edu.gatech.cc.cellwatch.domain.model.TcpTuple(
                            remoteAddress = tuple.remoteAddress,
                            remotePort = tuple.remotePort,
                            timestamp = tuple.timestamp,
                        )
                    )
                }
            })
        }
}
