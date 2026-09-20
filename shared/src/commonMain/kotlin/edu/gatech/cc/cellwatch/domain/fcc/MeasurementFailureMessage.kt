package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.localization.localized

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

    val UNREACHABLE: String get() = localized(
        en = "The measurement service can't be reached right now. Your cellular signal may be " +
            "too weak to run a test here. Please try again later, or move to a different location.",
        es = "No se puede comunicar con el servicio de medición en este momento. Puede que su " +
            "señal celular sea demasiado débil para hacer una prueba aquí. Por favor inténtelo " +
            "más tarde, o muévase a otro lugar.",
    )

    val CANCELLED: String get() = localized(
        en = "The measurement was cancelled.",
        es = "Se canceló la medición.",
    )

    val GENERIC: String get() = localized(
        en = "The measurement couldn't be completed. Please try again.",
        es = "No se pudo completar la medición. Por favor inténtelo de nuevo.",
    )

    fun forFailure(raw: String?): String {
        val text = raw?.lowercase().orEmpty()
        return when {
            text.isBlank() -> GENERIC
            matchesCancellation(text) -> CANCELLED
            isUnreachable(text) -> UNREACHABLE
            else -> GENERIC
        }
    }

    /**
     * Whether a failure was the user stopping the run rather than something
     * going wrong. Callers should not describe these as failures.
     */
    fun isCancellation(raw: String?): Boolean = matchesCancellation(raw?.lowercase().orEmpty())

    private fun matchesCancellation(text: String): Boolean =
        text.contains("cancellationexception") ||
            text.contains("was cancelled") ||
            // MeasurementRunViewModel hands our own CANCELLED sentence back
            // as the error message, and the run presenter then asks whether
            // it was a cancellation. Once that sentence is translated,
            // "was cancelled" no longer appears in it, and a stopped run
            // would be reported as "Medición fallida: Se canceló la
            // medición." Matching the string we produced covers any language
            // without listing markers for each one.
            text == CANCELLED.lowercase()

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
