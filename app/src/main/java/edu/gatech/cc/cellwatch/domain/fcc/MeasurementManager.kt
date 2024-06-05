package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.locate.LocateManager
import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputDirection
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import github.nisrulz.easydeviceinfo.base.EasyAppMod
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.UUID

object MeasurementManager {
    private val TAG = this::class.simpleName
    private var _bytesPerSecState = MutableStateFlow(0.0)

    private fun updateBytesPerSec(newBytesPerSec: Double) {
        _bytesPerSecState.update { newBytesPerSec }
    }

    fun checkCellular(): Boolean {
        return TelephonyInfoManager.getConnectionType() != NetworkConnectionType.WIFI
                && TelephonyInfoManager.isCellularDataEnabled() == true
    }

    suspend fun runTestSequence(
        groupId: String,
        inVehicle: Boolean,
        mode: CollectionMode,
        onLocateStart: () -> Unit,
        onLocateComplete: (r: String) -> Unit,
        onLatencyStart: () -> Unit,
        onLatencyComplete: (m: Measurement) -> Unit,
        onDownloadStart: () -> Unit,
        onDownloadComplete: (m: Measurement) -> Unit,
        onUploadStart: () -> Unit,
        onUploadComplete: (m: Measurement) -> Unit,
    ): MeasurementGroup {
        val measurementId: String? = if (BuildConfig.MSAK_SERVER_ENV == "local") {
            UUID.randomUUID().toString()
        } else {
            null
        }

        Log.i(TAG,"RUNNING TEST SEQUENCE with measurement id $measurementId")

        val client = OkHttpClient.Builder().build()

        Log.d(TAG, "selecting server")

        onLocateStart()
        val servers = chooseMsakServers(client)
        val throughputServer = servers.first
        val latencyServer = servers.second
        onLocateComplete(throughputServer.machine)

        Log.i(TAG, "selected servers $throughputServer $latencyServer")

        onLatencyStart()
        val latencyMeasurement = runLatencyTest(client, latencyServer, groupId, measurementId)
        insertMeasurement(latencyMeasurement)
        onLatencyComplete(latencyMeasurement)

        onDownloadStart()
        val downloadMeasurement = runThroughputTest(throughputServer, ThroughputDirection.DOWNLOAD, groupId, measurementId)
        insertMeasurement(downloadMeasurement)
        onDownloadComplete(downloadMeasurement)

        onUploadStart()
        val uploadMeasurement = runThroughputTest(throughputServer, ThroughputDirection.UPLOAD, groupId, measurementId)
        insertMeasurement(uploadMeasurement)
        onUploadComplete(uploadMeasurement)

        val fccSubmission = if (
            mode == CollectionMode.FCC_CHALLENGE &&
            latencyMeasurement.connectionType != NetworkConnectionType.WIFI &&
            downloadMeasurement.connectionType != NetworkConnectionType.WIFI &&
            uploadMeasurement.connectionType != NetworkConnectionType.WIFI &&
            latencyMeasurement.cellularDataEnabled != false &&
            downloadMeasurement.cellularDataEnabled != false &&
            uploadMeasurement.cellularDataEnabled != false
        ) {
            val submission = FccSubmission(
                id = groupId,
                deviceId = latencyMeasurement.deviceId ?: downloadMeasurement.deviceId ?: uploadMeasurement.deviceId,
                deviceTimestamp = Clock.System.now(),
                inVehicle = inVehicle,
                externalAntenna = false,
                deviceType = "Android",
                deviceManufacturer = latencyMeasurement.deviceManufacturer ?: downloadMeasurement.deviceManufacturer ?: uploadMeasurement.deviceManufacturer,
                deviceModel = latencyMeasurement.deviceModel ?: downloadMeasurement.deviceModel ?: uploadMeasurement.deviceModel,
                deviceOsName = "Android ${latencyMeasurement.deviceOsVersion ?: downloadMeasurement.deviceOsVersion ?: uploadMeasurement.deviceOsVersion}",
                appName = latencyMeasurement.appName ?: downloadMeasurement.appName ?: uploadMeasurement.appName,
                appVersion = EasyAppMod(CellWatchApp.applicationContext()).appVersion,
                provider = TelephonyInfoManager.getProviderName(),
                simCountryCode = latencyMeasurement.simMcc ?: downloadMeasurement.simMcc ?: uploadMeasurement.simMcc,
                simNetworkCode = latencyMeasurement.simMnc ?: downloadMeasurement.simMnc ?: uploadMeasurement.simMnc,
                netCountryCode = latencyMeasurement.netMcc ?: downloadMeasurement.netMcc ?: uploadMeasurement.netMcc,
                netNetworkCode = latencyMeasurement.netMnc ?: downloadMeasurement.netMnc ?: uploadMeasurement.netMnc,
                contactName = CellWatchApp.settingsRepository.getName(),
                contactEmail = CellWatchApp.settingsRepository.getEmail(),
                contactPhone = CellWatchApp.settingsRepository.getPhoneNumber(),
            )

            insertFccSubmission(submission)
            submission
        } else {
            Log.i(TAG, "skipping fcc submission insertion for group $groupId")
            null
        }

        return MeasurementGroup(latencyMeasurement, downloadMeasurement, uploadMeasurement, fccSubmission)
    }

