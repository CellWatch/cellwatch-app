package edu.gatech.cc.cellwatch.domain.profile

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * Contact-details wording, shared by onboarding and Settings.
 *
 * The two screens ask for the same three fields and reject them for the same
 * reasons, and their wording had already been copy-pasted between the two
 * view models. Keeping one copy of each sentence is what stops the Spanish
 * drifting apart from the English on one screen and not the other.
 *
 * Where frozenApp had an equivalent its Spanish is reused - "Correo
 * electrónico inválido" is frozenApp's `invalid_email`. The rest is mine and
 * unreviewed.
 */
object ProfileCopy {

    // Inline feedback, shown as the user types.

    val PHONE_TOO_SHORT: String get() = localized(
        en = "Phone should be 10 digits.",
        es = "El número de teléfono debe tener 10 dígitos.",
    )

    val EMAIL_INCOMPLETE: String get() = localized(
        en = "Email appears incomplete.",
        es = "El correo electrónico parece incompleto.",
    )

    val ACKNOWLEDGE_TERMS: String get() = localized(
        en = "Please acknowledge FCC challenge sharing terms.",
        es = "Por favor reconozca los términos de compartir de la impugnación FCC.",
    )

    // Field errors, raised on save.

    val NAME_REQUIRED: String get() = localized(
        en = "Name is required.",
        es = "El nombre es obligatorio.",
    )

    val PHONE_INVALID: String get() = localized(
        en = "Phone must be 10 digits (###-###-####).",
        es = "El número de teléfono debe tener 10 dígitos (###-###-####).",
    )

    /** frozenApp `invalid_email`. */
    val EMAIL_INVALID: String get() = localized(
        en = "Email is invalid.",
        es = "Correo electrónico inválido.",
    )

    val ACKNOWLEDGEMENT_REQUIRED: String get() = localized(
        en = "FCC acknowledgement is required.",
        es = "Se requiere el reconocimiento de la FCC.",
    )

    /**
     * The switch on the profile and Settings forms.
     *
     * Shorter than the full disclosure in
     * [edu.gatech.cc.cellwatch.domain.consent.ConsentCopy.FCC_ACKNOWLEDGEMENT],
     * which is what the consent step shows; this is the reminder next to the
     * toggle once consent has already been given.
     */
    val FCC_ACK_LABEL: String get() = localized(
        en = "I acknowledge the FCC challenge sharing terms.",
        es = "Reconozco los términos de compartir de la impugnación FCC.",
    )
}
