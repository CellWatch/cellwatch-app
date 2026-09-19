package edu.gatech.cc.cellwatch.domain.navigation

import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingDecision
import edu.gatech.cc.cellwatch.domain.applaunch.AppLaunchRoutingUseCase

/**
 * Every screen a user can reach, named once for both platforms.
 *
 * The app previously expressed screens as a per-platform launch-mode enum
 * selected by an Intent extra or launch argument, which meant each platform
 * owned its own idea of what screens existed and a story could be "done"
 * without being reachable. A destination here is a commitment that both
 * platforms present the same graph.
 *
 * Launch modes still exist in the test harnesses. They are an instrumentation
 * affordance, not navigation, and never satisfy a story.
 */
sealed interface Destination {
    data object Onboarding : Destination
    data object MapHome : Destination
    data object MeasurementStart : Destination
    /**
     * Carries the in-vehicle answer collected on [MeasurementStart].
     *
     * On the destination rather than in a shared mutable holder: the flag is
     * part of the measurement being started, and a holder would let a second
     * run inherit the first one's answer.
     */
    data class MeasurementRun(val inVehicle: Boolean = false) : Destination
    data object History : Destination
    data object Settings : Destination
    data object Export : Destination

    /** Unrecoverable startup state, e.g. runtime profile missing. */
    data class BlockingError(val reason: String) : Destination
}

/**
 * Where a cold launch lands, as a typed destination.
 *
 * [AppLaunchRoutingUseCase] keeps returning string tokens because its contract
 * and tests predate this graph; translating here means the launch decision has
 * one definition rather than one per platform, which is how the two apps
 * drifted apart in the first place.
 */
fun AppLaunchRoutingDecision.toDestination(): Destination = when (destinationToken) {
    AppLaunchRoutingUseCase.DESTINATION_ONBOARDING_FLOW -> Destination.Onboarding
    AppLaunchRoutingUseCase.DESTINATION_MAP_HOME -> Destination.MapHome
    AppLaunchRoutingUseCase.DESTINATION_BLOCKING_ERROR -> Destination.BlockingError(reason)
    // A token this graph does not know is a routing bug. Surfacing it beats
    // guessing a screen: a wrong guess looks like a working app that quietly
    // skipped onboarding or a required gate.
    else -> Destination.BlockingError("unknown destination token: $destinationToken")
}
