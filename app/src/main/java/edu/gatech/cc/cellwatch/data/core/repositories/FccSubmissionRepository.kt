package edu.gatech.cc.cellwatch.data.core.repositories

import edu.gatech.cc.cellwatch.core.util.Log
import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.dao.FccSubmissionDao
import edu.gatech.cc.cellwatch.data.local.model.asExternalModel
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.TcpTuple
import edu.gatech.cc.cellwatch.data.model.asEntity
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
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

class FccSubmissionRepository(
    private val fccSubmissionDao: FccSubmissionDao,
    private val networkDataSource: NetworkMeasurementDatasource
) {
    private val TAG = this::class.simpleName

    @WorkerThread
    suspend fun getUnsynchronizedFccSubmissions(): List<FccSubmission> =
        fccSubmissionDao.getUnsynchronizedFccSubmissions().map(FccSubmissionEntity::asExternalModel)

    @WorkerThread
    suspend fun insertFccSubmission(FccSubmission: FccSubmission) {
        val FccSubmissionEntity = FccSubmission.asEntity()
        fccSubmissionDao.insertFccSubmission(FccSubmissionEntity)
    }

    /**
     * Store-and-forward FccSubmission data
     * Query for any FccSubmission data in local Room database and upload it to
     * the network database (Supabase). Clear local database if successful.
     */
    @WorkerThread
    suspend fun uploadFccSubmissions(): Instant? {
        val fccSubmissions = getUnsynchronizedFccSubmissions()

        if (fccSubmissions.isNotEmpty()) {
            Log.d(TAG, "uploadFccSubmissions: Attempting to upload ${fccSubmissions.size} FccSubmissions")
            try {
                val tuple = getPubicTCPTuple()
                Log.d(TAG, "adding tcp tuple to submissions $tuple")
                fccSubmissions.forEach {
                    it.sourceIp = tuple.remoteAddress
                    it.sourcePort = "${tuple.remotePort}"
                    it.serverTimestamp = Instant.fromEpochMilliseconds(tuple.timestamp)
                }

                networkDataSource.insertFccSubmissions(fccSubmissions)
            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadFccSubmissions: ${e.message}")
                throw e
            }

            Log.d(TAG, "Update ${fccSubmissions.size} FccSubmissions as synchronized")
            val uploadTime = Clock.System.now()

            fccSubmissions.forEach { FccSubmission ->
                val FccSubmissionEntity = FccSubmission.asEntity()
                FccSubmissionEntity.uploadTime = uploadTime
                FccSubmissionEntity.sourceIp = FccSubmission.sourceIp
                FccSubmissionEntity.sourcePort = FccSubmission.sourcePort
                FccSubmissionEntity.serverTimestamp = FccSubmission.serverTimestamp
                fccSubmissionDao.updateFccSubmission(FccSubmissionEntity)
            }

            return uploadTime
        } else {
            Log.d(TAG, "No FccSubmissions to upload !!!")
            return null
        }
    }

    suspend fun getPubicTCPTuple(
        serviceUrl: String = BuildConfig.TCP_TUPLE_URL,
    ): TcpTuple = suspendCoroutine { continuation ->
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(serviceUrl)
            .header("User-Agent", BuildConfig.USER_AGENT)
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
