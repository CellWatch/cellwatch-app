package edu.gatech.cc.cellwatch.domain.settings

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * Wording specific to the Settings screen.
 *
 * The field-level sentences come from
 * [edu.gatech.cc.cellwatch.domain.profile.ProfileCopy], shared with
 * onboarding. Mine, and unreviewed, except where noted.
 */
object SettingsCopy {

    /** frozenApp `settings`. */
    val TITLE: String get() = localized(en = "Settings", es = "Ajustes")

    val UPDATE_AND_SAVE: String get() = localized(
        en = "Update your settings and save.",
        es = "Actualice sus ajustes y guárdelos.",
    )

    val LOOKS_GOOD: String get() = localized(
        en = "Looks good. Tap Save Settings.",
        es = "Todo bien. Toque Guardar ajustes.",
    )

    val SAVED_SETTINGS_LOADED: String get() = localized(
        en = "Saved settings loaded.",
        es = "Se cargaron los ajustes guardados.",
    )

    val SETTINGS_SAVED: String get() = localized(
        en = "Settings saved.",
        es = "Ajustes guardados.",
    )

    val FIX_ERRORS: String get() = localized(
        en = "Fix validation errors and save again.",
        es = "Corrija los errores y guarde otra vez.",
    )

    val SAVE_SETTINGS: String get() = localized(en = "Save settings", es = "Guardar ajustes")

    val SUBMIT_TO_CHALLENGE: String get() = localized(
        en = "Submit measurements to the FCC challenge",
        es = "Enviar las mediciones a la impugnación de la FCC",
    )

    val ABOUT_THIS_INSTALL: String get() = localized(
        en = "About this install",
        es = "Sobre esta instalación",
    )

    val LOADING: String get() = localized(en = "Loading…", es = "Cargando…")

    val DETAILS_NOTE: String get() = localized(
        en = "These details accompany every submission. Changing them affects future " +
            "measurements, not ones already uploaded.",
        es = "Estos datos acompañan cada envío. Cambiarlos afecta las mediciones futuras, no " +
            "las que ya se subieron.",
    )

    val SUBMIT_NOTE: String get() = localized(
        en = "With this off, measurements are still taken and saved, but no FCC submission " +
            "is created for them.",
        es = "Con esto desactivado, las mediciones igual se toman y se guardan, pero no se crea " +
            "ningún envío a la FCC para ellas.",
    )

    /** frozenApp `device_id`. */
    fun deviceId(value: String): String =
        localized(en = "Device ID: $value", es = "Identificación de dispositivo: $value")

    fun measurementServer(mode: String, endpoint: String): String = localized(
        en = "Measurement server: $mode ($endpoint)",
        es = "Servidor de medición: $mode ($endpoint)",
    )

    fun uploadTarget(mode: String): String = localized(
        en = "Upload target: $mode",
        es = "Destino de subida: $mode",
    )
}
