package edu.gatech.cc.cellwatch.domain.fcc

import android.util.Log
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.latency.LatencyTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import kotlin.math.abs

class LatencyTest(
    server: Server,
    client: OkHttpClient? = null,
    measurementId: String? = null,
) {
    private val TAG = this::class.simpleName
    private val msakTest = LatencyTest(server, client, measurementId)
    private val _rttChan = Channel<Int>(32)

    val rttChan: ReceiveChannel<Int> = _rttChan

    suspend fun run(): LatencyResult {
        val fallbackStartTime = Clock.System.now()

        try {
            msakTest.start()

            msakTest.updatesChan.consumeEach {
                if (it.message.LastRTT != null) {
                    val result = _rttChan.trySend(it.message.LastRTT)
                    if (!result.isSuccess) {
                        Log.d(TAG, "failed to send rtt on channel: $result")
                    }
                }
            }

            val result = msakTest.result
            val error = msakTest.error

            val rtts = result?.RoundTrips?.filter { !it.Lost }?.map { it.RTT }
            val meanRTT = rtts?.average()
            val variance = if (meanRTT != null) {
                rtts.map { abs(it - meanRTT) }.sum() / rtts.size
            } else {
                null
            }

            val startTime = msakTest.startTime ?: fallbackStartTime
            val endTime = msakTest.endTime ?: Clock.System.now()

            return LatencyResult(
                msakTest.serverHost,
                error == null,
                startTime,
                (endTime - startTime).inWholeMicroseconds,
                meanRTT?.toInt() ?: 0,
                variance?.toInt() ?: 0,
                result?.PacketsSent ?: 0,
                result?.PacketsReceived ?: 0,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "unexpected error running latency test", e)
            msakTest.stop()
            throw e
        } finally {
            _rttChan.close()
        }
    }
}
