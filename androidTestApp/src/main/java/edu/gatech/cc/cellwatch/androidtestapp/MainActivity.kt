package edu.gatech.cc.cellwatch.androidtestapp

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.onboarding.AndroidOnboardingProfileStore
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriverFactory
import edu.gatech.cc.cellwatch.androidtestapp.sync.FixedSupabaseEnvironmentProvider
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.androidtestapp.sync.resolveRuntimeProfileConfigFromProperties
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingInput
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingUseCase
import edu.gatech.cc.cellwatch.domain.capability.AndroidPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.CapabilityCaptureReportFormatter
import edu.gatech.cc.cellwatch.domain.capability.CapabilityPersistenceSummaryFormatter
import edu.gatech.cc.cellwatch.domain.fcc.DefaultMsakMeasurementSequenceOrchestratorFactory
import edu.gatech.cc.cellwatch.domain.fcc.MsakServerSelectionHarness
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceRequest
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceStage
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeFeatureViewController
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeMapInteractionController
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeMeasurementLocationSnapshot
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeOverlayMode
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeInput
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeViewController
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementNetworkPath
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementPreflightResult
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementPreflightUseCase
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightFlowController
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightFlowUiState
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightFlowViewModel
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightEnvironmentOverrides
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightEnvironmentResolver
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightUiPresenter
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunUiPresenter
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunViewController
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementResultReadModelUseCase
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingPersistenceUseCase
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileSubmission
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileUiState
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileViewModel
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingValidationUseCase
import edu.gatech.cc.cellwatch.domain.settings.SettingsProfileSubmission
import edu.gatech.cc.cellwatch.domain.settings.SettingsProfileUiState
import edu.gatech.cc.cellwatch.domain.settings.SettingsProfileViewModel
import edu.gatech.cc.cellwatch.domain.measurementhistory.MeasurementHistoryStatusInput
import edu.gatech.cc.cellwatch.domain.measurementhistory.MeasurementHistoryStatusUseCase
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeMsakMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeModeUiBridge
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeOnboardingContract
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeProfileConfig
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeProfileResolver
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSupabaseMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfile
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSequenceSyncOrchestrator
import edu.gatech.cc.cellwatch.domain.sync.Phase3UiSliceFormatter
import edu.gatech.cc.cellwatch.domain.sync.Phase3UiSliceResult
import edu.gatech.cc.cellwatch.domain.sync.SyncDiagnosticsConfig
import edu.gatech.cc.cellwatch.domain.sync.SyncDiagnosticsLevel
import edu.gatech.cc.cellwatch.domain.sync.SyncDiagnosticsRegistry
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeEnvelopeBuilder
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeResultFormatter
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerParityHarness
import edu.gatech.cc.cellwatch.domain.sync.renderForStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.net.InetSocketAddress
import java.net.Socket
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import org.json.JSONArray
import org.json.JSONObject
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

class MainActivity : AppCompatActivity() {
    companion object {
        const val RUN_SHARED_SLICE_BUTTON_ID = 1001
        const val STATUS_TEXT_VIEW_ID = 1002
        const val RUN_PHASE3_SEQUENCE_BUTTON_ID = 1003
        const val MSAK_MODE_BUTTON_ID = 1004
        const val SUPABASE_MODE_BUTTON_ID = 1005
        const val ONBOARDING_NAME_INPUT_ID = 1006
        const val ONBOARDING_PHONE_INPUT_ID = 1007
        const val ONBOARDING_EMAIL_INPUT_ID = 1008
        const val ONBOARDING_ACK_CHECKBOX_ID = 1009
        const val ONBOARDING_SUBMIT_BUTTON_ID = 1010
        const val MEASUREMENT_PREFLIGHT_IN_VEHICLE_ID = 1011
        const val MEASUREMENT_PREFLIGHT_EVALUATE_BUTTON_ID = 1019
        const val MEASUREMENT_PREFLIGHT_OUTPUT_ID = 1020
        const val PENDING_SYNC_COUNTS_BUTTON_ID = 1021
        const val RETRY_PENDING_SYNC_BUTTON_ID = 1022
        const val PENDING_SYNC_SUMMARY_ID = 1023
        const val MEASUREMENT_RUN_START_BUTTON_ID = 1024
        const val MEASUREMENT_RUN_HEADER_ID = 1025
        const val MEASUREMENT_RUN_DETAIL_ID = 1026
        const val MEASUREMENT_RUN_PROGRESS_ID = 1027
        const val MEASUREMENT_RUN_RESULTS_ID = 1028
        const val MEASUREMENT_HISTORY_TITLE_ID = 1029
        const val MEASUREMENT_HISTORY_SYNC_ID = 1030
        const val MEASUREMENT_HISTORY_REFRESH_ID = 1031
        const val SETTINGS_MODE_TESTING_ID = 1032
        const val SETTINGS_MODE_FCC_ID = 1033
        const val SETTINGS_SUBMIT_BUTTON_ID = 1034
        const val SETTINGS_DEVICE_ID_VALUE_ID = 1035
        const val SETTINGS_APP_VERSION_VALUE_ID = 1036
        const val SETTINGS_COPY_DEVICE_ID_BUTTON_ID = 1037
        const val SETTINGS_COPY_APP_VERSION_BUTTON_ID = 1038
        const val MEASUREMENT_HISTORY_RUN_1_ID = 1040
        const val MEASUREMENT_HISTORY_RUN_2_ID = 1041
        const val MEASUREMENT_HISTORY_RUN_3_ID = 1042
        const val MEASUREMENT_HISTORY_STATE_KEY_ID = 1043
        const val MEASUREMENT_HISTORY_SYNC_STATE_KEY_ID = 1044
        const val MAP_HOME_RENDER_STATE_ID = 1045
        const val EXTRA_UI_MODE = "cellwatch.uiMode"
        const val UI_MODE_ONBOARDING_FLOW = "onboarding-flow"
        const val UI_MODE_SETTINGS_PROFILE_FLOW = "settings-profile-flow"
        const val UI_MODE_MEASUREMENT_START_FLOW = "measurement-start-flow"
        const val UI_MODE_PENDING_SYNC_FLOW = "pending-sync-flow"
        const val UI_MODE_MEASUREMENT_RUN_FLOW = "measurement-run-flow"
        const val UI_MODE_MEASUREMENT_HISTORY_FLOW = "measurement-history-flow"
        const val UI_MODE_MAP_HOME = "map-home"
        const val UI_MODE_MVP_MENU = "mvp-menu"
        const val UI_MODE_FULL_HARNESS = "full-harness"
        const val EXTRA_NEXT_UI_MODE_ON_SUCCESS = "cellwatch.nextUiModeOnSuccess"
        const val EXTRA_MEASUREMENT_START_AUTO_RUN = "cellwatch.measurementStart.autoRun"
        const val EXTRA_MEASUREMENT_RUN_AUTO_START = "cellwatch.measurementRun.autoStart"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_NETWORK_PATH = "cellwatch.measurementPreflight.overrideNetworkPath"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_LOCATION_PERMISSION = "cellwatch.measurementPreflight.overrideLocationPermission"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_RUNTIME_PROFILE = "cellwatch.measurementPreflight.overrideRuntimeProfile"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_COLLECTION_MODE = "cellwatch.measurementPreflight.overrideCollectionMode"
        private const val LOG_TAG = "AndroidTestHarness"
        private const val PERMISSION_REQUEST_CODE = 7001
        private const val HISTORY_PREFS = "cellwatch_history_snapshot"
        private const val HISTORY_LATENCY = "latency"
        private const val HISTORY_DOWNLOAD = "download"
        private const val HISTORY_UPLOAD = "upload"
        private const val HISTORY_UPLOADED = "uploaded"
        private const val HISTORY_DETAIL = "detail"
        private const val HISTORY_ENTRIES = "entries"
        private const val HISTORY_MAX_ENTRIES = 20
    }

    private data class HistorySnapshotEntry(
        val timestampMs: Long,
        val latency: String,
        val download: String,
        val upload: String,
        val uploaded: String,
        val detail: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
    )

    private enum class UiMode {
        FULL_HARNESS,
        ONBOARDING_FLOW,
        SETTINGS_PROFILE_FLOW,
        MEASUREMENT_START_FLOW,
        PENDING_SYNC_FLOW,
        MEASUREMENT_RUN_FLOW,
        MEASUREMENT_HISTORY_FLOW,
        MAP_HOME,
        MVP_MENU,
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (::statusText.isInitialized) {
            val envelope = smokeEnvelopeBuilder.failure(
                scenario = "unexpected-coroutine-failure",
                errorMessage = throwable.message,
            )
            runOnUiThread {
                statusText.text = smokeFormatter.format(envelope)
            }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + coroutineExceptionHandler)
    private lateinit var db: CellwatchDatabase
    private lateinit var measurementRepo: MeasurementRepositoryImpl
    private lateinit var latencyRepo: LatencyDataRepositoryImpl
    private lateinit var uploadDownloadRepo: UploadDownloadDataRepositoryImpl
    private lateinit var submissionRepo: FccSubmissionRepositoryImpl
    private lateinit var statusText: TextView
    private lateinit var msakModeButton: Button
    private lateinit var supabaseModeButton: Button
    private lateinit var onboardingNameInput: EditText
    private lateinit var onboardingPhoneInput: EditText
    private lateinit var onboardingEmailInput: EditText
    private lateinit var onboardingAckCheckbox: CheckBox
    private lateinit var measurementPreflightInVehicleCheckbox: CheckBox
    private lateinit var measurementPreflightOutput: TextView
    private lateinit var pendingSyncSummary: TextView
    private lateinit var measurementRunHeader: TextView
    private lateinit var measurementRunDetail: TextView
    private lateinit var measurementRunProgress: ProgressBar
    private lateinit var measurementRunResults: TextView
    private lateinit var measurementRunPrimaryButton: Button
    private lateinit var measurementHistoryTitle: TextView
    private lateinit var measurementHistorySync: TextView
    private lateinit var measurementHistoryRunsContainer: LinearLayout
    private lateinit var measurementHistoryStateKey: TextView
    private lateinit var measurementHistorySyncStateKey: TextView
    private lateinit var mapHomeRenderState: TextView

