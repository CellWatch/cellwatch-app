package edu.gatech.cc.cellwatch.androidtestapp.product

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.onboarding.AndroidOnboardingProfileStore
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingInput
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingUseCase
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeInput
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = AndroidOnboardingProfileStore(applicationContext)
        val decision = AppLaunchRoutingUseCase().resolve(
            AppLaunchRoutingInput(
                onboardingComplete = store.loadProfile()?.onboardingComplete == true,
                runtimeProfileReady = true,
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
        super.onDestroy()
    }

    private fun render() {
        val destination = navigator.current
        if (destination !is Destination.MapHome) {
            // The map view holds native resources; drop it when leaving.
            mapHomeScreen?.onDestroy()
            mapHomeScreen = null
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
                // Counts stay zero until history is wired (task 2.1); what
                // matters here is that a saved profile enables Measure.
                MapHomeInput(
                    onboardingComplete = AndroidOnboardingProfileStore(applicationContext)
                        .loadProfile()?.onboardingComplete == true,
                    recentRunCount = 0,
                    pendingCountsKnown = false,
                    pendingMeasurements = 0,
                    pendingSubmissions = 0,
                )
            },
            onMeasure = { goTo(Destination.MeasurementStart) },
            onHistory = { goTo(Destination.History) },
            onSettings = { goTo(Destination.Settings) },
        ).also { mapHomeScreen = it }.view

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
