package edu.gatech.cc.cellwatch.androidtestapp.product

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.onboarding.AndroidOnboardingProfileStore
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingInput
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingUseCase
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeInput
import edu.gatech.cc.cellwatch.domain.measurementhistory.MeasurementHistoryViewModel
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunViewModel
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartViewModel
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeViewModel
import edu.gatech.cc.cellwatch.domain.navigation.Destination
import edu.gatech.cc.cellwatch.domain.navigation.Navigator
import edu.gatech.cc.cellwatch.domain.navigation.toDestination
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingPersistenceUseCase
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileViewModel
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingValidationUseCase

/**
 * Hosts the product navigation graph. Android counterpart of `ProductShell`.
 *
 * One Activity swapping content views rather than an Activity per screen: the
 * shared [Navigator] owns the back stack, and having the platform own a second
 * one would mean two sources of truth that could disagree. System back is
 * forwarded to the navigator and only falls through to the platform at the
 * root.
 *
 * Destinations without a screen render a placeholder, so the graph is walkable
 * end to end and unfinished work is visible rather than looking like a dead
 * button.
 */
class ProductShellActivity : AppCompatActivity() {

    private lateinit var navigator: Navigator
    private val mapHomeViewModel = MapHomeViewModel(minHexGridZoom = 0.0)
    private var mapHomeScreen: MapHomeScreen? = null
    private var measurementRunScreen: MeasurementRunScreen? = null

