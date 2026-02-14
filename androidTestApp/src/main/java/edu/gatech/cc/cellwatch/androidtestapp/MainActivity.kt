package edu.gatech.cc.cellwatch.androidtestapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import edu.gatech.cc.cellwatch.domain.capability.AndroidPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.CapabilityCaptureReportFormatter
import edu.gatech.cc.cellwatch.domain.capability.CapabilityPersistenceSummaryFormatter
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
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementNetworkPath
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementPreflightResult
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementPreflightUseCase
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightFlowUiState
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightFlowViewModel
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightEnvironmentOverrides
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightEnvironmentResolver
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightUiPresenter
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunUiPresenter
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunViewController
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingPersistenceUseCase
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileSubmission
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileUiState
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileViewModel
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingValidationUseCase
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
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

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
        const val EXTRA_UI_MODE = "cellwatch.uiMode"
        const val UI_MODE_ONBOARDING_FLOW = "onboarding-flow"
        const val UI_MODE_MEASUREMENT_START_FLOW = "measurement-start-flow"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_NETWORK_PATH = "cellwatch.measurementPreflight.overrideNetworkPath"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_LOCATION_PERMISSION = "cellwatch.measurementPreflight.overrideLocationPermission"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_RUNTIME_PROFILE = "cellwatch.measurementPreflight.overrideRuntimeProfile"
        const val EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_COLLECTION_MODE = "cellwatch.measurementPreflight.overrideCollectionMode"
        private const val LOG_TAG = "AndroidTestHarness"
        private const val PERMISSION_REQUEST_CODE = 7001
    }

    private enum class UiMode {
        FULL_HARNESS,
        ONBOARDING_FLOW,
        MEASUREMENT_START_FLOW,
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

    private var syncDriver: AndroidTestSyncDriver? = null
    private var lastGroup: MeasurementGroup? = null
    @Volatile private var phase3RunInFlight: Boolean = false
    private var previousDefaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private val runtimeModeBridge = RuntimeModeUiBridge()
    private var selectedMsakMode: RuntimeMsakMode = RuntimeMsakMode.LOCAL
    private var selectedSupabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL
    private val allowRemoteSupabase: Boolean = System.getenv("CELLWATCH_ALLOW_REMOTE_SUPABASE") == "true"
    private var runtimeProfile: RuntimeSyncMsakProfile =
        RuntimeProfileResolver.resolveProfile(resolveRuntimeProfileConfig())
    private val onboardingValidationUseCase = OnboardingValidationUseCase()
    private lateinit var onboardingPersistenceUseCase: OnboardingPersistenceUseCase
    private lateinit var onboardingViewModel: OnboardingProfileViewModel
    private val measurementPreflightUseCase = MeasurementPreflightUseCase()
    private val measurementStartEnvironmentResolver = MeasurementStartPreflightEnvironmentResolver()
    private val measurementStartFlowViewModel = MeasurementStartPreflightFlowViewModel(
        useCase = measurementPreflightUseCase,
        uiPresenter = MeasurementStartPreflightUiPresenter(),
    )
    private val measurementRunViewController = MeasurementRunViewController()
    private val measurementRunUiPresenter = MeasurementRunUiPresenter()
    private var onboardingUiRenderInProgress = false
    private val smokeEnvelopeBuilder = SyncSmokeEnvelopeBuilder()
    private val smokeFormatter = SyncSmokeResultFormatter()
    private var uiMode: UiMode = UiMode.FULL_HARNESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiMode = if (intent?.getStringExtra(EXTRA_UI_MODE) == UI_MODE_ONBOARDING_FLOW) {
            UiMode.ONBOARDING_FLOW
        } else if (intent?.getStringExtra(EXTRA_UI_MODE) == UI_MODE_MEASUREMENT_START_FLOW) {
            UiMode.MEASUREMENT_START_FLOW
        } else {
            UiMode.FULL_HARNESS
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
        setContentView(buildUi())
        if (uiMode != UiMode.MEASUREMENT_START_FLOW) {
            bindOnboardingInputs()
            applyOnboardingUiState(onboardingViewModel.loadPersistedProfile())
        }
        requestHarnessRuntimePermissions()
    }

    override fun onDestroy() {
        restoreDefaultUncaughtExceptionHandler()
        super.onDestroy()
        scope.cancel()
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

    private fun buildUi(): ScrollView {
        return when (uiMode) {
            UiMode.ONBOARDING_FLOW -> buildOnboardingOnlyUi()
            UiMode.MEASUREMENT_START_FLOW -> buildMeasurementStartOnlyUi()
            UiMode.FULL_HARNESS -> buildHarnessUi()
        }
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
        content.addView(buildCard(seedAndMapSync, measurementCompleteSync, runMapSyncOnly, runSharedSliceButton, locateServersButton, runPhase3SequenceButton))
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
        content.addView(buildCard(onboardingNameInput, onboardingPhoneInput, onboardingEmailInput, onboardingAckCheckbox, onboardingSubmitButton))
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
            text = "Go"
            stylePrimaryButton(this)
            setOnClickListener { evaluateMeasurementStartPreflightFromUi() }
        }
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
            ),
        )
        content.addView(measurementPreflightOutput)
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
                hasLocationPermission = observedMeasurementStartCapabilities().hasLocationPermission,
                networkPath = MeasurementNetworkPath.UNKNOWN,
                userConfirmedNonCellularChallengePath = false,
            ),
        )
        if (!story2Preflight.allowed) {
            val envelope = smokeEnvelopeBuilder.failure(
                scenario = "measurement-start-preflight",
                errorMessage = "reason=${story2Preflight.reasonCode}",
            )
            statusText.text = smokeFormatter.format(envelope)
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
            statusText.text = rendered
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
        statusText.text = measurementRunUiPresenter.present(measurementRunViewController.currentState()).headerText
        phase3RunInFlight = true
        scope.launch {
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
                )
                val syncOrchestrator = MeasurementSequenceSyncOrchestrator(
                    sequenceOrchestrator = sequenceOrchestrator,
                    uploadTriggerUseCase = createSyncDriverFactory().createUploadTriggerUseCase(resolveSupabaseTarget()),
                )
                syncOrchestrator.run(request) to capabilitySummary
            }.onSuccess { (outcome, capabilitySummary) ->
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
                    measurementCompleteUploadTimeSet = outcome.measurementCompleteUploadTime != null,
                    persistedMeasurements = persistedMeasurements,
                    persistedSubmissions = persistedSubmissions,
                    errorMessage = if (outcome.measurementCompleteUploadTime == null) {
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
                val runHeader = measurementRunUiPresenter
                    .present(measurementRunViewController.currentState())
                    .headerText
                statusText.text = "$runHeader\n${smokeFormatter.format(envelope)}"
            }
        }
    }

    private fun evaluateMeasurementStartPreflightFromUi() {
        measurementStartFlowViewModel.setInVehicle(measurementPreflightInVehicleCheckbox.isChecked)
        val resolvedInputs = measurementStartEnvironmentResolver.resolve(
            collectionMode = measurementStartCollectionMode(),
            capabilitySnapshot = observedMeasurementStartCapabilities(),
            overrides = measurementStartEnvironmentOverrides(),
        )
        val goState = measurementStartFlowViewModel.onGoPressed(resolvedInputs)
        renderMeasurementPreflightOutput(goState, resolvedInputs.networkPath)
        if (goState.shouldPromptConfirmation) {
            AlertDialog.Builder(this)
                .setMessage(goState.confirmationMessage)
                .setPositiveButton("Measure anyway") { dialog, _ ->
                    dialog.dismiss()
                    val confirmedState = measurementStartFlowViewModel.onConfirmProceed(resolvedInputs)
                    renderMeasurementPreflightOutput(confirmedState, resolvedInputs.networkPath)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    val canceledState = measurementStartFlowViewModel.onConfirmCancel()
                    renderMeasurementPreflightOutput(canceledState, resolvedInputs.networkPath)
                }
                .show()
        }
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
