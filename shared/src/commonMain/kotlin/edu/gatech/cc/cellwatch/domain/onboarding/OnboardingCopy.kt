package edu.gatech.cc.cellwatch.domain.onboarding

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * Wording specific to first-run profile entry.
 *
 * The field-level sentences live in
 * [edu.gatech.cc.cellwatch.domain.profile.ProfileCopy], which Settings shares.
 * Only what names *this* screen - "Save Profile" rather than "Save Settings" -
 * is here. Mine, and unreviewed.
 */
object OnboardingCopy {

    val TITLE: String get() = localized(en = "Your profile", es = "Su perfil")

    val PROMPT: String get() = localized(
        en = "Tell us who you are before starting measurements. These details accompany every " +
            "submission.",
        es = "Díganos quién es usted antes de comenzar a medir. Estos datos acompañan cada " +
            "envío.",
    )

    val COMPLETE_THE_FORM: String get() = localized(
        en = "Complete the form and save your profile.",
        es = "Complete el formulario y guarde su perfil.",
    )

    val LOOKS_GOOD: String get() = localized(
        en = "Looks good. Tap Save Profile.",
        es = "Todo bien. Toque Guardar perfil.",
    )

    val SAVED_PROFILE_LOADED: String get() = localized(
        en = "Saved profile loaded. You can edit and save again.",
        es = "Se cargó el perfil guardado. Puede editarlo y guardarlo otra vez.",
    )

    val PROFILE_SAVED: String get() = localized(en = "Profile saved.", es = "Perfil guardado.")

    val FIX_ERRORS: String get() = localized(
        en = "Fix validation errors and try again.",
        es = "Corrija los errores y vuelva a intentarlo.",
    )

    val SAVE_PROFILE: String get() = localized(en = "Save profile", es = "Guardar perfil")

    /** frozenApp `name`, `phone_hint` and `email`. */
    val FULL_NAME: String get() = localized(en = "Full name", es = "Nombre completo")
    val PHONE_HINT: String get() = localized(
        en = "Phone (###-###-####)",
        es = "Número de teléfono (###-###-####)",
    )
    val EMAIL: String get() = localized(en = "Email", es = "Correo electrónico")
}