    /**
     * Resolved once. A failure here means the build has no usable runtime
     * configuration, which is a blocking error rather than something a screen
     * can retry past.
     */
    private val container by lazy { AndroidProductContainer.resolve(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = AndroidOnboardingProfileStore(applicationContext)
        val decision = AppLaunchRoutingUseCase().resolve(
            AppLaunchRoutingInput(
                onboardingComplete = store.loadProfile()?.onboardingComplete == true,
                // Previously hardcoded true, which meant a build with no usable
                // runtime configuration still offered a Measure button that
                // could only fail once a measurement was under way.
                runtimeProfileReady = container.isSuccess,
            ),
        )
        navigator = Navigator(decision.toDestination())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navigator.back()) {
                    render()
                } else {
                    // At the root the navigator declines, so the platform gets
                    // its usual behaviour rather than a trapped back button.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        render()
    }

    private fun goTo(destination: Destination) {
        navigator.goTo(destination)
        render()
    }

    private fun resetTo(destination: Destination) {
        navigator.resetTo(destination)
        render()
    }

    override fun onDestroy() {
        mapHomeScreen?.onDestroy()
        mapHomeScreen = null
        measurementRunScreen?.onDestroy()
        measurementRunScreen = null
        super.onDestroy()
    }

    private fun render() {
        val destination = navigator.current
        if (destination !is Destination.MapHome) {
            // The map view holds native resources; drop it when leaving.
            mapHomeScreen?.onDestroy()
            mapHomeScreen = null
        }
        if (destination !is Destination.MeasurementRun) {
            // Leaving the run screen cancels the run: there is no foreground
            // service, so a measurement the user navigated away from would
            // produce partial data at best.
            measurementRunScreen?.onDestroy()
            measurementRunScreen = null
        }
        title = label(destination)
        supportActionBar?.setDisplayHomeAsUpEnabled(navigator.canGoBack)
        setContentView(screen(destination))
    }

    private fun screen(destination: Destination): View = when (destination) {
        is Destination.Onboarding -> OnboardingScreen(
            context = this,
            viewModel = OnboardingProfileViewModel(
                OnboardingValidationUseCase(),
                OnboardingPersistenceUseCase(AndroidOnboardingProfileStore(applicationContext)),
            ),
            // Reset rather than push: back must not return to onboarding once a
            // profile is saved.
            onComplete = { resetTo(Destination.MapHome) },
        ).view

        is Destination.MapHome -> MapHomeScreen(
            context = this,
            viewModel = mapHomeViewModel,
            inputProvider = {
                // Counts are filled in asynchronously by mapHomeSyncProvider;
                // this is the synchronous part the screen needs immediately.
                MapHomeInput(
                    onboardingComplete = AndroidOnboardingProfileStore(applicationContext)
                        .loadProfile()?.onboardingComplete == true,
                    recentRunCount = 0,
                    pendingCountsKnown = false,
                    pendingMeasurements = 0,
                    pendingSubmissions = 0,
                )
            },
            syncStatusProvider = { deliver ->
                val productContainer = container.getOrNull()
                if (productContainer == null) {
                    deliver(null)
                } else {
                    lifecycleScope.launch { deliver(productContainer.syncStatus()) }
                }
            },
            measurementLocationProvider = { deliver ->
                val productContainer = container.getOrNull()
                if (productContainer == null) {
                    deliver(emptyList())
                } else {
                    lifecycleScope.launch {
                        deliver(productContainer.recentMeasurementLocations(500L))
                    }
                }
            },
            onMeasure = { goTo(Destination.MeasurementStart) },
            onHistory = { goTo(Destination.History) },
            onSettings = { goTo(Destination.Settings) },
        ).also { mapHomeScreen = it }.view

        is Destination.MeasurementStart -> MeasurementStartScreen(
            context = this,
            viewModel = MeasurementStartViewModel(CollectionMode.FCC_CHALLENGE),
            hasRuntimeProfile = container.isSuccess,
            onReadyToRun = { inVehicle -> goTo(Destination.MeasurementRun(inVehicle)) },
        ).view

        is Destination.MeasurementRun -> container.fold(
            onSuccess = { productContainer ->
                MeasurementRunScreen(
                    context = this,
                    viewModel = MeasurementRunViewModel(
                        container = productContainer,
                        mode = CollectionMode.FCC_CHALLENGE,
                        inVehicle = destination.inVehicle,
                    ),
                    onDone = { resetTo(Destination.MapHome) },
                    onMeasureAgain = { resetTo(Destination.MeasurementStart) },
                ).also { measurementRunScreen = it }.view
            },
            onFailure = { error ->
                placeholder(
                    "Measurement is unavailable: ${error.message}",
                    Components.StatusTone.WARNING,
                )
            },
        )

        is Destination.History -> container.fold(
            onSuccess = { productContainer ->
                HistoryScreen(
                    context = this,
                    viewModel = MeasurementHistoryViewModel(),
                    snapshotProvider = { deliver ->
                        lifecycleScope.launch { deliver(productContainer.historySnapshot()) }
                    },
                    onRetry = { deliver ->
                        lifecycleScope.launch { deliver(productContainer.retryPendingUploads()) }
                    },
                ).also { it.setOnBack { resetTo(Destination.MapHome) } }.view
            },
            onFailure = { error ->
                placeholder("History is unavailable: ${error.message}", Components.StatusTone.WARNING)
            },
        )

        is Destination.BlockingError -> placeholder(destination.reason, Components.StatusTone.WARNING)

        else -> placeholder(
            "This screen is not built yet. See UI_DELIVERY_PLAN.md for where it lands.",
            Components.StatusTone.NEUTRAL,
        )
    }

    private fun placeholder(message: String, tone: Components.StatusTone): View =
        ScreenScaffold(this).apply {
            addContent(Components.statusCard(this@ProductShellActivity, message, tone))
        }

    private fun label(destination: Destination): String = when (destination) {
        is Destination.Onboarding -> "Your profile"
        is Destination.MapHome -> "Map home"
        is Destination.MeasurementStart -> "Start measurement"
        is Destination.MeasurementRun -> "Measurement"
        is Destination.History -> "History"
        is Destination.Settings -> "Settings"
        is Destination.Export -> "Export"
        is Destination.BlockingError -> "Cannot start"
    }
}
