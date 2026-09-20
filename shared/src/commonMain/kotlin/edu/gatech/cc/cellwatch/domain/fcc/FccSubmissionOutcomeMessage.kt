package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.localization.currentLanguageCode
import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * Why a completed measurement will or will not reach the FCC.
 *
 * A measurement can finish perfectly and still be withheld - taken off
 * cellular, contact details missing, no carrier detected. Nothing surfaced
 * that: the run reported "Measurement complete" and the submission was
 * silently dropped, so a user could collect measurements for an afternoon
 * believing they were contributing to a challenge and submit none of them.
 *
 * Phrased as what to do about it where there is something to do.
 *
 * Each message comes in two forms: a property that follows the device
 * language, for screens, and a function taking an explicit language, for the
 * extended export - a file that may be read by the FCC or the research team
 * and must not change language with the phone that produced it.
 */
object FccSubmissionOutcomeMessage {

    val SUBMITTED: String get() = submitted()

    fun submitted(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "This measurement will be submitted to the FCC.",
        es = "Esta medición se enviará a la FCC.",
    )

    val OPTED_OUT: String get() = optedOut()

    fun optedOut(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "Not submitted to the FCC: FCC submission is turned off in Settings. This " +
            "measurement is still saved and uploaded to the CellWatch server.",
        es = "No se envió a la FCC: el envío a la FCC está desactivado en los ajustes. Esta " +
            "medición igual se guarda y se sube al servidor de CellWatch.",
    )

    val NOT_ELIGIBLE: String get() = notEligible()

    fun notEligible(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "Not submitted to the FCC: the FCC only accepts measurements taken over a " +
            "cellular connection. Turn off Wi-Fi and measure again.",
        es = "No se envió a la FCC: la FCC solo acepta mediciones tomadas por una conexión " +
            "celular. Apague el WiFi y mida otra vez.",
    )

    val CONTACT_INCOMPLETE: String get() = contactIncomplete()

    fun contactIncomplete(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "Not submitted to the FCC: your contact details are incomplete. Add your name, " +
            "email and phone in your profile, then measure again.",
        es = "No se envió a la FCC: sus datos de contacto están incompletos. Anote su nombre, " +
            "correo electrónico y número de teléfono en su perfil, y mida otra vez.",
    )

    val CARRIER_UNKNOWN: String get() = carrierUnknown()

    fun carrierUnknown(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "Not submitted to the FCC: no mobile carrier was detected for this measurement.",
        es = "No se envió a la FCC: no se detectó ningún proveedor móvil para esta medición.",
    )

    val DEVICE_INCOMPLETE: String get() = deviceIncomplete()

    fun deviceIncomplete(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "Not submitted to the FCC: this device did not report the information the FCC " +
            "requires.",
        es = "No se envió a la FCC: este dispositivo no reportó la información que requiere " +
            "la FCC.",
    )

    /**
     * For records written after the fact.
     *
     * The validation result exists only while a run is in flight and is not
     * stored, so an export cannot always say why a submission was withheld.
     * Saying so is better than reusing a run-time message that asserts a
     * specific cause - an export may end up in front of the FCC, and a
     * confident wrong reason is worse there than an admitted gap.
     */
    val REASON_UNRECORDED: String get() = reasonUnrecorded()

    fun reasonUnrecorded(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "No FCC submission was created for this run. The specific reason was not " +
            "recorded with the measurement.",
        es = "No se creó ningún envío a la FCC para esta sesión. No se registró el motivo " +
            "específico junto con la medición.",
    )

    val INCOMPLETE_TESTS: String get() = incompleteTests()

    fun incompleteTests(language: String = currentLanguageCode()): String = localized(
        language = language,
        en = "Not submitted to the FCC: one of the three tests did not produce a result.",
        es = "No se envió a la FCC: una de las tres pruebas no produjo un resultado.",
    )

    /**
     * [validation] is null when no submission was attempted at all, which the
     * orchestrator does when the measurement was not eligible in the first
     * place - a different thing from one that was built and then rejected.
     */
    fun forOutcome(
        submissionCreated: Boolean,
        validation: FccSubmissionValidationResult?,
        challengeMode: Boolean = true,
    ): String {
        if (submissionCreated) return SUBMITTED
        // Checked before the eligibility branches: a user who turned
        // submission off would otherwise be told their connection was the
        // problem, which is both wrong and unactionable.
        if (!challengeMode) return OPTED_OUT
        val codes = validation?.codes ?: return NOT_ELIGIBLE
        return when {
            codes.any { it in MEASUREMENT_CODES } -> INCOMPLETE_TESTS
            codes.any { it in CONTACT_CODES } -> CONTACT_INCOMPLETE
            FccSubmissionValidationCode.MISSING_PROVIDER_NAME in codes -> CARRIER_UNKNOWN
            else -> DEVICE_INCOMPLETE
        }
    }

    private val MEASUREMENT_CODES = setOf(
        FccSubmissionValidationCode.MISSING_LATENCY_MEASUREMENT,
        FccSubmissionValidationCode.MISSING_DOWNLOAD_MEASUREMENT,
        FccSubmissionValidationCode.MISSING_UPLOAD_MEASUREMENT,
    )

    private val CONTACT_CODES = setOf(
        FccSubmissionValidationCode.MISSING_CONTACT_NAME,
        FccSubmissionValidationCode.MISSING_CONTACT_EMAIL,
        FccSubmissionValidationCode.INVALID_CONTACT_EMAIL,
        FccSubmissionValidationCode.MISSING_CONTACT_PHONE,
        FccSubmissionValidationCode.INVALID_CONTACT_PHONE,
    )
}
