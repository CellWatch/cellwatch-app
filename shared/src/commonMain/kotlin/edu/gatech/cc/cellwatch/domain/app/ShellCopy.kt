package edu.gatech.cc.cellwatch.domain.app

import edu.gatech.cc.cellwatch.domain.consent.ConsentCopy
import edu.gatech.cc.cellwatch.domain.export.ExportCopy
import edu.gatech.cc.cellwatch.domain.localization.localized
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeCopy
import edu.gatech.cc.cellwatch.domain.measurementhistory.HistoryCopy
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunCopy
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartCopy
import edu.gatech.cc.cellwatch.domain.navigation.Destination
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingCopy
import edu.gatech.cc.cellwatch.domain.settings.SettingsCopy

/**
 * The navigation shell: screen titles the two hosts put in their bars, and
 * the failures that stop a screen from opening at all.
 *
 * Both shells kept their own copy of this list, in English, which is how the
 * action bar came to say "Data use" above a Spanish screen. Shared now, so a
 * title cannot drift from the screen under it - or from the other platform.
 *
 * Mine, and unreviewed.
 */
object ShellCopy {

    /**
     * The title for a destination's bar.
     *
     * Both shells carried their own copy of this mapping, which is how the
     * Android bar could say "Data use" while the screen under it said "Uso de
     * datos": two lists, one edited. One list now, and the compiler catches a
     * destination nobody gave a title.
     */
    fun title(destination: Destination): String = when (destination) {
        is Destination.DataUse -> ConsentCopy.DATA_USE_TITLE
        is Destination.CollectionChoice -> ConsentCopy.COLLECTION_MODE_TITLE
        is Destination.Onboarding -> OnboardingCopy.TITLE
        is Destination.MapHome -> MapHomeCopy.SUBTITLE
        is Destination.MeasurementStart -> MeasurementStartCopy.TITLE
        is Destination.MeasurementRun -> MeasurementRunCopy.TITLE
        is Destination.History -> HistoryCopy.NAV_TITLE
        is Destination.Settings -> SettingsCopy.TITLE
        is Destination.Export -> ExportCopy.NAV_TITLE
        is Destination.BlockingError -> CANNOT_START
    }

    /** frozenApp `ready` - the word it used for the same "finished" button. */
    val DONE: String get() = localized(en = "Done", es = "Listo")

    val CANNOT_START: String get() = localized(en = "Cannot start", es = "No se puede comenzar")

    /** Substituted when a screen cannot open and nothing more specific is known. */
    val RUNTIME_CONFIG_MISSING: String get() = localized(
        en = "runtime configuration missing",
        es = "falta la configuración de ejecución",
    )

    fun measurementUnavailable(reason: String): String = localized(
        en = "Measurement is unavailable: $reason",
        es = "La medición no está disponible: $reason",
    )

    fun historyUnavailable(reason: String): String = localized(
        en = "History is unavailable: $reason",
        es = "El historial no está disponible: $reason",
    )

    fun settingsUnavailable(reason: String): String = localized(
        en = "Settings are unavailable: $reason",
        es = "Los ajustes no están disponibles: $reason",
    )

    fun exportUnavailable(reason: String): String = localized(
        en = "Export is unavailable: $reason",
        es = "La exportación no está disponible: $reason",
    )

    /**
     * Deliberately English: this is a developer placeholder for a route that
     * has not been built, and it points at a file in the repository. Nobody
     * outside the team should ever see it, and translating a pointer to
     * `UI_DELIVERY_PLAN.md` would only make it harder to act on.
     */
    const val NOT_BUILT_YET =
        "This screen is not built yet. See UI_DELIVERY_PLAN.md for where it lands."
}
