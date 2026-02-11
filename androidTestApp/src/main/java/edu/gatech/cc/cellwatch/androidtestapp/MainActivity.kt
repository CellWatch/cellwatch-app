package edu.gatech.cc.cellwatch.androidtestapp

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriverFactory
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: CellwatchDatabase
    private lateinit var measurementRepo: MeasurementRepositoryImpl
    private lateinit var latencyRepo: LatencyDataRepositoryImpl
    private lateinit var submissionRepo: FccSubmissionRepositoryImpl
    private lateinit var statusText: TextView

    private var syncDriver: AndroidTestSyncDriver? = null
    private var lastGroup: MeasurementGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDataLayer()
        setContentView(buildUi())
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun initDataLayer() {
        val sqlDriver = AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = this,
            name = "android-test-app.db",
        )
        db = CellwatchDatabase(sqlDriver)
        measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
        latencyRepo = LatencyDataRepositoryImpl(db.latencyDataQueries, EmptyCoroutineContext)
        submissionRepo = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext)
    }

    private fun buildUi(): ScrollView {
        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Android Test App Harness"
            textSize = 20f
        }
        val seedAndMapSync = Button(this).apply {
            text = "Seed + Run Map-Start Sync"
            setOnClickListener { runSeedAndMapSync() }
        }
        val measurementCompleteSync = Button(this).apply {
            text = "Run Measurement-Complete Sync"
            setOnClickListener { runMeasurementCompleteSync() }
        }
        val runMapSyncOnly = Button(this).apply {
            text = "Run Map-Start Sync (No Seed)"
            setOnClickListener { runMapSyncOnly() }
        }
        statusText = TextView(this).apply {
            text = "Ready. Local Supabase target is enforced by default."
            textSize = 14f
            setPadding(0, 24, 0, 0)
        }

        content.addView(title)
        content.addView(seedAndMapSync)
        content.addView(measurementCompleteSync)
        content.addView(runMapSyncOnly)
        content.addView(statusText)
        root.addView(content)
        return root
    }

    private fun runSeedAndMapSync() {
        scope.launch {
            runCatching {
                val group = seedMeasurementGroup()
                lastGroup = group
                val report = getSyncDriver().runMapStartSync()
                "Seeded ${group.id}\nmap-start report:\n${formatReport(report)}"
            }.onSuccess { statusText.text = it }
                .onFailure { statusText.text = "seed/map-start failed: ${it.message}" }
        }
    }

    private fun runMeasurementCompleteSync() {
        scope.launch {
            runCatching {
                val group = lastGroup ?: seedMeasurementGroup().also { lastGroup = it }
                val uploadedAt = getSyncDriver().runMeasurementCompleteSync(group)
                "measurement-complete group=${group.id}\nuploadTime=$uploadedAt"
            }.onSuccess { statusText.text = it }
                .onFailure { statusText.text = "measurement-complete failed: ${it.message}" }
        }
    }

    private fun runMapSyncOnly() {
        scope.launch {
            runCatching {
                val report = getSyncDriver().runMapStartSync()
                "map-start report:\n${formatReport(report)}"
            }.onSuccess { statusText.text = it }
                .onFailure { statusText.text = "map-start failed: ${it.message}" }
        }
    }

    private suspend fun seedMeasurementGroup(): MeasurementGroup {
        val now = Clock.System.now()
        val groupId = UUID.randomUUID().toString()
        val measurementId = UUID.randomUUID().toString()
        val deviceId = "android-test-app-device"

        val latency = LatencyData(
            id = UUID.randomUUID().toString(),
            measurementId = measurementId,
            rtt = 24,
            jitter = 2,
            sent = 10,
            received = 10,
        )
        val measurement = Measurement(
            id = measurementId,
            groupId = groupId,
            deviceId = deviceId,
            type = "latency",
            timestamp = now,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            latencyData = latency,
        )
        val submission = FccSubmission(
            id = groupId,
            deviceId = deviceId,
            provider = "android-test-app-ui",
            submitted = false,
        )

        measurementRepo.upsert(measurement)
        latencyRepo.upsert(latency)
        submissionRepo.upsert(submission)

        return MeasurementGroup(
            latency = measurement,
            download = null,
            upload = null,
            submission = submission,
            id = groupId,
        )
    }

    private fun getSyncDriver(): AndroidTestSyncDriver {
        val existing = syncDriver
        if (existing != null) return existing

        val created = AndroidTestSyncDriverFactory(
            database = db,
            deviceAuthStore = InMemoryDeviceAuthStore(),
            tcpTupleProvider = object : TcpTupleProvider {
                override suspend fun getPublicTcpTuple(): TcpTuple = TcpTuple(
                    remoteAddress = "203.0.113.20",
                    remotePort = 443,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
            },
            io = EmptyCoroutineContext,
        ).create(SupabaseTarget.LOCAL)
        syncDriver = created
        return created
    }

    private fun formatReport(report: edu.gatech.cc.cellwatch.domain.sync.SyncAllReport?): String {
        if (report == null) return "null (driver error: ${syncDriver?.state?.value?.lastError})"
        return buildString {
            appendLine("measurements: attempted=${report.measurements.attempted}, uploaded=${report.measurements.uploaded}, markedUploaded=${report.measurements.markedUploaded}, networkErrors=${report.measurements.networkErrors}, unexpectedErrors=${report.measurements.unexpectedErrors}")
            appendLine("submissions: attempted=${report.submissions.attempted}, uploaded=${report.submissions.uploaded}, blockedBeforeUpload=${report.submissions.blockedBeforeUpload}, networkErrors=${report.submissions.networkErrors}, unexpectedErrors=${report.submissions.unexpectedErrors}")
        }
    }
}

private class InMemoryDeviceAuthStore(
    private val deviceId: String = UUID.randomUUID().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
