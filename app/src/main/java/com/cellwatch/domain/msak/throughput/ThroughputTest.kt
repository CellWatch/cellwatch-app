package com.cellwatch.domain.msak.throughput

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cellwatch.domain.msak.Server
import io.ktor.http.Url
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

class ThroughputTest(
    server: Server,
    direction: ThroughputDirection,
    streams: Int = 3,
    private val duration: Long = 10 * 1000,
    private val delay: Long = 0,
    measurementId: String? = null,
    private val serverEndTimeGraceMillis: Long = 5000,
) {
    private val TAG = this::class.simpleName
    private val url = server.getThroughputUrl(direction, streams, duration, delay, measurementId)
    private val _updatesChan = Channel<ThroughputUpdate>(32)
    private val handler = Handler(Looper.getMainLooper())
    private val startStopSem = Semaphore(1)

    val streams = List(streams) { ThroughputStream(it, url, direction) }
    val updatesChan: ReceiveChannel<ThroughputUpdate> = _updatesChan
    var startTime: Instant? = null; private set
    var endTime: Instant? = null; private set
    val started; get() = startTime != null
    val ended; get() = endTime != null
    val serverHost = Url(url).host

    fun start() {
        startStopSem.acquire()

        try {
            if (started) {
                throw Throwable("already started")
            }

            startTime = Clock.System.now()
            streams.forEachIndexed { i, stream ->
                handler.postDelayed({ runStream(stream) }, i * delay)
            }

            handler.postDelayed({
                Log.w(TAG, "test not ended by server")
                finish()
            }, duration + serverEndTimeGraceMillis)
        } finally {
            startStopSem.release()
        }
    }

    fun stop() {
        startStopSem.acquire()

        try {
            if (!started) {
                throw Throwable("can't stop before starting")
            }

            finish()
        } finally {
            startStopSem.release()
        }
    }

    private fun finish() {
        if (ended) {
            return
        }

        for (stream in streams) {
            try {
                stream.stop()
            } catch (n: NotStartedException) {
                // ignore
            }
        }

        handler.removeCallbacksAndMessages(null)
        _updatesChan.close()
        endTime = Clock.System.now()
    }

    private fun runStream(stream: ThroughputStream) {
        thread {
            try {
                runBlocking {
                    stream.start()
                    stream.updatesChan.consumeEach {
                        val result = _updatesChan.trySend(it)
                        if (!result.isSuccess) {
                            Log.d(TAG, "failed to send throughput update on channel: $result")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "unexpected error running stream", e)
                finish()
            }
        }
    }
}