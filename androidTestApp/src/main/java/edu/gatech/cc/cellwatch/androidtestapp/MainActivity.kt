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
import edu.gatech.cc.cellwatch.androidtestapp.sync.FixedSupabaseEnvironmentProvider
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.androidtestapp.sync.resolveRuntimeProfileFromProperties
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.capability.AndroidPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.CapabilityCaptureReportFormatter
import edu.gatech.cc.cellwatch.domain.fcc.DefaultMsakMeasurementSequenceOrchestratorFactory
import edu.gatech.cc.cellwatch.domain.fcc.MsakServerSelectionHarness
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceRequest
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeMsakMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSupabaseMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfile
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSequenceSyncOrchestrator
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeEnvelopeBuilder
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeResultFormatter
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
    private lateinit var uploadDownloadRepo: UploadDownloadDataRepositoryImpl
    private lateinit var submissionRepo: FccSubmissionRepositoryImpl
    private lateinit var statusText: TextView
    private lateinit var msakModeButton: Button
    private lateinit var supabaseModeButton: Button

    private var syncDriver: AndroidTestSyncDriver? = null
    private var lastGroup: MeasurementGroup? = null
    private var selectedMsakMode: RuntimeMsakMode = RuntimeMsakMode.PUBLIC
    private var selectedSupabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL
    private val allowRemoteSupabase: Boolean = System.getenv("CELLWATCH_ALLOW_REMOTE_SUPABASE") == "true"
    private var runtimeProfile: RuntimeSyncMsakProfile = resolveRuntimeProfile()
    private val smokeEnvelopeBuilder = SyncSmokeEnvelopeBuilder()
    private val smokeFormatter = SyncSmokeResultFormatter()

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
        uploadDownloadRepo = UploadDownloadDataRepositoryImpl(db.uploadDownloadDataQueries, EmptyCoroutineContext)
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
        msakModeButton = Button(this).apply {
            setOnClickListener { cycleMsakMode() }
        }
        supabaseModeButton = Button(this).apply {
            setOnClickListener { cycleSupabaseMode() }
        }
        val locateServersButton = Button(this).apply {
            text = "Select MSAK Servers (Shared Selector)"
            setOnClickListener { runSelectServers() }
        }
        val runPhase3SequenceButton = Button(this).apply {
            text = "Run Phase3 Sequence (Shared Orchestrator)"
            setOnClickListener { runPhase3Sequence() }
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
        content.addView(msakModeButton)
        content.addView(supabaseModeButton)
        content.addView(locateServersButton)
        content.addView(runPhase3SequenceButton)
        content.addView(statusText)
        refreshModeUi()
        root.addView(content)
        return root
    }

    private fun resolveRuntimeProfile(): RuntimeSyncMsakProfile {
        return resolveRuntimeProfileFromProperties(
            msakMode = selectedMsakMode,
            supabaseMode = selectedSupabaseMode,
            allowRemoteSupabase = allowRemoteSupabase,
        )
    }

    private fun cycleMsakMode() {
        selectedMsakMode = when (selectedMsakMode) {
            RuntimeMsakMode.PUBLIC -> RuntimeMsakMode.STAGING
            RuntimeMsakMode.STAGING -> RuntimeMsakMode.LOCAL
            RuntimeMsakMode.LOCAL -> RuntimeMsakMode.PUBLIC
        }
        applyRuntimeModeChange()
    }

    private fun cycleSupabaseMode() {
        selectedSupabaseMode = when (selectedSupabaseMode) {
            RuntimeSupabaseMode.LOCAL -> RuntimeSupabaseMode.TESTING
            RuntimeSupabaseMode.TESTING -> RuntimeSupabaseMode.LIVE
            RuntimeSupabaseMode.LIVE -> RuntimeSupabaseMode.LOCAL
        }
        applyRuntimeModeChange()
    }

    private fun applyRuntimeModeChange() {
        runtimeProfile = resolveRuntimeProfile()
        syncDriver = null
        refreshModeUi()
        statusText.text =
            "Runtime mode updated.\n" +
                "MSAK=${selectedMsakMode.name}, Supabase=${selectedSupabaseMode.name}, remoteAllowed=$allowRemoteSupabase"
    }

    private fun refreshModeUi() {
        msakModeButton.text = "MSAK Mode: ${selectedMsakMode.name} (tap to cycle)"
        supabaseModeButton.text = "Supabase Mode: ${selectedSupabaseMode.name} (tap to cycle)"
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
                val envelope = smokeEnvelopeBuilder.measurementComplete(
                    uploadTimeSet = uploadedAt != null,
                    errorMessage = getSyncDriver().state.value.lastError,
                )
                smokeFormatter.format(envelope) +
                    "\ngroup=${group.id}\nuploadTime=$uploadedAt"
            }.onSuccess { statusText.text = it }
                .onFailure {
                    val envelope = smokeEnvelopeBuilder.failure(
                        scenario = "measurement-complete-sync",
                        errorMessage = it.message,
                    )
                    statusText.text = smokeFormatter.format(envelope)
                }
        }
    }

    private fun runMapSyncOnly() {
        scope.launch {
            runCatching {
                val report = getSyncDriver().runMapStartSync()
                val envelope = smokeEnvelopeBuilder.mapStart(
                    hasReport = report != null,
                    errorMessage = getSyncDriver().state.value.lastError,
                )
                smokeFormatter.format(envelope) + "\n" + formatReport(report)
            }.onSuccess { statusText.text = it }
                .onFailure {
                    val envelope = smokeEnvelopeBuilder.failure(
                        scenario = "map-start-sync",
                        errorMessage = it.message,
                    )
                    statusText.text = smokeFormatter.format(envelope)
                }
        }
    }

    private fun runSelectServers() {
        val harness = MsakServerSelectionHarness(
            runtimeProfile.msakConfig.copy(userAgent = "android-test-app-harness")
        )
        harness.runDefaultScenario { result, error ->
            runOnUiThread {
                statusText.text = if (error != null) {
                    "server-select failed: ${error.message}"
                } else {
                    "server-select throughput=${result?.throughputMachine}\n" +
                        "latency=${result?.latencyMachine}\n" +
                        "fallback=${result?.fallbackUsed}"
                }
            }
            harness.close()
        }
    }

    private fun runPhase3Sequence() {
        scope.launch {
            runCatching {
                val capabilityProvider = AndroidPlatformCapabilityProvider(applicationContext)
                val capabilitySummary = runCatching {
                    CapabilityCaptureReportFormatter.format(
                        CapabilityCaptureReportFormatter.fromSnapshot(
                            capabilityProvider.captureSnapshot(),
                        ),
                    )
                }.getOrElse { error ->
                    "capabilities(capture=FAILED, error=${error.message})"
                }
                val request = MeasurementSequenceRequest(
                    groupId = "phase3-${Clock.System.now().toEpochMilliseconds()}",
                    inVehicle = false,
                    mode = edu.gatech.cc.cellwatch.domain.model.CollectionMode.FCC_CHALLENGE,
                    measurementId = null,
                )
                val resultStore = RepositoryBackedMeasurementResultStore(
                    measurementRepository = measurementRepo,
                    latencyDataRepository = latencyRepo,
                    uploadDownloadDataRepository = uploadDownloadRepo,
                    submissionRepository = submissionRepo,
                )
                val sequenceOrchestrator = DefaultMsakMeasurementSequenceOrchestratorFactory.create(
                    config = runtimeProfile.msakConfig.copy(userAgent = "android-test-app-phase3-sync"),
                    resultStore = resultStore,
                    appSource = "android-test-app-phase3-sync",
                    capabilityProvider = capabilityProvider,
                )
                val syncOrchestrator = MeasurementSequenceSyncOrchestrator(
                    sequenceOrchestrator = sequenceOrchestrator,
                    uploadTriggerUseCase = createSyncDriverFactory().createUploadTriggerUseCase(resolveSupabaseTarget()),
                )
                syncOrchestrator.run(request) to capabilitySummary
            }.onSuccess { (outcome, capabilitySummary) ->
                val sequence = outcome.sequenceOutcome
                val persistedMeasurements = measurementRepo.getByGroupId(sequence.group.id).size
                val persistedSubmissions = if (submissionRepo.getById(sequence.group.id) != null) 1 else 0
                val uploadTime = outcome.measurementCompleteUploadTime?.toEpochMilliseconds() ?: -1L
                val envelope = smokeEnvelopeBuilder.phase3Sequence(
                    measurementCompleteUploadTimeSet = outcome.measurementCompleteUploadTime != null,
                    persistedMeasurements = persistedMeasurements,
                    persistedSubmissions = persistedSubmissions,
                    errorMessage = null,
                )
                statusText.text = smokeFormatter.format(envelope) +
                    "\ngroup=${sequence.group.id}\n" +
                    "throughput=${sequence.throughputServerMachine}\n" +
                    "latency=${sequence.latencyServerMachine}\n" +
                    "submissionCreated=${sequence.group.submission != null}\n" +
                    "mapStartUploaded(m=${outcome.mapStartReport.measurements.uploaded},s=${outcome.mapStartReport.submissions.uploaded})\n" +
                    "measurementCompleteUploadTimeMs=$uploadTime\n" +
                    "persistedMeasurements=$persistedMeasurements, persistedSubmissions=$persistedSubmissions\n" +
                    capabilitySummary
            }.onFailure {
                val envelope = smokeEnvelopeBuilder.failure(
                    scenario = "phase3-sequence-sync",
                    errorMessage = it.message,
                )
                statusText.text = smokeFormatter.format(envelope)
            }
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

        val created = createSyncDriverFactory().create(resolveSupabaseTarget())
        syncDriver = created
        return created
    }

    private fun createSyncDriverFactory(): AndroidTestSyncDriverFactory {
        return AndroidTestSyncDriverFactory(
            database = db,
            deviceAuthStore = InMemoryDeviceAuthStore(),
            tcpTupleProvider = object : TcpTupleProvider {
                override suspend fun getPublicTcpTuple(): TcpTuple = TcpTuple(
                    remoteAddress = "203.0.113.20",
                    remotePort = 443,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
            },
            environmentProvider = FixedSupabaseEnvironmentProvider(runtimeProfile.syncConfig),
            io = EmptyCoroutineContext,
        )
    }

    private fun resolveSupabaseTarget(): SupabaseTarget {
        return if (runtimeProfile.supabaseMode == RuntimeSupabaseMode.LOCAL) {
            SupabaseTarget.LOCAL
        } else {
            SupabaseTarget.REMOTE
        }
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
