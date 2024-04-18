package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.latency.LatencyTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import kotlin.math.pow

class LatencyTest(
    private val server: Server,
    client: OkHttpClient? = null,
    groupId: String,
    measurementId: String? = null,
): MeasurementTest<LatencyResult>(groupId, "latency") {
    private val TAG = this::class.simpleName
    private val _rttChan = Channel<Int>(32)
    val msakTest = LatencyTest(server, client, measurementId)

    val rttChan: ReceiveChannel<Int> = _rttChan

    override suspend fun measure(): LatencyResult {
        val fallbackStartTime = Clock.System.now()

        try {
            if (server is UnreachableServer) {
                return LatencyResult(server.machine, false, Clock.System.now(), 0, 0, 0, 0, 0)
            }

            msakTest.start()

            msakTest.updatesChan.consumeEach {
                if (it.message.LastRTT != null) {
                    val result = _rttChan.trySend(it.message.LastRTT)
                    if (!result.isSuccess) {
                        Log.d(TAG, "failed to send rtt on channel: $result")
                    }
                }
            }

            val result = when (val error = msakTest.error) {
                null,
                is LatencyTest.AuthorizeFailureExecption,
                is LatencyTest.ResultFailureException,
                is LatencyTest.NoAddrException,
                is LatencyTest.InitialPacketTimeoutException -> msakTest.result

                // Throw non-network related errors. These indicate that something went wrong that
                // invalidates the test.
                else -> throw error
            }

            val rtts = result?.RoundTrips?.filter { !it.Lost }?.map { it.RTT }
            val meanRTT = rtts?.average()
            val stddev = if (meanRTT != null) {
                (rtts.sumOf { (it - meanRTT).pow(2) } / rtts.size).pow(0.5)
            } else {
                null
            }

            val startTime = msakTest.startTime ?: fallbackStartTime
            val endTime = msakTest.endTime ?: Clock.System.now()

            // According to the FCC, a test is successful if it transmitted/received >0 packets,
            // even if it ends prematurely, for example because of a network error. We already
            // threw non-network errors above.
            val success = (result?.PacketsReceived ?: 0) > 0

            return LatencyResult(
                msakTest.serverHost,
                success,
                startTime,
                (endTime - startTime).inWholeMicroseconds,
                meanRTT?.toInt() ?: 0,
                stddev?.toInt() ?: 0,
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
