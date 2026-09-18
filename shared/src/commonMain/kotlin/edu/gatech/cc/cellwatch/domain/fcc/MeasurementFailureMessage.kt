package edu.gatech.cc.cellwatch.domain.fcc

/**
 * Turns a measurement failure into something a person can act on.
 *
 * The raw text is an exception description - "MsakException: authorize call
 * failed" - which tells a user nothing and misattributes the cause besides: a
 * timeout reaching the M-Lab Locate service surfaces under that same message,
 * because the selector substitutes an unreachable placeholder server whose
 * authorize call then fails.
 *
 * These failures are overwhelmingly transient and environmental: the device
 * could not reach the measurement service, often because cellular service at
 * that spot is too poor to complete a test at all. Moving or retrying is the
 * useful advice.
 *
 * Callers must keep logging the raw text - this is for display only, and
 * deliberately discards detail that matters when diagnosing.
 */
object MeasurementFailureMessage {

    const val UNREACHABLE =
        "The measurement service can't be reached right now. Your cellular signal may be " +
            "too weak to run a test here. Please try again later, or move to a different location."

    const val CANCELLED = "The measurement was cancelled."

    const val GENERIC =
        "The measurement couldn't be completed. Please try again."

    fun forFailure(raw: String?): String {
        val text = raw?.lowercase().orEmpty()
        return when {
            text.isBlank() -> GENERIC
            isCancellation(text) -> CANCELLED
            isUnreachable(text) -> UNREACHABLE
            else -> GENERIC
        }
    }

    private fun isCancellation(text: String): Boolean =
        text.contains("cancellationexception") || text.contains("was cancelled")

    private fun isUnreachable(text: String): Boolean =
        UNREACHABLE_SIGNALS.any { text.contains(it) }

    private val UNREACHABLE_SIGNALS = listOf(
        // The locate-failure masquerade: no server was ever found, so the
        // placeholder's authorize call is what actually fails.
        "authorize call failed",
        "authorizefailure",
        "locate",
        "no usable throughput url",
        "handshake",
        // Transport-level
        "timeout",
        "timed out",
        "did not complete within",
        "cannot connect",
        "connection refused",
        "network is unreachable",
        "no route to host",
        "socket is not connected",
        "bad url",
        "0.0.0.0",
    )
}
