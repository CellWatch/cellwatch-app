package com.example.ndtm

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import okhttp3.OkHttpClient
import java.util.*
import kotlin.concurrent.schedule

class NdtMDownloadTest(
    private val client: OkHttpClient,
    private val url: String,
    numStreams: Int,
) {
    private val TAG = NdtMDownloadTest::class.simpleName
    private val streams = Array(numStreams) { NdtMDownloadStream(it, client, url) }
    private var startTime: Long = 0
    private val progressChan = Channel<NdtMTestMetrics>()
    val progress: ReceiveChannel<NdtMTestMetrics> = progressChan

    suspend fun run(): NdtMTestResult {
        val maxDurationTimer = Timer()
        maxDurationTimer.schedule(NDTM_MAX_DURATION) {
            Log.d(TAG, "max test duration reached")
            for (stream in streams) stream.cancel(false)
        }

        try {
            startTime = SystemClock.elapsedRealtimeNanos() / 1000

            coroutineScope {
                for (stream in streams) {
                    stream.start()

                    async {
                        stream.updates.consumeEach { progressChan.trySend(getLatestTestMetrics()) }
                        for (s in streams) s.cancel(false)
                    }
                }
            }
        } catch (c: CancellationException) {
            Log.i(TAG, "download test cancelled")
            for (stream in streams) stream.cancel(true)
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "unexpected error running download test", t)
            for (stream in streams) stream.cancel(true)
            throw t
        } finally {
            maxDurationTimer.cancel()
            progressChan.close()
        }

        val success = streams.all { it.latestUpdate?.success == true }
        return NdtMTestResult(success, getLatestTestMetrics())
    }

    private fun getLatestTestMetrics(): NdtMTestMetrics {
        val duration = SystemClock.elapsedRealtimeNanos() / 1000 - startTime
        var bytesTransferred: Long = 0
        var bytesPerSec = 0.0
        for (stream in streams) {
            val appInfo = stream.latestUpdate?.measurement?.AppInfo ?: continue
            bytesTransferred += appInfo.NumBytes
            bytesPerSec += appInfo.NumBytes / appInfo.ElapsedTime * 1e6
        }

        return NdtMTestMetrics(bytesPerSec, bytesTransferred, duration)
    }
}

data class NdtMTestResult(
    val success: Boolean,
    val metrics: NdtMTestMetrics,
)

data class NdtMTestMetrics(
    val bytesPerSec: Double,
    val bytesTransferred: Long,
    val duration: Long,
)
