package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.msak.Server
import edu.gatech.cc.cellwatch.msak.latency.LatencyTest
import edu.gatech.cc.cellwatch.msak.latency.LatencyResult as MsakLatencyResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    val msakTest = LatencyTest(server, client, measurementId, userAgent = CellWatchApp.userAgent)

    val rttChan: ReceiveChannel<Int> = _rttChan

    override suspend fun measure(): LatencyResult {
        val fallbackStartTime = Clock.System.now()

        try {
            if (server is UnreachableServer) {
                return unreachableResult(server.machine, Clock.System.now())
            }

            msakTest.start()

            msakTest.updatesChan.consumeEach { update ->
                update.message.LastRTT?.let {
                    val result = _rttChan.trySend(it)
                    if (!result.isSuccess) {
                        Log.d(TAG, "failed to send rtt on channel: $result")
                    }
                }
            }

            return summarize(
                msakTest.serverHost,
                msakTest.error,
                msakTest.result,
                msakTest.startTime ?: fallbackStartTime,
                msakTest.endTime ?: Clock.System.now(),
            )
        } catch (e: Throwable) {
            Log.e(TAG, "unexpected error running latency test", e)
            msakTest.stop()
            throw e
        } finally {
            _rttChan.close()
        }
    }

    companion object {
        /** The result recorded when no latency server could be located. */
        internal fun unreachableResult(host: String, now: Instant) =
            LatencyResult(host, false, now, 0, 0, 0, 0, 0)

        /**
         * Builds the result of a finished msak latency test from the error and result it ended
         * with. Kept free of Android and msak state so it can be unit tested.
         */
        internal fun summarize(
            serverHost: String,
            error: Throwable?,
            msakResult: MsakLatencyResult?,
            startTime: Instant,
            endTime: Instant,
        ): LatencyResult {
            val result = when (error) {
                null,
                is LatencyTest.AuthorizeFailureExecption,
                is LatencyTest.ResultFailureException,
                is LatencyTest.NoAddrException,
                is LatencyTest.InitialPacketTimeoutException -> msakResult

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

            // According to the FCC, a test is successful if it transmitted/received >0 packets,
            // even if it ends prematurely, for example because of a network error. We already
            // threw non-network errors above.
            val success = (result?.PacketsReceived ?: 0) > 0

            return LatencyResult(
                serverHost,
                success,
                startTime,
                (endTime - startTime).inWholeMicroseconds,
                meanRTT?.toInt() ?: 0,
                stddev?.toInt() ?: 0,
                result?.PacketsSent ?: 0,
                result?.PacketsReceived ?: 0,
            )
        }
    }
}
