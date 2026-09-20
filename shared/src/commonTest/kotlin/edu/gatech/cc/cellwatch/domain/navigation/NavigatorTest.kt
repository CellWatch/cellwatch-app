package edu.gatech.cc.cellwatch.domain.navigation

import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingInput
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    @Test
    fun startsAtItsRootAndCannotGoBackFromIt() {
        val nav = Navigator(Destination.MapHome)

        assertEquals(Destination.MapHome, nav.current)
        assertFalse(nav.canGoBack)
        assertFalse(nav.back(), "back at the root must defer to the platform, not pop an empty stack")
        assertEquals(Destination.MapHome, nav.current)
    }

    @Test
    fun pushAndPopWalkTheStack() {
        val nav = Navigator(Destination.MapHome)

        nav.goTo(Destination.MeasurementStart)
        nav.goTo(Destination.MeasurementRun())
        assertEquals(
            listOf(Destination.MapHome, Destination.MeasurementStart, Destination.MeasurementRun()),
            nav.backStack,
        )

        assertTrue(nav.back())
        assertEquals(Destination.MeasurementStart, nav.current)
    }

    @Test
    fun navigatingToTheScreenAlreadyShowingIsIgnored() {
        // A double tap must not cost the user two presses of back.
        val nav = Navigator(Destination.MapHome)

        nav.goTo(Destination.Settings)
        nav.goTo(Destination.Settings)

        assertEquals(2, nav.backStack.size)
        assertTrue(nav.back())
        assertEquals(Destination.MapHome, nav.current)
    }

    @Test
    fun resetToDropsHistory() {
        // Finishing onboarding must not leave onboarding behind the back button.
        val nav = Navigator(Destination.Onboarding)

        nav.resetTo(Destination.MapHome)

        assertEquals(Destination.MapHome, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun backToRootKeepsTheRoot() {
        val nav = Navigator(Destination.MapHome)
        nav.goTo(Destination.History)
        nav.goTo(Destination.Settings)

        nav.backToRoot()

        assertEquals(Destination.MapHome, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun aFirstRunLandsOnConsentAndAReadyAppOnTheMap() {
        val useCase = AppLaunchRoutingUseCase()

        // DataUse, not Onboarding: consent is the first step of the flow now.
        // A new user is told what is published before being asked for their
        // name, and the profile form is the last step rather than the first.
        assertEquals(
            Destination.DataUse,
            useCase.resolve(AppLaunchRoutingInput(onboardingComplete = false)).toDestination(),
        )
        assertEquals(
            Destination.MapHome,
            useCase.resolve(AppLaunchRoutingInput(onboardingComplete = true)).toDestination(),
        )
    }

    @Test
    fun anUnusableRuntimeProfileBlocksRatherThanGuessingAScreen() {
        val decision = AppLaunchRoutingUseCase().resolve(
            AppLaunchRoutingInput(onboardingComplete = true, runtimeProfileReady = false),
        )

        val destination = decision.toDestination()
        assertTrue(destination is Destination.BlockingError)
        assertEquals("runtime_profile_unavailable", destination.reason)
    }
}
