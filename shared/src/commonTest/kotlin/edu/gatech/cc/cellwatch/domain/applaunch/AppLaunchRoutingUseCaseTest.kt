package edu.gatech.cc.cellwatch.domain.applaunch

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLaunchRoutingUseCaseTest {
    private val useCase = AppLaunchRoutingUseCase()

    @Test
    fun resolve_onboardingIncomplete_routesToOnboarding() {
        val decision = useCase.resolve(
            AppLaunchRoutingInput(
                onboardingComplete = false,
                runtimeProfileReady = true,
            ),
        )

        assertEquals(AppLaunchRoutingUseCase.DESTINATION_ONBOARDING_FLOW, decision.destinationToken)
        assertEquals("onboarding_incomplete", decision.reason)
    }

    @Test
    fun resolve_runtimeMissing_routesToBlockingError() {
        val decision = useCase.resolve(
            AppLaunchRoutingInput(
                onboardingComplete = true,
                runtimeProfileReady = false,
            ),
        )

        assertEquals(AppLaunchRoutingUseCase.DESTINATION_BLOCKING_ERROR, decision.destinationToken)
        assertEquals("runtime_profile_unavailable", decision.reason)
    }

    @Test
    fun resolve_ready_routesToMapHome() {
        val decision = useCase.resolve(
            AppLaunchRoutingInput(
                onboardingComplete = true,
                runtimeProfileReady = true,
            ),
        )

        assertEquals(AppLaunchRoutingUseCase.DESTINATION_MAP_HOME, decision.destinationToken)
        assertEquals("ready", decision.reason)
    }
}
