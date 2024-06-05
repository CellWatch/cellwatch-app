package edu.gatech.cc.cellwatch.domain.fcc

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.concurrent.thread

class MeasurementService: Service() {
    private val TAG = this::class.simpleName
    private var state = MutableStateFlow<State?>(null)
    private var machine: String? = null
    private var latency: Measurement? = null
    private var download: Measurement? = null
    private var upload: Measurement? = null
    private var group: MeasurementGroup? = null
    private var groupId: String? = null

    companion object {
        const val EXTRA_COLLECTION_MODE = "collection_mode"
        const val EXTRA_IN_VEHICLE = "in_vehicle"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        val mode = intent?.extras?.getString(EXTRA_COLLECTION_MODE)?.let { CollectionMode.valueOf(it) }
        val inVehicle = intent?.extras?.getBoolean(EXTRA_IN_VEHICLE)

        if (mode == null || inVehicle == null) {
            Log.e(TAG, "started without required extras $mode $inVehicle")
        } else {
            thread {
                Looper.prepare()
                Handler(Looper.myLooper()!!).post {
                    runBlocking {
                        try {
                            runTest(mode, inVehicle)
                        } catch (e: Throwable) {
                            Log.e(TAG, "unexpected error running test", e)
                        } finally {
                            stopSelf()
                            Looper.myLooper()?.quitSafely()
                        }
                    }
                }
                Looper.loop()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(
            this,
            CellWatchApp.measurementNotificationChannel.id
        )
            .setContentText(getText(R.string.measurement_in_progress))
            .setSmallIcon(R.drawable.logo_monochrome)
            .build()

        ServiceCompat.startForeground(
            this,
            100,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    override fun onBind(intent: Intent?): IBinder {
        return MeasurementBinder(this)
    }

    private suspend fun runTest(mode: CollectionMode, inVehicle: Boolean) {
        val starting = state.compareAndSet(null, State.START) || state.compareAndSet(State.DONE, State.START)
        if (!starting) {
            Log.e(TAG, "measurement already running")
            return
        }

        machine = null
        latency = null
        download = null
        upload = null

        val gid = UUID.randomUUID().toString()
        groupId = gid

        try {
            val g = MeasurementManager.runTestSequence(
                gid,
                inVehicle,
                mode,
                { state.update { State.LOCATE } },
                { machine = it },
                { state.update { State.LATENCY } },
                { latency = it },
                { state.update { State.DOWNLOAD } },
                { download = it },
                { state.update { State.UPLOAD } },
                { upload = it },
            )
            group = g
        } catch (e: Exception) {
            Log.e(TAG, "running test failed", e)
        } finally {
            state.update { State.DONE }
        }
    }

    enum class State {START, LOCATE, LATENCY, DOWNLOAD, UPLOAD, DONE}

    class MeasurementBinder(private val service: MeasurementService): Binder() {
        val latency: Measurement?
            get() = service.latency

        val download: Measurement?
            get() = service.download

        val upload: Measurement?
            get() = service.upload

        val group: MeasurementGroup?
            get() = service.group

        val groupId: String?
            get() = service.groupId

        val state: StateFlow<State?> = service.state
    }
}