    private var syncDriver: AndroidTestSyncDriver? = null
    private var lastGroup: MeasurementGroup? = null
    @Volatile private var phase3RunInFlight: Boolean = false
    private var previousDefaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private val runtimeModeBridge = RuntimeModeUiBridge()
    private var selectedMsakMode: RuntimeMsakMode = RuntimeMsakMode.LOCAL
    private var selectedSupabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL
    private val allowRemoteSupabase: Boolean = System.getenv("CELLWATCH_ALLOW_REMOTE_SUPABASE") == "true"
    private val disableSupabaseSync: Boolean = BuildConfig.CELLWATCH_DISABLE_SUPABASE_SYNC
    private var runtimeProfile: RuntimeSyncMsakProfile =
        RuntimeProfileResolver.resolveProfile(resolveRuntimeProfileConfig())
    private val onboardingValidationUseCase = OnboardingValidationUseCase()
    private lateinit var onboardingPersistenceUseCase: OnboardingPersistenceUseCase
    private lateinit var onboardingViewModel: OnboardingProfileViewModel
    private lateinit var settingsViewModel: SettingsProfileViewModel
    private val measurementPreflightUseCase = MeasurementPreflightUseCase()
    private val measurementStartEnvironmentResolver = MeasurementStartPreflightEnvironmentResolver()
    private val measurementStartFlowController = MeasurementStartPreflightFlowController(
        environmentResolver = measurementStartEnvironmentResolver,
        flowViewModel = MeasurementStartPreflightFlowViewModel(
            useCase = measurementPreflightUseCase,
            uiPresenter = MeasurementStartPreflightUiPresenter(),
        ),
    )
    private val measurementRunViewController = MeasurementRunViewController()
    private val measurementRunUiPresenter = MeasurementRunUiPresenter()
    private val measurementResultReadModelUseCase = MeasurementResultReadModelUseCase()
    private val measurementHistoryStatusUseCase = MeasurementHistoryStatusUseCase()
    private val mapHomeViewController = MapHomeViewController()
    private val mapHomeMapInteractionController = MapHomeMapInteractionController(minHexGridZoom = 0.0)
    private val mapHomeFeatureViewController = MapHomeFeatureViewController()
    private var mapHomeMapView: MapView? = null
    private var mapHomePointAnnotations: PointAnnotationManager? = null
    private var mapHomeHexAnnotations: PointAnnotationManager? = null
    private var mapHomeMapIdleCaptured: Boolean = false
    private var onboardingUiRenderInProgress = false
    private var settingsUiRenderInProgress = false
    private val smokeEnvelopeBuilder = SyncSmokeEnvelopeBuilder()
    private val smokeFormatter = SyncSmokeResultFormatter()
    private var uiMode: UiMode = UiMode.FULL_HARNESS
    private var launchedWithExplicitUiMode: Boolean = false
    private var historyPendingMeasurements: Int? = null
    private var historyPendingSubmissions: Int? = null
    private var selectedHistoryTimestampMs: Long? = null
    private lateinit var settingsModeGroup: RadioGroup
    private lateinit var settingsModeTesting: RadioButton
    private lateinit var settingsModeFcc: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val explicitMode = intent?.getStringExtra(EXTRA_UI_MODE)
        launchedWithExplicitUiMode = explicitMode != null
        uiMode = if (explicitMode == UI_MODE_ONBOARDING_FLOW) {
            UiMode.ONBOARDING_FLOW
        } else if (explicitMode == UI_MODE_SETTINGS_PROFILE_FLOW) {
            UiMode.SETTINGS_PROFILE_FLOW
        } else if (explicitMode == UI_MODE_MEASUREMENT_START_FLOW) {
            UiMode.MEASUREMENT_START_FLOW
        } else if (explicitMode == UI_MODE_PENDING_SYNC_FLOW) {
            UiMode.PENDING_SYNC_FLOW
        } else if (explicitMode == UI_MODE_MEASUREMENT_RUN_FLOW) {
            UiMode.MEASUREMENT_RUN_FLOW
        } else if (explicitMode == UI_MODE_MEASUREMENT_HISTORY_FLOW) {
            UiMode.MEASUREMENT_HISTORY_FLOW
        } else if (explicitMode == UI_MODE_MVP_MENU) {
            UiMode.MVP_MENU
        } else if (explicitMode == UI_MODE_MAP_HOME) {
            UiMode.MAP_HOME
        } else if (explicitMode == UI_MODE_FULL_HARNESS) {
            UiMode.FULL_HARNESS
        } else {
            resolveDefaultUiMode()
        }
        SyncDiagnosticsRegistry.configure(
            SyncDiagnosticsConfig(
                level = SyncDiagnosticsLevel.fromString(BuildConfig.CELLWATCH_SYNC_DIAGNOSTICS_LEVEL),
                maxSampledErrorsPerReport = BuildConfig.CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES,
                includeCauseChain = BuildConfig.CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN,
            ),
        )
        installHarnessUncaughtExceptionHandler()
        initDataLayer()
        onboardingPersistenceUseCase = OnboardingPersistenceUseCase(
            AndroidOnboardingProfileStore(applicationContext),
        )
        onboardingViewModel = OnboardingProfileViewModel(
            validationUseCase = onboardingValidationUseCase,
            persistenceUseCase = onboardingPersistenceUseCase,
        )
        settingsViewModel = SettingsProfileViewModel(
            validationUseCase = onboardingValidationUseCase,
            persistenceUseCase = onboardingPersistenceUseCase,
        )
        if (!isOnboardingCompletePersisted()) {
            clearPersistedHistorySnapshots()
        }
        setContentView(buildUi())
        if (uiMode == UiMode.FULL_HARNESS || uiMode == UiMode.ONBOARDING_FLOW) {
            bindOnboardingInputs()
            applyOnboardingUiState(onboardingViewModel.loadPersistedProfile())
        }
        if (uiMode == UiMode.SETTINGS_PROFILE_FLOW) {
            bindSettingsInputs()
            applySettingsUiState(settingsViewModel.loadPersistedProfile())
        }
        requestHarnessRuntimePermissions()
    }

    override fun onDestroy() {
        mapHomeMapView?.onDestroy()
        mapHomeMapView = null
        mapHomePointAnnotations = null
        mapHomeHexAnnotations = null
        restoreDefaultUncaughtExceptionHandler()
        super.onDestroy()
        scope.cancel()
    }

    override fun onStart() {
        super.onStart()
        mapHomeMapView?.onStart()
    }

    override fun onStop() {
        mapHomeMapView?.onStop()
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return
        permissions.forEachIndexed { index, permission ->
            val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
            Log.d(LOG_TAG, "Runtime permission result: $permission granted=$granted")
        }
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

    private fun requestHarnessRuntimePermissions() {
        val wanted = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val missing = wanted.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            Log.d(LOG_TAG, "All harness runtime permissions already granted")
            return
        }
        Log.d(LOG_TAG, "Requesting harness runtime permissions: $missing")
        ActivityCompat.requestPermissions(
            this,
            missing.toTypedArray(),
            PERMISSION_REQUEST_CODE,
        )
    }

    private fun buildUi(): View {
        return when (uiMode) {
            UiMode.ONBOARDING_FLOW -> buildOnboardingOnlyUi()
            UiMode.SETTINGS_PROFILE_FLOW -> buildSettingsProfileOnlyUi()
            UiMode.MEASUREMENT_START_FLOW -> buildMeasurementStartOnlyUi()
            UiMode.PENDING_SYNC_FLOW -> buildPendingSyncOnlyUi()
            UiMode.MEASUREMENT_RUN_FLOW -> buildMeasurementRunOnlyUi()
            UiMode.MEASUREMENT_HISTORY_FLOW -> buildMeasurementHistoryOnlyUi()
            UiMode.MAP_HOME -> buildMapHomeFullScreenUi()
            UiMode.MVP_MENU -> buildMvpMenuUi()
            UiMode.FULL_HARNESS -> buildHarnessUi()
        }
    }

    private fun buildMapHomeFullScreenUi(): View {
        val historyEntries = loadHistoryEntries()
        val mapHomeState = mapHomeViewController.present(
            MapHomeInput(
                onboardingComplete = isOnboardingCompletePersisted(),
                recentRunCount = historyEntries.size,
                pendingCountsKnown = historyPendingMeasurements != null && historyPendingSubmissions != null,
                pendingMeasurements = historyPendingMeasurements ?: 0,
                pendingSubmissions = historyPendingSubmissions ?: 0,
            ),
        )

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F7F9FC"))
        }

        val mapContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(mapContainer)

        mapHomeMapView?.onStop()
        mapHomeMapView?.onDestroy()
        mapHomePointAnnotations = null
        mapHomeHexAnnotations = null
        mapHomeMapIdleCaptured = false
        mapHomeMapView = MapView(this).also { mapView ->
            mapView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            updateMapHomeRenderState("STYLE_LOADING")
            mapView.mapboxMap.loadStyle(Style.STANDARD) {
                updateMapHomeRenderState("STYLE_LOADED")
                val initialState = mapHomeMapInteractionController.currentState()
                applyInitialMapHomeCamera(historyEntries, initialState)
            }
            mapView.mapboxMap.addOnMapIdleListener {
                if (!mapHomeMapIdleCaptured) {
                    mapHomeMapIdleCaptured = true
                    updateMapHomeRenderState("MAP_IDLE")
                }
            }
            mapContainer.addView(mapView)
            mapContainer.postDelayed({
                if (::mapHomeRenderState.isInitialized && mapHomeRenderState.text == "STYLE_LOADING") {
                    updateMapHomeRenderState("MAP_TIMEOUT")
                }
            }, 20_000L)
        }

        mapHomeRenderState = TextView(this).apply {
            id = MAP_HOME_RENDER_STATE_ID
            visibility = View.GONE
            text = "STYLE_LOADING"
        }
        root.addView(mapHomeRenderState)

