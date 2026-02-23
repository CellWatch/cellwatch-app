package edu.gatech.cc.cellwatch.domain.applaunch

data class AppLaunchRoutingInput(
    val onboardingComplete: Boolean,
    val runtimeProfileReady: Boolean = true,
)

data class AppLaunchRoutingDecision(
    val destinationToken: String,
    val reason: String,
)

class AppLaunchRoutingUseCase {
    fun resolve(input: AppLaunchRoutingInput): AppLaunchRoutingDecision {
        if (!input.onboardingComplete) {
            return AppLaunchRoutingDecision(
                destinationToken = DESTINATION_ONBOARDING_FLOW,
                reason = "onboarding_incomplete",
            )
        }
        if (!input.runtimeProfileReady) {
            return AppLaunchRoutingDecision(
                destinationToken = DESTINATION_BLOCKING_ERROR,
                reason = "runtime_profile_unavailable",
            )
        }
        return AppLaunchRoutingDecision(
            destinationToken = DESTINATION_MAP_HOME,
            reason = "ready",
        )
    }

    companion object {
        const val DESTINATION_ONBOARDING_FLOW = "onboarding-flow"
        const val DESTINATION_MAP_HOME = "map-home"
        const val DESTINATION_BLOCKING_ERROR = "blocking-error"
    }
}