    private suspend fun runThroughputTest(
        server: Server,
        direction: ThroughputDirection,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        val dir = if (direction == ThroughputDirection.DOWNLOAD) "download" else "upload"
        Log.i(TAG, "running $dir test")

        val measurement = try {
            val test = ThroughputTest(server, 3, direction, groupId, measurementId)

            coroutineScope {
                launch {
                    withContext(Dispatchers.IO) {
                        test.metricsChan.consumeEach {
                            Log.v(TAG, "got metrics $it")
                            updateBytesPerSec(it.bytesPerSec)
                        }
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "$dir test failed: $t")
            throw t
        } finally {
            updateBytesPerSec(0.0)
        }

        Log.i(TAG, "$dir test complete: $measurement")
        return measurement
    }

    private suspend fun runLatencyTest(
        client: OkHttpClient,
        server: Server,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        val measurement = try {
            val test = LatencyTest(server, client, groupId, measurementId)

            coroutineScope {
                launch {
                    test.rttChan.consumeEach {
                        Log.v(TAG, "got rtt $it")
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "latency test failed", t)
            throw t
        }

        Log.d(TAG, "got latency result: $measurement")
        return measurement
    }

    private suspend fun insertFccSubmission(fccSubmission: FccSubmission) {
        try {
            CellWatchApp.measurementRepository.insertFccSubmission(fccSubmission)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting new FccSubmission in MeasurementManager: ${e.message}")
            throw e
        }
    }

    private suspend fun insertMeasurement(m: Measurement) {
        try {
            CellWatchApp.measurementRepository.insertMeasurement(m)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting new measurement in MeasurementManager: ${e.message}")
            throw e
        }
    }

    private suspend fun chooseMsakServers(client: OkHttpClient? = null): Pair<Server, Server> {
        val manager = LocateManager(client)
        try {
            val throughputServers = manager.locateThroughputServers()

            if (throughputServers.isEmpty()) {
                Log.i(TAG, "no throughput servers")
                throw Exception("no throughput servers found")
            }

            val throughputServer = try {
                throughputServers.maxBy { ping(it.machine) }
            } catch (t: Throwable) {
                Log.i(TAG, "pinging available servers failed", t)
                throughputServers[0]
            }

            val latencyServers = manager.locateLatencyServers(throughputServer)

            if (latencyServers.isEmpty()) {
                Log.i(TAG, "no latency servers at matching throughput server ${throughputServer.machine}")
                throw Exception("no latency servers found")
            }

            val latencyServer = latencyServers.firstOrNull { it.machine == throughputServer.machine } ?: latencyServers[0]
            return Pair(throughputServer, latencyServer)
        } catch (e: IOException) {
            val server = UnreachableServer(Url(manager.locateUrl).host)
            return Pair(server, server)
        }
    }
}
