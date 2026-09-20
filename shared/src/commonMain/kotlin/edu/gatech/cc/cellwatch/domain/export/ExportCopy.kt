package edu.gatech.cc.cellwatch.domain.export

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * The Export screen.
 *
 * What is on screen is translated; what goes *into* the exported file is not -
 * see `deriveFccOutcome`, which pins its sentences to English because an
 * export may be read by the FCC or the research team rather than by the
 * person who produced it.
 *
 * frozenApp had an export button ("Exportar") but no format chooser, so the
 * descriptions are mine and unreviewed.
 */
object ExportCopy {

    val TITLE: String get() = localized(en = "Export data", es = "Exportar los datos")

    /** frozenApp `export_button`. */
    val NAV_TITLE: String get() = localized(en = "Export", es = "Exportar")

    val CHOOSE_FORMAT: String get() = localized(
        en = "Choose a format to export.",
        es = "Elija un formato para exportar.",
    )

    val FCC_FILE_TITLE: String get() = localized(
        en = "FCC submission file",
        es = "Archivo de envío a la FCC",
    )

    val FCC_FILE_DESCRIPTION: String get() = localized(
        en = "The format the FCC accepts for a challenge submission. It contains only " +
            "measurements that qualified: taken over cellular, complete, and with " +
            "submission turned on. If none qualified, this file will be empty.",
        es = "El formato que acepta la FCC para un envío de impugnación. Contiene solo las " +
            "mediciones que calificaron: tomadas por conexión celular, completas y con el " +
            "envío activado. Si ninguna calificó, este archivo estará vacío.",
    )

    val FULL_EXPORT_TITLE: String get() = localized(
        en = "Full export",
        es = "Exportación completa",
    )

    val FULL_EXPORT_DESCRIPTION: String get() = localized(
        en = "Everything this device recorded, including measurements the FCC file leaves " +
            "out and the reason each one was left out. Also records what the device " +
            "could not report — missing permissions, unavailable telephony — which the " +
            "FCC format has no field for.",
        es = "Todo lo que registró este dispositivo, incluso las mediciones que el archivo de " +
            "la FCC deja fuera y el motivo por el cual se dejó fuera cada una. También " +
            "registra lo que el dispositivo no pudo reportar — permisos faltantes, telefonía " +
            "no disponible — para lo cual el formato de la FCC no tiene ningún campo.",
    )

    val EXPORT_FCC_FILE: String get() = localized(
        en = "Export FCC submission file",
        es = "Exportar el archivo de envío a la FCC",
    )

    val EXPORT_FULL_DATA: String get() = localized(
        en = "Export full data",
        es = "Exportar todos los datos",
    )

    val PREPARING_FCC_FILE: String get() = localized(
        en = "Preparing FCC file…",
        es = "Preparando el archivo de la FCC…",
    )

    val PREPARING_FULL_EXPORT: String get() = localized(
        en = "Preparing full export…",
        es = "Preparando la exportación completa…",
    )

    fun ready(label: String, recordCount: Int): String = localized(
        en = "$label ready: $recordCount record${if (recordCount == 1) "" else "s"}. " +
            "Choose where to save it.",
        es = "$label listo: $recordCount registro${if (recordCount == 1) "" else "s"}. " +
            "Elija dónde guardarlo.",
    )

    fun savedTo(fileName: String): String = localized(
        en = "Saved to $fileName.",
        es = "Se guardó en $fileName.",
    )

    fun notSaved(reason: String): String = localized(
        en = "Export not saved: $reason",
        es = "No se guardó la exportación: $reason",
    )

    val NO_LOCATION_CHOSEN: String get() = localized(
        en = "no location chosen",
        es = "no se eligió ninguna ubicación",
    )

    val COULD_NOT_OPEN_FILE: String get() = localized(
        en = "could not open the chosen file",
        es = "no se pudo abrir el archivo elegido",
    )

    val UNKNOWN_ERROR: String get() = localized(en = "unknown error", es = "error desconocido")
}