        val topCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#F8F8FA"),
                strokeColor = Color.parseColor("#D6DEE8"),
            )
        }
        val topParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP
            setMargins(24, 42, 24, 0)
        }
        val title = TextView(this).apply {
            text = mapHomeState.title
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
        }
        val subtitle = TextView(this).apply {
            text = mapHomeState.subtitle
            textSize = 15f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 6, 0, 0)
        }
        topCard.addView(title)
        topCard.addView(subtitle)
        root.addView(topCard, topParams)

        val controlsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#F8F8FA"),
                strokeColor = Color.parseColor("#D6DEE8"),
            )
        }
        val controlsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM
            setMargins(24, 0, 24, 24)
        }
        val mapSummary = TextView(this).apply {
            text = mapHomeState.mapPanelBody
            textSize = 13f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(2, 0, 2, 8)
        }
        val mapInteractionState = mapHomeMapInteractionController
            .reset()
            .let { mapHomeMapInteractionController.onZoomChanged(it.zoomLevel) }
        val initialFeatureState = mapHomeFeatureViewController
            .loadMeasurements(mapHomeSnapshotsForRendering(historyEntries))
            .let { mapHomeFeatureViewController.onZoomChanged(mapInteractionState.zoomLevel) }
        if (initialFeatureState.hasAnyLocationData) {
            mapSummary.text = "${mapHomeState.mapPanelBody}\n${initialFeatureState.summary}"
        }
        val overlayToggle = Button(this).apply {
            text = if (mapInteractionState.overlayMode == MapHomeOverlayMode.HEX_GRID) {
                "Overlay: Hex Grid"
            } else {
                "Overlay: Points"
            }
            styleSecondaryButton(this)
            setOnClickListener {
                val target = if (text.toString().contains("Hex")) {
                    MapHomeOverlayMode.POINTS
                } else {
                    MapHomeOverlayMode.HEX_GRID
                }
                val next = mapHomeMapInteractionController.setPreferredOverlayMode(target)
                text = if (next.overlayMode == MapHomeOverlayMode.HEX_GRID) {
                    "Overlay: Hex Grid"
                } else {
                    "Overlay: Points"
                }
                renderMapHomeFeatures(historyEntries, next, mapSummary, mapHomeState.mapPanelBody)
            }
        }
        val profileButton = Button(this).apply {
            text = "Settings"
            styleSecondaryButton(this)
            setOnClickListener {
                openUiMode(
                    UI_MODE_SETTINGS_PROFILE_FLOW,
                    EXTRA_NEXT_UI_MODE_ON_SUCCESS to UI_MODE_MAP_HOME,
                )
            }
        }
        val measureButton = Button(this).apply {
            text = "Measure"
            stylePrimaryButton(this)
            setOnClickListener {
                if (isOnboardingCompletePersisted()) {
                    openUiMode(
                        UI_MODE_MEASUREMENT_RUN_FLOW,
                        EXTRA_MEASUREMENT_RUN_AUTO_START to true,
                    )
                } else {
                    openUiMode(
                        UI_MODE_ONBOARDING_FLOW,
                        EXTRA_NEXT_UI_MODE_ON_SUCCESS to UI_MODE_MEASUREMENT_RUN_FLOW,
                        EXTRA_MEASUREMENT_RUN_AUTO_START to true,
                    )
                }
            }
        }
        val historyButton = Button(this).apply {
            text = "History & Sync Status"
            styleSecondaryButton(this)
            setOnClickListener { openUiMode(UI_MODE_MEASUREMENT_HISTORY_FLOW) }
        }
        val syncSummary = TextView(this).apply {
            text = mapHomeState.syncSummary
            textSize = 13f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 14, 16, 14)
            background = roundedCard(
                fillColor = Color.parseColor("#F2F8F5"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = mapHomeState.statusText
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 14, 16, 14)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        controlsCard.addView(mapSummary)
        controlsCard.addView(overlayToggle)
        controlsCard.addView(profileButton)
        controlsCard.addView(measureButton)
        controlsCard.addView(historyButton)
        controlsCard.addView(syncSummary)
        controlsCard.addView(statusText)
        root.addView(controlsCard, controlsParams)
        renderMapHomeFeatures(historyEntries, mapInteractionState, mapSummary, mapHomeState.mapPanelBody)
        applyInitialMapHomeCamera(historyEntries, mapInteractionState)
        return root
    }

    private fun buildMvpMenuUi(): ScrollView {
        val historyEntries = loadHistoryEntries()
        val mapHomeState = mapHomeViewController.present(
            MapHomeInput(
                onboardingComplete = isOnboardingCompletePersisted(),
                recentRunCount = historyEntries.size,
                pendingCountsKnown = historyPendingMeasurements != null && historyPendingSubmissions != null,
                pendingMeasurements = historyPendingMeasurements ?: 0,
                pendingSubmissions = historyPendingSubmissions ?: 0,
            ),
        )
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor("#F7F9FC"))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 64, 42, 42)
        }

        val title = TextView(this).apply {
            text = mapHomeState.title
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = mapHomeState.subtitle
            textSize = 16f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 20)
        }
        val mapPlaceholder = TextView(this).apply {
            text = mapHomeState.mapPanelTitle
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 10)
        }
        val mapContainer = FrameLayout(this).apply {
            val desiredHeight = (220 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                desiredHeight,
            )
            background = roundedCard(
                fillColor = Color.parseColor("#E9EEF5"),
                strokeColor = Color.parseColor("#C8DCEE"),
            )
            clipToOutline = true
        }
        mapHomeMapView?.onStop()
        mapHomeMapView?.onDestroy()
        mapHomePointAnnotations = null
        mapHomeHexAnnotations = null
        mapHomeMapIdleCaptured = false
        mapHomeMapView = MapView(this).also { mapView ->
            mapView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            updateMapHomeRenderState("STYLE_LOADING")
            mapView.mapboxMap.loadStyle(Style.STANDARD) {
                updateMapHomeRenderState("STYLE_LOADED")
                val initialState = mapHomeMapInteractionController.currentState()
                applyInitialMapHomeCamera(historyEntries, initialState)
            }
            mapView.mapboxMap.addOnMapIdleListener {
                if (!mapHomeMapIdleCaptured) {
                    mapHomeMapIdleCaptured = true
                    updateMapHomeRenderState("MAP_IDLE")
                }
            }
            mapContainer.addView(mapView)
            mapContainer.postDelayed({
                if (::mapHomeRenderState.isInitialized && mapHomeRenderState.text == "STYLE_LOADING") {
                    updateMapHomeRenderState("MAP_TIMEOUT")
                }
            }, 20_000L)
        }
        mapHomeRenderState = TextView(this).apply {
            id = MAP_HOME_RENDER_STATE_ID
            visibility = View.GONE
            text = "STYLE_LOADING"
        }
        val mapSummary = TextView(this).apply {
            text = mapHomeState.mapPanelBody
            textSize = 13f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(2, 10, 2, 8)
        }
        val mapInteractionState = mapHomeMapInteractionController
            .reset()
            .let { mapHomeMapInteractionController.onZoomChanged(it.zoomLevel) }
        val initialFeatureState = mapHomeFeatureViewController
            .loadMeasurements(mapHomeSnapshotsForRendering(historyEntries))
            .let { mapHomeFeatureViewController.onZoomChanged(mapInteractionState.zoomLevel) }
        if (initialFeatureState.hasAnyLocationData) {
            mapSummary.text = "${mapHomeState.mapPanelBody}\n${initialFeatureState.summary}"
        }
        val overlayToggle = Button(this).apply {
            text = if (mapInteractionState.overlayMode == MapHomeOverlayMode.HEX_GRID) {
                "Overlay: Hex Grid"
            } else {
                "Overlay: Points"
            }
            styleSecondaryButton(this)
            setOnClickListener {
                val target = if (text.toString().contains("Hex")) {
                    MapHomeOverlayMode.POINTS
                } else {
                    MapHomeOverlayMode.HEX_GRID
                }
                val next = mapHomeMapInteractionController.setPreferredOverlayMode(target)
                text = if (next.overlayMode == MapHomeOverlayMode.HEX_GRID) {
                    "Overlay: Hex Grid"
                } else {
                    "Overlay: Points"
                }
                renderMapHomeFeatures(historyEntries, next, mapSummary, mapHomeState.mapPanelBody)
            }
        }
        val profileButton = Button(this).apply {
            text = "Settings"
            styleSecondaryButton(this)
            setOnClickListener {
                openUiMode(
                    UI_MODE_SETTINGS_PROFILE_FLOW,
                    EXTRA_NEXT_UI_MODE_ON_SUCCESS to UI_MODE_MAP_HOME,
                )
            }
        }
        val measureButton = Button(this).apply {
            text = "Measure"
            stylePrimaryButton(this)
            setOnClickListener {
                if (isOnboardingCompletePersisted()) {
                    openUiMode(
                        UI_MODE_MEASUREMENT_RUN_FLOW,
                        EXTRA_MEASUREMENT_RUN_AUTO_START to true,
                    )
                } else {
                    openUiMode(
                        UI_MODE_ONBOARDING_FLOW,
                        EXTRA_NEXT_UI_MODE_ON_SUCCESS to UI_MODE_MEASUREMENT_RUN_FLOW,
                        EXTRA_MEASUREMENT_RUN_AUTO_START to true,
                    )
                }
            }
        }
        val historyButton = Button(this).apply {
            text = "History & Sync Status"
            styleSecondaryButton(this)
            setOnClickListener { openUiMode(UI_MODE_MEASUREMENT_HISTORY_FLOW) }
        }
        val syncSummary = TextView(this).apply {
            text = mapHomeState.syncSummary
            textSize = 13f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 14, 16, 14)
            background = roundedCard(
                fillColor = Color.parseColor("#F2F8F5"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = mapHomeState.statusText
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }
        content.addView(title)
        content.addView(subtitle)
        content.addView(mapPlaceholder)
        content.addView(mapContainer)
        content.addView(mapSummary)
        content.addView(overlayToggle)
        content.addView(buildCard(profileButton, measureButton, historyButton))
        content.addView(syncSummary)
        content.addView(mapHomeRenderState)
        content.addView(statusText)
        root.addView(content)
        renderMapHomeFeatures(historyEntries, mapInteractionState, mapSummary, mapHomeState.mapPanelBody)
        applyInitialMapHomeCamera(historyEntries, mapInteractionState)
        return root
    }

    private fun updateMapHomeRenderState(state: String) {
        if (::mapHomeRenderState.isInitialized) {
            mapHomeRenderState.text = state
        }
    }

    private fun applyInitialMapHomeCamera(
        entries: List<HistorySnapshotEntry>,
        interactionState: edu.gatech.cc.cellwatch.domain.maphome.MapHomeMapInteractionState,
    ) {
        val mapView = mapHomeMapView ?: return
        val featureState = mapHomeFeatureViewController
            .loadMeasurements(mapHomeSnapshotsForRendering(entries))
            .let { mapHomeFeatureViewController.onZoomChanged(interactionState.zoomLevel) }

        val (lat, lon, zoom) = if (featureState.hasAnyLocationData) {
            Triple(
                featureState.centerLatitude ?: 33.778462,
                featureState.centerLongitude ?: -84.390123,
                if (interactionState.overlayMode == MapHomeOverlayMode.HEX_GRID) 10.5 else 12.5,
            )
        } else {
            val location = resolveCurrentMapHomeLocation()
            Triple(location.first, location.second, 12.5)
        }
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(lon, lat))
                .zoom(zoom)
                .build(),
        )
    }

    private fun resolveCurrentMapHomeLocation(): Pair<Double, Double> {
        val defaultLat = System.getenv("CELLWATCH_MAP_SIM_LAT")?.toDoubleOrNull() ?: 33.778462
        val defaultLon = System.getenv("CELLWATCH_MAP_SIM_LON")?.toDoubleOrNull() ?: -84.390123
        val defaults = Pair(defaultLat, defaultLon)
        val hasLocationPermission = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).any { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasLocationPermission) {
            return defaults
        }
        val manager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return defaults
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        for (provider in providers) {
            runCatching {
                manager.getLastKnownLocation(provider)
            }.getOrNull()?.let { location ->
                return Pair(location.latitude, location.longitude)
            }
        }
        return defaults
    }

    private fun buildMeasurementHistoryOnlyUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.setBackgroundColor(Color.parseColor("#F7F9FC"))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 64, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Measurement History"
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Review your latest results and sync status."
            textSize = 16f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 20)
        }
        measurementHistoryTitle = TextView(this).apply {
            id = MEASUREMENT_HISTORY_TITLE_ID
            text = "Measurement details"
            textSize = 20f
            setTextColor(Color.parseColor("#0A2A43"))
        }
        val selectedDetail = TextView(this).apply {
            id = MEASUREMENT_RUN_RESULTS_ID
            textSize = 14f
            setTextColor(Color.parseColor("#2E4A62"))
            setPadding(16, 16, 16, 16)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8DCEE"),
            )
        }
        measurementRunResults = selectedDetail
        measurementHistorySync = TextView(this).apply {
            id = MEASUREMENT_HISTORY_SYNC_ID
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 16, 16, 16)
            background = roundedCard(
                fillColor = Color.parseColor("#F2F8F5"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }
        measurementHistoryStateKey = TextView(this).apply {
            id = MEASUREMENT_HISTORY_STATE_KEY_ID
            visibility = View.GONE
        }
        measurementHistorySyncStateKey = TextView(this).apply {
            id = MEASUREMENT_HISTORY_SYNC_STATE_KEY_ID
            visibility = View.GONE
        }
        val recentRunsTitle = TextView(this).apply {
            text = "Recent runs (newest first)"
            textSize = 16f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 8, 0, 4)
        }
        measurementHistoryRunsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val runsScroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                measurementHistoryRunsContainer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val runsScrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (220 * resources.displayMetrics.density).toInt(),
        )
        val refreshButton = Button(this).apply {
            id = MEASUREMENT_HISTORY_REFRESH_ID
            text = "Refresh Sync Status"
            stylePrimaryButton(this)
            setOnClickListener { refreshMeasurementHistorySync() }
        }
        val cancelButton = createBackToMapButton()
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = "Loading latest measurement history..."
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(
            buildCard(
                measurementHistoryTitle,
                recentRunsTitle,
                runsScroll,
                selectedDetail,
                measurementHistorySync,
                measurementHistoryStateKey,
                measurementHistorySyncStateKey,
                refreshButton,
                cancelButton,
            ),
        )
        content.addView(statusText)
        root.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        runsScroll.layoutParams = runsScrollParams
        renderMeasurementHistoryUi()
        refreshMeasurementHistorySync()
        return root
    }

    private fun buildHarnessUi(): ScrollView {
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor("#F2F2F2"))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 42, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Android Test App Harness"
            textSize = 24f
            setTextColor(Color.parseColor("#07416B"))
        }
        val subtitle = TextView(this).apply {
            text = "CellWatch runtime onboarding + phase 3 actions"
            textSize = 14f
            setTextColor(Color.parseColor("#5E8BAB"))
            setPadding(0, 6, 0, 18)
        }
        val seedAndMapSync = Button(this).apply {
            text = "Seed + Run Map-Start Sync"
            stylePrimaryButton(this)
            setOnClickListener { runSeedAndMapSync() }
        }
        val onboardingHeader = TextView(this).apply {
            text = "Onboarding Profile"
            textSize = 16f
            setTextColor(Color.parseColor("#07416B"))
        }
        onboardingNameInput = EditText(this).apply {
            id = ONBOARDING_NAME_INPUT_ID
            hint = "Full name"
            setSingleLine()
        }
        onboardingPhoneInput = EditText(this).apply {
            id = ONBOARDING_PHONE_INPUT_ID
            hint = "Phone (###-###-####)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine()
        }
        onboardingEmailInput = EditText(this).apply {
            id = ONBOARDING_EMAIL_INPUT_ID
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
        }
        onboardingAckCheckbox = CheckBox(this).apply {
            id = ONBOARDING_ACK_CHECKBOX_ID
            text = "I acknowledge FCC challenge sharing terms."
            setTextColor(Color.parseColor("#003618"))
        }
        val onboardingSubmitButton = Button(this).apply {
            id = ONBOARDING_SUBMIT_BUTTON_ID
            text = "Submit Onboarding"
            stylePrimaryButton(this)
            setOnClickListener { submitOnboarding() }
        }
        val measurementCompleteSync = Button(this).apply {
            text = "Run Measurement-Complete Sync"
            styleSecondaryButton(this)
            setOnClickListener { runMeasurementCompleteSync() }
        }
        val runMapSyncOnly = Button(this).apply {
            text = "Run Map-Start Sync (No Seed)"
            styleSecondaryButton(this)
            setOnClickListener { runMapSyncOnly() }
        }
        val showPendingCountsButton = Button(this).apply {
            id = PENDING_SYNC_COUNTS_BUTTON_ID
            text = "Show Pending Sync Counts"
            styleSecondaryButton(this)
            setOnClickListener { runShowPendingSyncCounts() }
        }
        val retryPendingSyncButton = Button(this).apply {
            id = RETRY_PENDING_SYNC_BUTTON_ID
            text = "Retry Pending Sync"
            styleSecondaryButton(this)
            setOnClickListener { runRetryPendingSync() }
        }
        pendingSyncSummary = TextView(this).apply {
            id = PENDING_SYNC_SUMMARY_ID
            text = "Pending sync status: not checked."
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 16, 16, 16)
            background = roundedCard(
                fillColor = Color.parseColor("#F2F8F5"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }
        val runSharedSliceButton = Button(this).apply {
            id = RUN_SHARED_SLICE_BUTTON_ID
            text = "Run Map-Start Shared Slice"
            styleSecondaryButton(this)
            setOnClickListener { runSharedSlice() }
        }
        msakModeButton = Button(this).apply {
            id = MSAK_MODE_BUTTON_ID
            styleModeButton(this)
            setOnClickListener { cycleMsakMode() }
        }
        supabaseModeButton = Button(this).apply {
            id = SUPABASE_MODE_BUTTON_ID
            styleModeButton(this)
            setOnClickListener { cycleSupabaseMode() }
        }
        val locateServersButton = Button(this).apply {
            text = "Select MSAK Servers (Shared Selector)"
            styleSecondaryButton(this)
            setOnClickListener { runSelectServers() }
        }
        val runPhase3SequenceButton = Button(this).apply {
            id = RUN_PHASE3_SEQUENCE_BUTTON_ID
            text = "Run Phase3 Sequence (Shared Orchestrator)"
            stylePrimaryButton(this)
            setOnClickListener { runPhase3Sequence() }
        }
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = "Ready. Local Supabase target is enforced by default."
            textSize = 14f
            setTextColor(Color.parseColor("#003618"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(
            buildCard(
                onboardingHeader,
                onboardingNameInput,
                onboardingPhoneInput,
                onboardingEmailInput,
                onboardingAckCheckbox,
                onboardingSubmitButton,
            ),
        )
        content.addView(buildCard(msakModeButton, supabaseModeButton))
        content.addView(
            buildCard(
                seedAndMapSync,
                measurementCompleteSync,
                runMapSyncOnly,
                showPendingCountsButton,
                retryPendingSyncButton,
                pendingSyncSummary,
                runSharedSliceButton,
                locateServersButton,
                runPhase3SequenceButton,
            ),
        )
        content.addView(statusText)
        refreshModeUi()
        root.addView(content)
        return root
    }

    private fun buildOnboardingOnlyUi(): ScrollView {
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor("#F7F9FC"))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 64, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Complete Your Profile"
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Tell us who you are before starting measurements."
            textSize = 16f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 28)
        }
        onboardingNameInput = EditText(this).apply {
            id = ONBOARDING_NAME_INPUT_ID
            hint = "Full name"
            setSingleLine()
        }
        onboardingPhoneInput = EditText(this).apply {
            id = ONBOARDING_PHONE_INPUT_ID
            hint = "Phone (###-###-####)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine()
        }
        onboardingEmailInput = EditText(this).apply {
            id = ONBOARDING_EMAIL_INPUT_ID
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
        }
        onboardingAckCheckbox = CheckBox(this).apply {
            id = ONBOARDING_ACK_CHECKBOX_ID
            text = "I acknowledge FCC challenge sharing terms."
            setTextColor(Color.parseColor("#003618"))
        }
        val onboardingSubmitButton = Button(this).apply {
            id = ONBOARDING_SUBMIT_BUTTON_ID
            text = "Save Profile"
            stylePrimaryButton(this)
            setOnClickListener { submitOnboarding() }
        }
        val cancelButton = createBackToMapButton()
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = "Complete the form and save your profile."
            textSize = 14f
            setTextColor(Color.parseColor("#003618"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(
            buildCard(
                onboardingNameInput,
                onboardingPhoneInput,
                onboardingEmailInput,
                onboardingAckCheckbox,
                onboardingSubmitButton,
                cancelButton,
            ),
        )
        content.addView(statusText)
        root.addView(content)
        return root
    }

    private fun buildSettingsProfileOnlyUi(): ScrollView {
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor("#F7F9FC"))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 64, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Profile Settings"
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Update profile details and collection mode."
            textSize = 16f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 20)
        }
        settingsModeTesting = RadioButton(this).apply {
            id = SETTINGS_MODE_TESTING_ID
            text = "Testing Mode"
            setTextColor(Color.parseColor("#0A2A43"))
        }
        settingsModeFcc = RadioButton(this).apply {
            id = SETTINGS_MODE_FCC_ID
            text = "FCC Challenge Mode"
            setTextColor(Color.parseColor("#0A2A43"))
        }
        settingsModeGroup = RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(settingsModeTesting)
            addView(settingsModeFcc)
        }
        onboardingNameInput = EditText(this).apply {
            id = ONBOARDING_NAME_INPUT_ID
            hint = "Full name"
            setSingleLine()
        }
        onboardingPhoneInput = EditText(this).apply {
            id = ONBOARDING_PHONE_INPUT_ID
            hint = "Phone (###-###-####)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine()
        }
        onboardingEmailInput = EditText(this).apply {
            id = ONBOARDING_EMAIL_INPUT_ID
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
        }
        onboardingAckCheckbox = CheckBox(this).apply {
            id = ONBOARDING_ACK_CHECKBOX_ID
            text = "I acknowledge FCC challenge sharing terms."
            setTextColor(Color.parseColor("#003618"))
        }
        val settingsSubmitButton = Button(this).apply {
            id = SETTINGS_SUBMIT_BUTTON_ID
            text = "Save Settings"
            stylePrimaryButton(this)
            setOnClickListener { submitSettings() }
        }
        val cancelButton = createBackToMapButton()
        val deviceIdValue = TextView(this).apply {
            id = SETTINGS_DEVICE_ID_VALUE_ID
            textSize = 14f
            setTextColor(Color.parseColor("#2E4A62"))
            text = resolveSettingsDeviceId()
        }
        val appVersionValue = TextView(this).apply {
            id = SETTINGS_APP_VERSION_VALUE_ID
            textSize = 14f
            setTextColor(Color.parseColor("#2E4A62"))
            text = resolveSettingsAppVersion()
        }
        val copyDeviceButton = Button(this).apply {
            id = SETTINGS_COPY_DEVICE_ID_BUTTON_ID
            text = "Copy Device ID"
            styleSecondaryButton(this)
            setOnClickListener {
                copyToClipboard("device id", deviceIdValue.text?.toString().orEmpty())
                statusText.setTextColor(Color.parseColor("#003618"))
                statusText.text = "Device ID copied."
            }
        }
        val copyVersionButton = Button(this).apply {
            id = SETTINGS_COPY_APP_VERSION_BUTTON_ID
            text = "Copy App Version"
            styleSecondaryButton(this)
            setOnClickListener {
                copyToClipboard("app version", appVersionValue.text?.toString().orEmpty())
                statusText.setTextColor(Color.parseColor("#003618"))
                statusText.text = "App version copied."
            }
        }
        val identityHeader = TextView(this).apply {
            text = "App Identity"
            textSize = 16f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 8, 0, 6)
        }
        val deviceIdLabel = TextView(this).apply {
            text = "Device ID"
            textSize = 12f
            setTextColor(Color.parseColor("#3B5D77"))
        }
        val appVersionLabel = TextView(this).apply {
            text = "App Version"
            textSize = 12f
            setTextColor(Color.parseColor("#3B5D77"))
        }
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = "Update your settings and save."
            textSize = 14f
            setTextColor(Color.parseColor("#003618"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(
            buildCard(
                settingsModeGroup,
                onboardingNameInput,
                onboardingPhoneInput,
                onboardingEmailInput,
                onboardingAckCheckbox,
                settingsSubmitButton,
                cancelButton,
                identityHeader,
                deviceIdLabel,
                deviceIdValue,
                copyDeviceButton,
                appVersionLabel,
                appVersionValue,
                copyVersionButton,
            ),
        )
        content.addView(statusText)
        root.addView(content)
        return root
    }

    private fun buildMeasurementStartOnlyUi(): ScrollView {
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor("#F7F9FC"))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 64, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Measurement Start Preflight"
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Confirm your setup before starting a measurement."
            textSize = 16f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 20)
        }
        measurementPreflightInVehicleCheckbox = CheckBox(this).apply {
            id = MEASUREMENT_PREFLIGHT_IN_VEHICLE_ID
            text = "In moving vehicle"
            isChecked = false
        }
        val evaluateButton = Button(this).apply {
            id = MEASUREMENT_PREFLIGHT_EVALUATE_BUTTON_ID
            text = "Start Measurement"
            stylePrimaryButton(this)
            setOnClickListener { evaluateMeasurementStartPreflightFromUi() }
        }
        val cancelButton = createBackToMapButton()
        measurementPreflightOutput = TextView(this).apply {
            id = MEASUREMENT_PREFLIGHT_OUTPUT_ID
            text = "Tap Go to check readiness."
            textSize = 14f
            setTextColor(Color.parseColor("#003618"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(
            buildCard(
                measurementPreflightInVehicleCheckbox,
                evaluateButton,
                cancelButton,
            ),
        )
        content.addView(measurementPreflightOutput)
        root.addView(content)
        return root
    }

    private fun buildPendingSyncOnlyUi(): ScrollView {
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor("#F7F9FC"))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 64, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Sync Pending Uploads"
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Check pending uploads and retry sync."
            textSize = 16f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 20)
        }
        val backToMapButton = createBackToMapButton()
        val showPendingCountsButton = Button(this).apply {
            id = PENDING_SYNC_COUNTS_BUTTON_ID
            text = "Check Pending Uploads"
            styleSecondaryButton(this)
            setOnClickListener { runShowPendingSyncCounts() }
        }
        val retryPendingSyncButton = Button(this).apply {
            id = RETRY_PENDING_SYNC_BUTTON_ID
            text = "Retry Sync"
            stylePrimaryButton(this)
            setOnClickListener { runRetryPendingSync() }
        }
        pendingSyncSummary = TextView(this).apply {
            id = PENDING_SYNC_SUMMARY_ID
            text = "Pending sync status: not checked."
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 16, 16, 16)
            background = roundedCard(
                fillColor = Color.parseColor("#F2F8F5"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = "Tap Check Pending Uploads to begin."
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(backToMapButton)
        content.addView(buildCard(showPendingCountsButton, retryPendingSyncButton, pendingSyncSummary, statusText))
        root.addView(content)
        return root
    }

    private fun buildMeasurementRunOnlyUi(): ScrollView {
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor("#F7F9FC"))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 64, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Run Measurement"
            textSize = 28f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Start a measurement and follow live progress."
            textSize = 16f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 0, 0, 20)
        }
        val startButton = Button(this).apply {
            id = MEASUREMENT_RUN_START_BUTTON_ID
            text = "Start Measurement"
            stylePrimaryButton(this)
            setOnClickListener { runPhase3Sequence() }
        }
        val cancelButton = createBackToMapButton()
        measurementRunPrimaryButton = startButton
        measurementRunHeader = TextView(this).apply {
            id = MEASUREMENT_RUN_HEADER_ID
            text = "Ready to start."
            textSize = 20f
            setTextColor(Color.parseColor("#0A2A43"))
            setPadding(0, 6, 0, 6)
        }
        measurementRunProgress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal,
        ).apply {
            id = MEASUREMENT_RUN_PROGRESS_ID
            max = 100
            progress = 0
        }
        measurementRunDetail = TextView(this).apply {
            id = MEASUREMENT_RUN_DETAIL_ID
            text = "Tap Start Measurement to begin."
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(0, 8, 0, 0)
        }
        measurementRunResults = TextView(this).apply {
            id = MEASUREMENT_RUN_RESULTS_ID
            text = "Latency: --\nDownload: --\nUpload: --\nUploaded: In progress.\n\nMeasurement in progress."
            textSize = 14f
            setTextColor(Color.parseColor("#2E4A62"))
            setPadding(16, 16, 16, 16)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8DCEE"),
            )
            visibility = View.GONE
        }
        statusText = TextView(this).apply {
            id = STATUS_TEXT_VIEW_ID
            text = "Tap Start Measurement to begin."
            textSize = 14f
            setTextColor(Color.parseColor("#3B5D77"))
            setPadding(16, 20, 16, 20)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#C8E3CC"),
            )
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(
            buildCard(
                measurementRunHeader,
                measurementRunProgress,
                measurementRunDetail,
                measurementRunResults,
                startButton,
                cancelButton,
            ),
        )
        if (intent?.getBooleanExtra(EXTRA_MEASUREMENT_RUN_AUTO_START, false) == true) {
            content.post { runPhase3Sequence() }
        }
        root.addView(content)
        return root
    }

    private fun resolveRuntimeProfileConfig(): RuntimeProfileConfig {
        val baseConfig = resolveRuntimeProfileConfigFromProperties(
            msakMode = selectedMsakMode,
            supabaseMode = selectedSupabaseMode,
            allowRemoteSupabase = allowRemoteSupabase,
        )
        val draft = RuntimeOnboardingContract.fromConfig(baseConfig)
        return draft.toRuntimeProfileConfig(
            allowRemoteSupabase = allowRemoteSupabase,
            strictSupabaseConfig = baseConfig.strictSupabaseConfig,
            userAgent = "android-test-app-runtime-profile",
        )
    }

    private fun createBackToMapButton(): Button {
        return Button(this).apply {
            text = "Cancel"
            styleSecondaryButton(this)
            setOnClickListener { openUiMode(UI_MODE_MAP_HOME) }
        }
    }

    private fun cycleMsakMode() {
        selectedMsakMode = runtimeModeBridge.nextMsakMode(selectedMsakMode)
        applyRuntimeModeChange()
    }

    private fun cycleSupabaseMode() {
        selectedSupabaseMode = runtimeModeBridge.nextSupabaseMode(selectedSupabaseMode)
        applyRuntimeModeChange()
    }

    private fun applyRuntimeModeChange() {
        syncDriver = null
        refreshModeUi()
        val config = resolveRuntimeProfileConfig()
        val resolution = runtimeModeBridge.resolve(config)
        val snapshot = resolution.snapshot
        if (snapshot == null) {
            val invalidMessage = "Runtime mode invalid: ${resolution.errorMessage ?: "unknown resolution failure"}"
            Log.e(LOG_TAG, invalidMessage)
            statusText.text = invalidMessage
            return
        }
        runtimeProfile = RuntimeProfileResolver.resolveProfile(config)
        val message =
            "Runtime mode updated.\n" +
                "MSAK=${runtimeModeBridge.msakModeLabel(selectedMsakMode)}, Supabase=${runtimeModeBridge.supabaseModeLabel(selectedSupabaseMode)}, remoteAllowed=$allowRemoteSupabase\n" +
                "msakLocalHost=${snapshot.msakLocalServerHost ?: "n/a"}\n" +
                "supabaseUrl=${snapshot.supabaseUrl}"
        Log.d(LOG_TAG, message)
        statusText.text = message
    }

    private fun refreshModeUi() {
        msakModeButton.text = "MSAK Mode: ${runtimeModeBridge.msakModeLabel(selectedMsakMode)} (tap to cycle)"
        supabaseModeButton.text = "Supabase Mode: ${runtimeModeBridge.supabaseModeLabel(selectedSupabaseMode)} (tap to cycle)"
    }

    private fun submitOnboarding() {
        syncOnboardingVmWithCurrentInputs()
        val submission: OnboardingProfileSubmission = onboardingViewModel.submit()
        applyOnboardingUiState(submission.state)
        statusText.text = submission.statusText
        if (submission.success && uiMode == UiMode.ONBOARDING_FLOW) {
            val nextMode = intent?.getStringExtra(EXTRA_NEXT_UI_MODE_ON_SUCCESS)
            if (!nextMode.isNullOrBlank()) {
                openUiMode(
                    nextMode,
                    EXTRA_MEASUREMENT_START_AUTO_RUN to intent?.getBooleanExtra(EXTRA_MEASUREMENT_START_AUTO_RUN, false),
                    EXTRA_MEASUREMENT_RUN_AUTO_START to intent?.getBooleanExtra(EXTRA_MEASUREMENT_RUN_AUTO_START, false),
                )
                finish()
            } else if (!launchedWithExplicitUiMode) {
                openUiMode(UI_MODE_MAP_HOME)
                finish()
            }
        }
    }

    private fun submitSettings() {
        syncSettingsVmWithCurrentInputs()
        val submission: SettingsProfileSubmission = settingsViewModel.submit()
        applySettingsUiState(submission.state)
        statusText.text = submission.statusText
        if (submission.success && uiMode == UiMode.SETTINGS_PROFILE_FLOW) {
            intent?.getStringExtra(EXTRA_NEXT_UI_MODE_ON_SUCCESS)?.let { nextMode ->
                openUiMode(nextMode)
                finish()
            }
        }
    }

    private fun resolveDefaultUiMode(): UiMode {
        val decision = AppLaunchRoutingUseCase().resolve(
            AppLaunchRoutingInput(
                onboardingComplete = isOnboardingCompletePersisted(),
                runtimeProfileReady = true,
            ),
        )
        return when (decision.destinationToken) {
            AppLaunchRoutingUseCase.DESTINATION_ONBOARDING_FLOW -> UiMode.ONBOARDING_FLOW
            AppLaunchRoutingUseCase.DESTINATION_MAP_HOME -> UiMode.MAP_HOME
            else -> UiMode.ONBOARDING_FLOW
        }
    }

    private fun isOnboardingCompletePersisted(): Boolean {
        return AndroidOnboardingProfileStore(applicationContext)
            .loadProfile()
            ?.onboardingComplete == true
    }

    private fun openUiMode(mode: String, vararg extras: Pair<String, Any?>) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_UI_MODE, mode)
            extras.forEach { (key, value) ->
                when (value) {
                    is String -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    null -> Unit
                }
            }
        })
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun resolveSettingsDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            .orEmpty()
        return androidId.ifBlank { "unavailable" }
    }

    private fun resolveSettingsAppVersion(): String {
        return runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            val versionName = info.versionName ?: BuildConfig.VERSION_NAME
            val versionCode = PackageInfoCompat.getLongVersionCode(info)
            "$versionName ($versionCode)"
        }.getOrElse { BuildConfig.VERSION_NAME }
    }

    private fun bindOnboardingInputs() {
        onboardingNameInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (onboardingUiRenderInProgress) return
                    applyOnboardingUiState(onboardingViewModel.onNameChanged(s?.toString().orEmpty()))
                }
            },
        )
        onboardingPhoneInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (onboardingUiRenderInProgress) return
                    applyOnboardingUiState(onboardingViewModel.onPhoneChanged(s?.toString().orEmpty()))
                }
            },
        )
        onboardingEmailInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (onboardingUiRenderInProgress) return
                    applyOnboardingUiState(onboardingViewModel.onEmailChanged(s?.toString().orEmpty()))
                }
            },
        )
        onboardingAckCheckbox.setOnCheckedChangeListener { _, checked ->
            if (onboardingUiRenderInProgress) return@setOnCheckedChangeListener
            applyOnboardingUiState(onboardingViewModel.onAcknowledgementChanged(checked))
        }
    }

    private fun bindSettingsInputs() {
        onboardingNameInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (settingsUiRenderInProgress) return
                    applySettingsUiState(settingsViewModel.onNameChanged(s?.toString().orEmpty()))
                }
            },
        )
        onboardingPhoneInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (settingsUiRenderInProgress) return
                    applySettingsUiState(settingsViewModel.onPhoneChanged(s?.toString().orEmpty()))
                }
            },
        )
        onboardingEmailInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (settingsUiRenderInProgress) return
                    applySettingsUiState(settingsViewModel.onEmailChanged(s?.toString().orEmpty()))
                }
            },
        )
        onboardingAckCheckbox.setOnCheckedChangeListener { _, checked ->
            if (settingsUiRenderInProgress) return@setOnCheckedChangeListener
            applySettingsUiState(settingsViewModel.onAcknowledgementChanged(checked))
        }
        settingsModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (settingsUiRenderInProgress) return@setOnCheckedChangeListener
            val mode = if (checkedId == SETTINGS_MODE_FCC_ID) CollectionMode.FCC_CHALLENGE else CollectionMode.TESTING
            applySettingsUiState(settingsViewModel.onCollectionModeChanged(mode))
        }
    }

    private fun syncOnboardingVmWithCurrentInputs() {
        onboardingViewModel.onNameChanged(onboardingNameInput.text?.toString().orEmpty())
        onboardingViewModel.onPhoneChanged(onboardingPhoneInput.text?.toString().orEmpty())
        onboardingViewModel.onEmailChanged(onboardingEmailInput.text?.toString().orEmpty())
        onboardingViewModel.onAcknowledgementChanged(onboardingAckCheckbox.isChecked)
    }

    private fun applyOnboardingUiState(state: OnboardingProfileUiState) {
        onboardingUiRenderInProgress = true
        try {
            if (onboardingNameInput.text?.toString() != state.name) {
                onboardingNameInput.setText(state.name)
                onboardingNameInput.setSelection(onboardingNameInput.text.length)
            }
            if (onboardingPhoneInput.text?.toString() != state.phone) {
                onboardingPhoneInput.setText(state.phone)
                onboardingPhoneInput.setSelection(onboardingPhoneInput.text.length)
            }
            if (onboardingEmailInput.text?.toString() != state.email) {
                onboardingEmailInput.setText(state.email)
                onboardingEmailInput.setSelection(onboardingEmailInput.text.length)
            }
            if (onboardingAckCheckbox.isChecked != state.fccAcknowledged) {
                onboardingAckCheckbox.isChecked = state.fccAcknowledged
            }
            if (uiMode == UiMode.ONBOARDING_FLOW) {
                statusText.text = state.feedbackMessage
                statusText.setTextColor(
                    if (state.feedbackIsError) Color.parseColor("#A82329") else Color.parseColor("#003618"),
                )
            } else {
                statusText.setTextColor(Color.parseColor("#003618"))
            }
        } finally {
            onboardingUiRenderInProgress = false
        }
    }

    private fun syncSettingsVmWithCurrentInputs() {
        val mode = if (settingsModeGroup.checkedRadioButtonId == SETTINGS_MODE_FCC_ID) {
            CollectionMode.FCC_CHALLENGE
        } else {
            CollectionMode.TESTING
        }
        settingsViewModel.onCollectionModeChanged(mode)
        settingsViewModel.onNameChanged(onboardingNameInput.text?.toString().orEmpty())
        settingsViewModel.onPhoneChanged(onboardingPhoneInput.text?.toString().orEmpty())
        settingsViewModel.onEmailChanged(onboardingEmailInput.text?.toString().orEmpty())
        settingsViewModel.onAcknowledgementChanged(onboardingAckCheckbox.isChecked)
    }

    private fun applySettingsUiState(state: SettingsProfileUiState) {
        settingsUiRenderInProgress = true
        try {
            val desiredCheckedId = if (state.collectionMode == CollectionMode.FCC_CHALLENGE) {
                SETTINGS_MODE_FCC_ID
            } else {
                SETTINGS_MODE_TESTING_ID
            }
            if (settingsModeGroup.checkedRadioButtonId != desiredCheckedId) {
                settingsModeGroup.check(desiredCheckedId)
            }
            if (onboardingNameInput.text?.toString() != state.name) {
                onboardingNameInput.setText(state.name)
                onboardingNameInput.setSelection(onboardingNameInput.text.length)
            }
            if (onboardingPhoneInput.text?.toString() != state.phone) {
                onboardingPhoneInput.setText(state.phone)
                onboardingPhoneInput.setSelection(onboardingPhoneInput.text.length)
            }
            if (onboardingEmailInput.text?.toString() != state.email) {
                onboardingEmailInput.setText(state.email)
                onboardingEmailInput.setSelection(onboardingEmailInput.text.length)
            }
            if (onboardingAckCheckbox.isChecked != state.fccAcknowledged) {
                onboardingAckCheckbox.isChecked = state.fccAcknowledged
            }
            statusText.text = state.feedbackMessage
            statusText.setTextColor(
                if (state.feedbackIsError) Color.parseColor("#A82329") else Color.parseColor("#003618"),
            )
        } finally {
            settingsUiRenderInProgress = false
        }
    }

    private fun runSeedAndMapSync() {
        scope.launch {
            phase3RunInFlight = true
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

    private fun runShowPendingSyncCounts() {
        scope.launch {
            runCatching {
                val counts = getSyncDriver().getPendingSyncCounts()
                pendingSyncSummary.text = "Pending uploads: ${counts.total} item(s)"
                pendingSyncSummary.setTextColor(Color.parseColor("#1C6E3A"))
                val debugText = "Pending sync counts:\nmeasurements=${counts.measurements}\nsubmissions=${counts.submissions}\ntotal=${counts.total}"
                val userText = "Pending uploads found: ${counts.total}."
                debugText to userText
            }.onSuccess { (debugText, userText) ->
                statusText.text = if (uiMode == UiMode.PENDING_SYNC_FLOW) userText else debugText
                statusText.setTextColor(Color.parseColor("#1C6E3A"))
            }
                .onFailure {
                    pendingSyncSummary.text = "Unable to load pending uploads."
                    pendingSyncSummary.setTextColor(Color.parseColor("#A82329"))
                    val envelope = smokeEnvelopeBuilder.failure(
                        scenario = "pending-sync-counts",
                        errorMessage = it.message,
                    )
                    statusText.text = if (uiMode == UiMode.PENDING_SYNC_FLOW) {
                        "Unable to load pending uploads."
                    } else {
                        smokeFormatter.format(envelope)
                    }
                    statusText.setTextColor(Color.parseColor("#A82329"))
                }
        }
    }

    private fun runRetryPendingSync() {
        scope.launch {
            runCatching {
                val summary = getSyncDriver().runPendingSync()
                if (summary == null) {
                    val fallbackCounts = getSyncDriver().getPendingSyncCounts()
                    pendingSyncSummary.text = "Sync status unknown. Pending uploads: ${fallbackCounts.total}"
                    pendingSyncSummary.setTextColor(Color.parseColor("#A06B00"))
                    return@runCatching "Retry pending sync returned no summary.\npending total=${fallbackCounts.total}" to
                        "Sync status unknown. Pending uploads: ${fallbackCounts.total}."
                }
                val statusLabel = when (summary.status) {
                    edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.SUCCEEDED -> "Sync complete. No pending uploads."
                    edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.PARTIAL_FAILURE -> "Sync partially complete. Some uploads are still pending."
                    edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.FAILED -> "Sync failed. Check connection and retry."
                    edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.PENDING -> "Sync still pending."
                    edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.IDLE -> "No pending uploads."
                    edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.IN_PROGRESS -> "Sync in progress."
                }
                pendingSyncSummary.text = statusLabel
                pendingSyncSummary.setTextColor(
                    when (summary.status) {
                        edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.SUCCEEDED,
                        edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.IDLE,
                        -> Color.parseColor("#1C6E3A")

                        edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.PARTIAL_FAILURE,
                        edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.PENDING,
                        edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.IN_PROGRESS,
                        -> Color.parseColor("#A06B00")

                        edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus.FAILED -> Color.parseColor("#A82329")
                    },
                )
                val debugText = "Retry pending sync:\nstatus=${summary.status}\n" +
                    "before(total=${summary.before.total}, measurements=${summary.before.measurements}, submissions=${summary.before.submissions})\n" +
                    "after(total=${summary.after.total}, measurements=${summary.after.measurements}, submissions=${summary.after.submissions})\n" +
                    summary.userMessage
                debugText to statusLabel
            }.onSuccess { (debugText, userText) ->
                statusText.text = if (uiMode == UiMode.PENDING_SYNC_FLOW) userText else debugText
            }
                .onFailure {
                    pendingSyncSummary.text = "Retry failed. Check connection and retry."
                    pendingSyncSummary.setTextColor(Color.parseColor("#A82329"))
                    val envelope = smokeEnvelopeBuilder.failure(
                        scenario = "retry-pending-sync",
                        errorMessage = it.message,
                    )
                    statusText.text = if (uiMode == UiMode.PENDING_SYNC_FLOW) {
                        "Retry failed. Check connection and retry."
                    } else {
                        smokeFormatter.format(envelope)
                    }
                    statusText.setTextColor(Color.parseColor("#A82329"))
                }
        }
    }

    private fun renderMeasurementHistoryUi() {
        scope.launch {
            runCatching {
                val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
                val model = measurementHistoryStatusUseCase.present(
                    MeasurementHistoryStatusInput(
                        recentMeasurements = emptyList(),
                        pendingMeasurements = historyPendingMeasurements,
                        pendingSubmissions = historyPendingSubmissions,
                        fallbackLatencyText = prefs.getString(HISTORY_LATENCY, null),
                        fallbackDownloadText = prefs.getString(HISTORY_DOWNLOAD, null),
                        fallbackUploadText = prefs.getString(HISTORY_UPLOAD, null),
                        fallbackUploadedText = prefs.getString(HISTORY_UPLOADED, null),
                        fallbackDetailText = prefs.getString(HISTORY_DETAIL, null),
                    ),
                )
                renderHistoryEntries(loadHistoryEntries(), model.detail)
                measurementHistorySync.text = model.syncSummary
                measurementHistoryStateKey.text = model.stateKey.name
                measurementHistorySyncStateKey.text = model.syncStateKey.name
                statusText.setTextColor(Color.parseColor("#3B5D77"))
                statusText.text = if (loadHistoryEntries().isNotEmpty()) {
                    "Measurement history loaded."
                } else {
                    "No measurements found yet."
                }
            }.onFailure { error ->
                renderHistoryEntries(loadHistoryEntries(), "Run your first measurement to populate history.")
                measurementHistorySync.text = "Sync status unknown. Tap refresh."
                measurementHistoryStateKey.text = "EMPTY"
                measurementHistorySyncStateKey.text = "UNKNOWN"
                statusText.setTextColor(Color.parseColor("#A82329"))
                statusText.text = "Unable to load history: ${error.message ?: "unknown error"}"
            }
        }
    }

    private fun persistLatestHistorySnapshot(
        latency: String,
        download: String,
        upload: String,
        uploaded: String,
        detail: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        val resolvedLocation = if (latitude == null || longitude == null) {
            resolveCurrentMapHomeLocation()
        } else {
            null
        }
        val resolvedLatitude = latitude ?: resolvedLocation?.first
        val resolvedLongitude = longitude ?: resolvedLocation?.second
        val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val updatedEntries = buildList {
            add(
                HistorySnapshotEntry(
                    timestampMs = System.currentTimeMillis(),
                    latency = latency,
                    download = download,
                    upload = upload,
                    uploaded = uploaded,
                    detail = detail,
                    latitude = resolvedLatitude,
                    longitude = resolvedLongitude,
                ),
            )
            addAll(loadHistoryEntries())
        }.take(HISTORY_MAX_ENTRIES)
        prefs.edit()
            .putString(HISTORY_LATENCY, latency)
            .putString(HISTORY_DOWNLOAD, download)
            .putString(HISTORY_UPLOAD, upload)
            .putString(HISTORY_UPLOADED, uploaded)
            .putString(HISTORY_DETAIL, detail)
            .putString(HISTORY_ENTRIES, encodeHistoryEntries(updatedEntries))
            .apply()
    }

    private fun loadHistoryEntries(): List<HistorySnapshotEntry> {
        val raw = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
            .getString(HISTORY_ENTRIES, null)
            .orEmpty()
            .trim()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val row = json.optJSONObject(i) ?: continue
                    add(
                        HistorySnapshotEntry(
                            timestampMs = row.optLong("timestampMs"),
                            latency = row.optString("latency"),
                            download = row.optString("download"),
                            upload = row.optString("upload"),
                            uploaded = row.optString("uploaded"),
                            detail = row.optString("detail"),
                            latitude = if (row.has("latitude") && !row.isNull("latitude")) {
                                row.optDouble("latitude")
                            } else {
                                null
                            },
                            longitude = if (row.has("longitude") && !row.isNull("longitude")) {
                                row.optDouble("longitude")
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun clearPersistedHistorySnapshots() {
        getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
            .edit()
            .remove(HISTORY_LATENCY)
            .remove(HISTORY_DOWNLOAD)
            .remove(HISTORY_UPLOAD)
            .remove(HISTORY_UPLOADED)
            .remove(HISTORY_DETAIL)
            .remove(HISTORY_ENTRIES)
            .apply()
        selectedHistoryTimestampMs = null
    }

    private fun encodeHistoryEntries(entries: List<HistorySnapshotEntry>): String {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject().apply {
                    put("timestampMs", entry.timestampMs)
                    put("latency", entry.latency)
                    put("download", entry.download)
                    put("upload", entry.upload)
                    put("uploaded", entry.uploaded)
                    put("detail", entry.detail)
                    entry.latitude?.let { put("latitude", it) }
                    entry.longitude?.let { put("longitude", it) }
                },
            )
        }
        return json.toString()
    }

    private fun List<HistorySnapshotEntry>.toMapHomeLocationSnapshots(): List<MapHomeMeasurementLocationSnapshot> {
        return mapIndexed { index, entry ->
            MapHomeMeasurementLocationSnapshot(
                id = "history-${entry.timestampMs}-$index",
                title = "Run ${index + 1}",
                timestampMs = entry.timestampMs,
                latitude = entry.latitude ?: Double.NaN,
                longitude = entry.longitude ?: Double.NaN,
            )
        }
    }

    private fun mapHomeSnapshotsForRendering(entries: List<HistorySnapshotEntry>): List<MapHomeMeasurementLocationSnapshot> {
        val snapshots = entries.toMapHomeLocationSnapshots()
        if (snapshots.isNotEmpty()) return snapshots
        val (latitude, longitude) = resolveCurrentMapHomeLocation()
        return listOf(
            MapHomeMeasurementLocationSnapshot(
                id = "current-location",
                title = "Current location",
                timestampMs = Clock.System.now().toEpochMilliseconds(),
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    private fun renderMapHomeFeatures(
        entries: List<HistorySnapshotEntry>,
        interactionState: edu.gatech.cc.cellwatch.domain.maphome.MapHomeMapInteractionState,
        mapSummaryView: TextView,
        fallbackSummary: String,
    ) {
        val mapView = mapHomeMapView ?: return
        val featureState = mapHomeFeatureViewController
            .loadMeasurements(mapHomeSnapshotsForRendering(entries))
            .let { mapHomeFeatureViewController.onZoomChanged(interactionState.zoomLevel) }

        mapSummaryView.text = if (featureState.hasAnyLocationData) {
            "$fallbackSummary\n${featureState.summary}"
        } else {
            fallbackSummary
        }

        val pointManager = mapHomePointAnnotations ?: mapView.annotations.createPointAnnotationManager().also {
            mapHomePointAnnotations = it
        }
        val hexManager = mapHomeHexAnnotations ?: mapView.annotations.createPointAnnotationManager().also {
            mapHomeHexAnnotations = it
        }
        if (interactionState.overlayMode == MapHomeOverlayMode.POINTS) {
            hexManager.deleteAll()
            pointManager.deleteAll()
            pointManager.create(
                featureState.points.map { point ->
                    PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(point.longitude, point.latitude))
                        .withIconImage("marker-15")
                        .withIconSize(1.6)
                        .withTextField(point.title)
                },
            )
        } else {
            pointManager.deleteAll()
            hexManager.deleteAll()
            hexManager.create(
                featureState.hexCells.map { cell ->
                    PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(cell.centerLongitude, cell.centerLatitude))
                        .withIconImage("circle-15")
                        .withIconSize(1.2)
                        .withTextField(cell.measurementCount.toString())
                },
            )
        }
    }

    private fun renderHistoryEntries(entries: List<HistorySnapshotEntry>, fallbackDetail: String) {
        measurementHistoryRunsContainer.removeAllViews()
        val sorted = entries.sortedByDescending { it.timestampMs }.take(5)
        if (sorted.isEmpty()) {
            measurementHistoryTitle.text = "Measurement details"
            measurementRunResults.text = fallbackDetail
            measurementHistoryRunsContainer.addView(
                TextView(this).apply {
                    text = "No recent runs yet."
                    textSize = 14f
                    setTextColor(Color.parseColor("#3B5D77"))
                    setPadding(8, 8, 8, 8)
                },
            )
            return
        }
        val preferredTimestamp = selectedHistoryTimestampMs ?: sorted.first().timestampMs
        selectedHistoryTimestampMs = preferredTimestamp
        sorted.forEachIndexed { index, entry ->
            val rowId = when (index) {
                0 -> MEASUREMENT_HISTORY_RUN_1_ID
                1 -> MEASUREMENT_HISTORY_RUN_2_ID
                2 -> MEASUREMENT_HISTORY_RUN_3_ID
                else -> View.generateViewId()
            }
            val summary =
                "Run ${index + 1}: ${entry.latency} latency, ${entry.download} download, ${entry.upload} upload"
            val isSelected = entry.timestampMs == selectedHistoryTimestampMs
            val row = Button(this).apply {
                id = rowId
                text = summary
                setAllCaps(false)
                textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                textSize = 13f
                setPadding(14, 12, 14, 12)
                setTextColor(Color.parseColor("#0A2A43"))
                background = roundedCard(
                    fillColor = if (isSelected) Color.parseColor("#E8F2FC") else Color.parseColor("#FFFFFF"),
                    strokeColor = Color.parseColor("#C8DCEE"),
                )
                setOnClickListener {
                    selectedHistoryTimestampMs = entry.timestampMs
                    renderHistoryEntries(entries, fallbackDetail)
                }
            }
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = 8
            }
            measurementHistoryRunsContainer.addView(row, layoutParams)
        }
        val selected = sorted.firstOrNull { it.timestampMs == selectedHistoryTimestampMs } ?: sorted.first()
        measurementHistoryTitle.text = "Selected run"
        measurementRunResults.text =
            "Captured: ${formatHistoryTimestamp(selected.timestampMs)}\n" +
                "Latency: ${selected.latency}\n" +
                "Download: ${selected.download}\n" +
                "Upload: ${selected.upload}\n" +
                "Status: ${selected.uploaded}\n\n" +
                selected.detail
    }

    private fun formatHistoryTimestamp(timestampMs: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMs))
    }

    private fun refreshMeasurementHistorySync() {
        statusText.text = "Refreshing sync status..."
        scope.launch {
            runCatching {
                getSyncDriver().getPendingSyncCounts()
            }.onSuccess { counts ->
                historyPendingMeasurements = counts.measurements
                historyPendingSubmissions = counts.submissions
                renderMeasurementHistoryUi()
                statusText.setTextColor(Color.parseColor("#3B5D77"))
                statusText.text = "Sync status refreshed."
            }.onFailure {
                historyPendingMeasurements = null
                historyPendingSubmissions = null
                renderMeasurementHistoryUi()
                statusText.text = "Unable to refresh sync status."
                statusText.setTextColor(Color.parseColor("#A82329"))
            }
        }
    }

    private fun runSharedSlice() {
        scope.launch {
            runCatching {
                val result = UploadTriggerParityHarness().runDefaultScenario()
                val envelope = smokeEnvelopeBuilder.mapStart(
                    hasReport = true,
                    errorMessage = null,
                )
                smokeFormatter.format(envelope) +
                    "\nmeasurements uploaded=${result.measurementsUploaded}, marked=${result.measurementsMarkedUploaded}" +
                    "\nsubmissions uploaded=${result.submissionsUploaded}, blocked=${result.submissionsBlockedBeforeUpload}"
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
        val story2Preflight = measurementPreflightUseCase.evaluate(
            edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightInput(
                request = edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartRequest(
                    collectionMode = edu.gatech.cc.cellwatch.domain.model.CollectionMode.FCC_CHALLENGE,
                    inVehicle = false,
                ),
                hasRuntimeProfile = true,
                // Phase3 harness button is a deterministic smoke path, not interactive preflight UX.
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.CELLULAR,
                userConfirmedNonCellularChallengePath = true,
            ),
        )
        if (!story2Preflight.allowed) {
            val envelope = smokeEnvelopeBuilder.failure(
                scenario = "measurement-start-preflight",
                errorMessage = "reason=${story2Preflight.reasonCode}",
            )
            if (uiMode == UiMode.MEASUREMENT_RUN_FLOW) {
                measurementRunDetail.text = "Unable to start measurement right now."
                measurementRunDetail.setTextColor(Color.parseColor("#A82329"))
            } else {
                statusText.text = smokeFormatter.format(envelope)
            }
            return
        }

        val preflightError = phase3PreflightError()
        if (preflightError != null) {
            val envelope = smokeEnvelopeBuilder.failure(
                scenario = "phase3-preflight",
                errorMessage = preflightError,
            )
            val rendered = smokeFormatter.format(envelope)
            Log.e(LOG_TAG, "Phase3 preflight failed: $preflightError")
            if (uiMode == UiMode.MEASUREMENT_RUN_FLOW) {
                measurementRunDetail.text = "Configuration issue: $preflightError"
                measurementRunDetail.setTextColor(Color.parseColor("#A82329"))
            } else {
                statusText.text = rendered
            }
            return
        }
        Log.d(
            LOG_TAG,
            "Phase3 preflight passed: msakMode=${runtimeProfile.msakMode}, msakEnv=${runtimeProfile.msakConfig.environment}, " +
                "msakLocalHost=${runtimeProfile.msakConfig.localServerHost}, supabaseMode=${runtimeProfile.supabaseMode}, " +
                "supabaseUrl=${runtimeProfile.resolveSyncSupabaseConfig().url}, supabaseKeyPresent=${runtimeProfile.resolveSyncSupabaseConfig().apiKey.isNotBlank()}",
        )
        measurementRunViewController.reset()
        measurementRunViewController.onSequenceStarted(UUID.randomUUID().toString())
        if (uiMode == UiMode.MEASUREMENT_RUN_FLOW) {
            renderMeasurementRunFlowState(
                detailText = "Measurement started. Collecting test data...",
            )
        } else {
            statusText.text = measurementRunUiPresenter.present(measurementRunViewController.currentState()).headerText
        }
        phase3RunInFlight = true
        scope.launch {
            val runOutcome = withContext(Dispatchers.IO) {
                runCatching {
                val localReachabilityIssue = checkLocalMsakReachabilityIssue()
                if (localReachabilityIssue != null) {
                    throw IllegalStateException(localReachabilityIssue)
                }
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
                    groupId = measurementRunViewController.currentState().results?.id ?: UUID.randomUUID().toString(),
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
                    progressListener = { stage ->
                        var phaseDetail = "Running tests against selected server..."
                        when (stage) {
                            MeasurementSequenceStage.STARTED -> {
                                measurementRunViewController.onSequenceStarted(request.groupId)
                                phaseDetail = "Measurement started."
                            }
                            MeasurementSequenceStage.LOCATE -> {
                                measurementRunViewController.onLocateStarted()
                                phaseDetail = "Finding closest test server..."
                            }
                            MeasurementSequenceStage.LATENCY -> {
                                measurementRunViewController.onLatencyStarted()
                                phaseDetail = "Running latency test..."
                            }
                            MeasurementSequenceStage.DOWNLOAD -> {
                                measurementRunViewController.onDownloadStarted()
                                phaseDetail = "Running download throughput test..."
                            }
                            MeasurementSequenceStage.UPLOAD -> {
                                measurementRunViewController.onUploadStarted()
                                phaseDetail = "Running upload throughput test..."
                            }
                            MeasurementSequenceStage.DONE -> {
                                phaseDetail = "Finalizing measurement results..."
                            }
                        }
                        if (uiMode == UiMode.MEASUREMENT_RUN_FLOW) {
                            renderMeasurementRunFlowState(
                                detailText = phaseDetail,
                            )
                            // Keep each stage visible long enough for deterministic UI-flow screenshots.
                            Thread.sleep(
                                when (stage) {
                                    MeasurementSequenceStage.DONE -> 250L
                                    else -> 600L
                                },
                            )
                        } else {
                            runOnUiThread {
                                statusText.text = measurementRunUiPresenter
                                    .present(measurementRunViewController.currentState())
                                    .headerText
                            }
                        }
                    },
                )
                val syncOrchestrator = MeasurementSequenceSyncOrchestrator(
                    sequenceOrchestrator = sequenceOrchestrator,
                    uploadTriggerUseCase = createSyncDriverFactory().createUploadTriggerUseCase(resolveSupabaseTarget()),
                )
                syncOrchestrator.run(request) to capabilitySummary
                }
            }
            runOutcome.onSuccess { (outcome, capabilitySummary) ->
                val sequence = outcome.sequenceOutcome
                val persistedMeasurementsList = measurementRepo.getByGroupId(sequence.group.id)
                val persistedMeasurements = persistedMeasurementsList.size
                val persistedSubmissions = if (submissionRepo.getById(sequence.group.id) != null) 1 else 0
                val capabilityPersistenceSummary = CapabilityPersistenceSummaryFormatter.format(
                    CapabilityPersistenceSummaryFormatter.summarize(persistedMeasurementsList),
                )
                val uploadTime = outcome.measurementCompleteUploadTime?.toEpochMilliseconds() ?: -1L
                val measurementCompleteReportSummary =
                    outcome.measurementCompleteReport.renderForStatus()
                val envelope = smokeEnvelopeBuilder.phase3Sequence(
                    measurementCompleteUploadTimeSet = disableSupabaseSync || outcome.measurementCompleteUploadTime != null,
                    persistedMeasurements = persistedMeasurements,
                    persistedSubmissions = persistedSubmissions,
                    errorMessage = if (!disableSupabaseSync && outcome.measurementCompleteUploadTime == null) {
                        "measurement-complete upload time missing; $measurementCompleteReportSummary"
                    } else {
                        null
                    },
                )
                measurementRunViewController.onCompleted(
                    group = sequence.group,
                    errorCode = null,
                    errorText = null,
                )
                measurementRunViewController.onUploadTimeResolved(outcome.measurementCompleteUploadTime)
                if (uiMode == UiMode.MEASUREMENT_RUN_FLOW) {
                    renderMeasurementRunFlowState(
                        detailText = if (disableSupabaseSync) {
                            "Measurement complete. Results saved on this device. Sync is disabled."
                        } else {
                            "Measurement complete. Results are saved and sync was attempted."
                        },
                    )
                } else {
                    val runHeader = measurementRunUiPresenter
                        .present(measurementRunViewController.currentState())
                        .headerText
                    statusText.text = Phase3UiSliceFormatter.format(
                        envelopeText = smokeFormatter.format(envelope),
                        result = Phase3UiSliceResult(
                            groupId = sequence.group.id,
                            throughputMachine = sequence.throughputServerMachine,
                            latencyMachine = sequence.latencyServerMachine,
                            submissionCreated = sequence.group.submission != null,
                            mapStartMeasurementsUploaded = outcome.mapStartReport.measurements.uploaded,
                            mapStartSubmissionsUploaded = outcome.mapStartReport.submissions.uploaded,
                            measurementCompleteUploadTimeSet = uploadTime >= 0L,
                            persistedMeasurements = persistedMeasurements,
                            persistedSubmissions = persistedSubmissions,
                            capabilityPersistenceSummary = capabilityPersistenceSummary,
                            capabilitySummary = capabilitySummary,
                        ),
                    ).let { "$runHeader\n$it" }
                }
                phase3RunInFlight = false
            }.onFailure {
                phase3RunInFlight = false
                val hintedMessage = withProtocolHint(it)
                Log.e(LOG_TAG, "Phase3 sequence failed", it)
                val envelope = smokeEnvelopeBuilder.failure(
                    scenario = "phase3-sequence-sync",
                    errorMessage = hintedMessage,
                )
                measurementRunViewController.onCompleted(
                    group = null,
                    errorCode = null,
                    errorText = hintedMessage,
                )
                if (uiMode == UiMode.MEASUREMENT_RUN_FLOW) {
                    renderMeasurementRunFlowState(
                        detailText = "Measurement failed. $hintedMessage",
                    )
                } else {
                    val runHeader = measurementRunUiPresenter
                        .present(measurementRunViewController.currentState())
                        .headerText
                    statusText.text = "$runHeader\n${smokeFormatter.format(envelope)}"
                }
            }
        }
    }

    private fun renderMeasurementRunFlowState(detailText: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { renderMeasurementRunFlowState(detailText) }
            return
        }
        val state = measurementRunViewController.currentState()
        val uiModel = measurementRunUiPresenter.present(state)
        val resultModel = measurementResultReadModelUseCase.present(state)
        measurementRunHeader.text = uiModel.headerText
        measurementRunDetail.text = detailText
        measurementRunDetail.setTextColor(
            if (state.progress == edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.ERROR) {
                Color.parseColor("#A82329")
            } else {
                Color.parseColor("#3B5D77")
            },
        )
        measurementRunProgress.progress = measurementRunProgressValue(state.progress)
        val showTerminal = uiModel.showCompletionActions
        measurementRunResults.visibility = if (showTerminal) View.VISIBLE else View.GONE
        measurementRunPrimaryButton.text = if (showTerminal) {
            "Take Another Measurement"
        } else {
            "Start Measurement"
        }
        val uploadedText = if (showTerminal && disableSupabaseSync) {
            "Sync disabled"
        } else {
            resultModel.uploadedText
        }
        val summaryText = if (showTerminal && disableSupabaseSync) {
            "Measurement complete. Results saved on this device. Sync is disabled."
        } else {
            resultModel.summaryText
        }
        measurementRunResults.text =
            "Latency: ${resultModel.latencyText}\n" +
                "Download: ${resultModel.downloadText}\n" +
                "Upload: ${resultModel.uploadText}\n" +
                "Uploaded: $uploadedText\n\n" +
                summaryText
        measurementRunResults.setTextColor(
            if (state.progress == edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.ERROR) {
                Color.parseColor("#A82329")
            } else {
                Color.parseColor("#2E4A62")
            },
        )
        if (showTerminal) {
            val centerLatLon = state.results?.centerLatLon()
            persistLatestHistorySnapshot(
                latency = resultModel.latencyText,
                download = resultModel.downloadText,
                upload = resultModel.uploadText,
                uploaded = uploadedText,
                detail = summaryText,
                latitude = centerLatLon?.first,
                longitude = centerLatLon?.second,
            )
        }
    }

    private fun measurementRunProgressValue(
        progress: edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress,
    ): Int {
        return when (progress) {
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.PRE -> 0
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.START -> 10
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.LOCATE -> 25
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.LATENCY -> 45
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.DOWNLOAD -> 65
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.UPLOAD -> 85
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.END -> 100
            edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress.ERROR -> 100
        }
    }

    private fun evaluateMeasurementStartPreflightFromUi() {
        measurementStartFlowController.setInVehicle(measurementPreflightInVehicleCheckbox.isChecked)
        val go = measurementStartFlowController.onGoPressed(
            collectionMode = measurementStartCollectionMode(),
            capabilitySnapshot = observedMeasurementStartCapabilities(),
            overrides = measurementStartEnvironmentOverrides(),
        )
        renderMeasurementPreflightOutput(go.state, go.environment.networkPath)
        if (go.state.shouldPromptConfirmation) {
            AlertDialog.Builder(this)
                .setMessage(go.state.confirmationMessage)
                .setPositiveButton("Measure anyway") { dialog, _ ->
                    dialog.dismiss()
                    val confirmed = measurementStartFlowController.onConfirmProceed()
                    renderMeasurementPreflightOutput(confirmed.state, confirmed.environment.networkPath)
                    if (shouldAutoStartMeasurementFromPreflight() && confirmed.state.latestResult?.allowed == true) {
                        openUiMode(
                            UI_MODE_MEASUREMENT_RUN_FLOW,
                            EXTRA_MEASUREMENT_RUN_AUTO_START to true,
                        )
                        finish()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    val canceled = measurementStartFlowController.onConfirmCancel()
                    renderMeasurementPreflightOutput(canceled.state, canceled.environment.networkPath)
                }
                .show()
        } else if (shouldAutoStartMeasurementFromPreflight() && go.state.latestResult?.allowed == true) {
            openUiMode(
                UI_MODE_MEASUREMENT_RUN_FLOW,
                EXTRA_MEASUREMENT_RUN_AUTO_START to true,
            )
            finish()
        }
    }

    private fun shouldAutoStartMeasurementFromPreflight(): Boolean {
        return intent?.getBooleanExtra(EXTRA_MEASUREMENT_START_AUTO_RUN, false) == true
    }

    private fun renderMeasurementPreflightOutput(
        flowState: MeasurementStartPreflightFlowUiState,
        networkPath: MeasurementNetworkPath,
    ) {
        measurementPreflightOutput.text = flowState.statusMessage
        measurementPreflightOutput.contentDescription = flowState.debugSummary
            .ifEmpty {
                fallbackDebugSummary(flowState.latestResult, networkPath)
            }
        measurementPreflightOutput.setTextColor(
            if (flowState.statusIsError) Color.parseColor("#A82329") else Color.parseColor("#003618"),
        )
    }

    private fun fallbackDebugSummary(
        result: MeasurementPreflightResult?,
        networkPath: MeasurementNetworkPath,
    ): String {
        if (result == null) return "networkPath=$networkPath"
        return "allowed=${result.allowed};reason=${result.reasonCode};networkPath=$networkPath"
    }

    private fun measurementStartCollectionMode(): edu.gatech.cc.cellwatch.domain.model.CollectionMode {
        return onboardingPersistenceUseCase.loadProfile()?.collectionMode
            ?: edu.gatech.cc.cellwatch.domain.model.CollectionMode.FCC_CHALLENGE
    }

    private fun observedMeasurementStartCapabilities(): MeasurementStartCapabilitySnapshot {
        return MeasurementStartCapabilitySnapshot(
            hasRuntimeProfile = true,
            hasLocationPermission = hasHarnessLocationPermissions(),
            networkPath = observedMeasurementNetworkPath(),
        )
    }

    private fun measurementStartEnvironmentOverrides(): MeasurementStartPreflightEnvironmentOverrides {
        return MeasurementStartPreflightEnvironmentOverrides(
            collectionMode = parseCollectionModeOverride(
                intent?.getStringExtra(EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_COLLECTION_MODE),
            ),
            hasRuntimeProfile = booleanExtraOverride(EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_RUNTIME_PROFILE),
            hasLocationPermission = booleanExtraOverride(EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_LOCATION_PERMISSION),
            networkPath = parseNetworkPathOverride(
                intent?.getStringExtra(EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_NETWORK_PATH),
            ),
        )
    }

    private fun parseCollectionModeOverride(raw: String?): edu.gatech.cc.cellwatch.domain.model.CollectionMode? {
        return when (raw?.trim()?.lowercase()) {
            "testing" -> edu.gatech.cc.cellwatch.domain.model.CollectionMode.TESTING
            "fcc_challenge", "fcc" -> edu.gatech.cc.cellwatch.domain.model.CollectionMode.FCC_CHALLENGE
            else -> null
        }
    }

    private fun parseNetworkPathOverride(raw: String?): MeasurementNetworkPath? {
        return when (raw?.trim()?.lowercase()) {
            "cellular", "cell" -> MeasurementNetworkPath.CELLULAR
            "wifi" -> MeasurementNetworkPath.WIFI
            "unknown" -> MeasurementNetworkPath.UNKNOWN
            else -> null
        }
    }

    private fun booleanExtraOverride(key: String): Boolean? {
        val extras = intent?.extras ?: return null
        if (!extras.containsKey(key)) return null
        return extras.getBoolean(key)
    }

    private fun observedMeasurementNetworkPath(): MeasurementNetworkPath {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return MeasurementNetworkPath.UNKNOWN
        val active = cm.activeNetwork ?: return MeasurementNetworkPath.UNKNOWN
        val caps = cm.getNetworkCapabilities(active) ?: return MeasurementNetworkPath.UNKNOWN
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> MeasurementNetworkPath.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> MeasurementNetworkPath.WIFI
            else -> MeasurementNetworkPath.UNKNOWN
        }
    }

    private suspend fun checkLocalMsakReachabilityIssue(): String? {
        if (runtimeProfile.msakMode != RuntimeMsakMode.LOCAL) return null
        val endpoint = runtimeProfile.msakConfig.localServerHost?.trim().orEmpty()
        if (endpoint.isBlank()) return "MSAK local endpoint is blank"
        val host = endpoint.substringBefore(':').ifBlank { endpoint }
        val port = endpoint.substringAfter(':', "80").toIntOrNull() ?: 80
        val reachable = withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1500)
                    true
                }
            }.getOrDefault(false)
        }
        return if (reachable) null else "MSAK local server is unreachable at $host:$port"
    }

    private fun installHarnessUncaughtExceptionHandler() {
        val currentDefault = Thread.getDefaultUncaughtExceptionHandler()
        if (currentDefault === harnessUncaughtExceptionHandler) {
            return
        }
        previousDefaultUncaughtExceptionHandler = currentDefault
        Thread.setDefaultUncaughtExceptionHandler(harnessUncaughtExceptionHandler)
    }

    private fun hasHarnessLocationPermissions(): Boolean {
        return listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun restoreDefaultUncaughtExceptionHandler() {
        if (Thread.getDefaultUncaughtExceptionHandler() === harnessUncaughtExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(previousDefaultUncaughtExceptionHandler)
        }
    }

    private val harnessUncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        if (phase3RunInFlight && isRecoverableMsakAsyncFailure(throwable)) {
            phase3RunInFlight = false
            val envelope = smokeEnvelopeBuilder.failure(
                scenario = "phase3-sequence-sync",
                errorMessage = withProtocolHint(throwable),
            )
            Log.e(LOG_TAG, "Recovered async MSAK failure from ${thread.name}", throwable)
            runOnUiThread {
                statusText.text = smokeFormatter.format(envelope)
            }
            return@UncaughtExceptionHandler
        }
        previousDefaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
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
            syncEnabled = !disableSupabaseSync,
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

    private fun phase3PreflightError(): String? {
        val issues = mutableListOf<String>()
        if (runtimeProfile.msakMode == RuntimeMsakMode.LOCAL) {
            val host = runtimeProfile.msakConfig.localServerHost
            if (host.isNullOrBlank()) {
                issues += "MSAK LOCAL requires resolved local server host"
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            issues += "READ_PHONE_STATE not granted (required by harness telephony/network capture)"
        }
        val supabaseConfig = runCatching { runtimeProfile.resolveSyncSupabaseConfig() }.getOrElse { error ->
            issues += "Supabase config unresolved: ${error.message}"
            return issues.joinToString("; ")
        }
        if (supabaseConfig.url.isBlank()) {
            issues += "Supabase URL is blank"
        } else {
            val allowedHosts = setOf("127.0.0.1", "10.0.2.2", "localhost")
            if (runtimeProfile.supabaseMode == RuntimeSupabaseMode.LOCAL &&
                allowedHosts.none { supabaseConfig.url.contains(it) }
            ) {
                issues += "Supabase LOCAL URL should target localhost/10.0.2.2 (got ${supabaseConfig.url})"
            }
        }
        if (supabaseConfig.apiKey.isBlank()) {
            issues += "Supabase API key is blank"
        }
        return issues.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun withProtocolHint(error: Throwable): String {
        val details = generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
        val mismatch = details.contains("MissingFieldException", ignoreCase = true) &&
            details.contains("BytesSent", ignoreCase = true)
        return if (mismatch) {
            "MSAK protocol mismatch: client expects Application.BytesSent/BytesReceived but server payload differs. " +
                "Use a matching local msak-server build for this msak-client-kmp version. details=$details"
        } else {
            error.message ?: details
        }
    }

    private fun isRecoverableMsakAsyncFailure(error: Throwable): Boolean {
        val details = generateSequence(error as Throwable?) { it.cause }
            .joinToString(" | ") { "${it::class.qualifiedName}:${it.message}" }
            .lowercase()
        return details.contains("authorizefailureexecption") ||
            details.contains("authorize call failed")
    }

    private fun formatReport(report: edu.gatech.cc.cellwatch.domain.sync.SyncAllReport?): String {
        if (report == null) return "null (driver error: ${syncDriver?.state?.value?.lastError})"
        return report.renderForStatus()
    }

    private fun buildCard(vararg children: android.view.View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = roundedCard(
                fillColor = Color.parseColor("#FFFFFF"),
                strokeColor = Color.parseColor("#DFDFDF"),
            )
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = 16
            }
            this.layoutParams = layoutParams
            children.forEach { child ->
                if (child.layoutParams == null) {
                    child.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = 12
                    }
                }
                addView(child)
            }
        }
    }

    private fun stylePrimaryButton(button: Button) {
        button.isAllCaps = false
        button.textSize = 15f
        button.setTextColor(Color.WHITE)
        button.minHeight = 104
        button.background = roundedCard(
            fillColor = Color.parseColor("#07416B"),
            strokeColor = Color.parseColor("#07416B"),
        )
    }

    private fun styleSecondaryButton(button: Button) {
        button.isAllCaps = false
        button.textSize = 15f
        button.setTextColor(Color.parseColor("#07416B"))
        button.minHeight = 96
        button.background = roundedCard(
            fillColor = Color.parseColor("#F8FBFD"),
            strokeColor = Color.parseColor("#5E8BAB"),
        )
    }

    private fun styleModeButton(button: Button) {
        button.isAllCaps = false
        button.textSize = 15f
        button.setTextColor(Color.parseColor("#1E5638"))
        button.minHeight = 92
        button.background = roundedCard(
            fillColor = Color.parseColor("#ECF7EF"),
            strokeColor = Color.parseColor("#C8E3CC"),
        )
    }

    private fun roundedCard(fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 26f
            setColor(fillColor)
            setStroke(2, strokeColor)
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
