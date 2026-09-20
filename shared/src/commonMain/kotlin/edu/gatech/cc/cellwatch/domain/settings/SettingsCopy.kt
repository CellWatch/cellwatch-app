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

    // Purging local measurements.

    val DELETE_DATA_TITLE: String get() = localized(
        en = "Delete measurements",
        es = "Eliminar las mediciones",
    )

    val DELETE_DATA_BUTTON: String get() = localized(
        en = "Delete all measurements on this device",
        es = "Eliminar todas las mediciones de este dispositivo",
    )

    /**
     * Says plainly what the button cannot do.
     *
     * There is no API to recall an upload, so a user who deletes locally to
     * "take back" a submission would be misled by anything vaguer. Export is
     * named because it is the only way to keep a copy.
     */
    val DELETE_DATA_EXPLANATION: String get() = localized(
        en = "Removes every saved measurement from this phone. Anything already uploaded stays " +
            "on the CellWatch server, and anything already submitted stays with the FCC - " +
            "deleting here cannot take those back. Export your data first if you want to keep " +
            "a copy.",
        es = "Elimina de este teléfono todas las mediciones guardadas. Lo que ya se subió " +
            "permanece en el servidor de CellWatch, y lo que ya se envió permanece con la FCC: " +
            "eliminarlas aquí no las puede recuperar. Exporte sus datos primero si desea " +
            "guardar una copia.",
    )

    val DELETE_DATA_CONFIRM_TITLE: String get() = localized(
        en = "Delete all measurements?",
        es = "¿Eliminar todas las mediciones?",
    )

    val DELETE_DATA_CONFIRM: String get() = localized(en = "Delete", es = "Eliminar")

    val DELETE_DATA_CANCEL: String get() = localized(en = "Cancel", es = "Cancelar")

    fun deleteDataDone(removed: Int): String = localized(
        en = "Deleted $removed measurement${if (removed == 1) "" else "s"} from this device.",
        es = "Se eliminaron $removed medici${if (removed == 1) "ón" else "ones"} de este dispositivo.",
    )
}
