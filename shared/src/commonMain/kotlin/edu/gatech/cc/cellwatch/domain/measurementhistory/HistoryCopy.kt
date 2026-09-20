package edu.gatech.cc.cellwatch.domain.measurementhistory

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * What the History screen says.
 *
 * "1 medición" / "%d mediciones" and the three test names are frozenApp's.
 * The rest is mine and unreviewed.
 */
object HistoryCopy {

    val TITLE: String get() = localized(en = "History & sync", es = "Historial y sincronización")

    val NO_MEASUREMENTS_YET: String get() = localized(
        en = "No measurements yet",
        es = "Todavía no hay mediciones",
    )

    val RUN_YOUR_FIRST: String get() = localized(
        en = "Run your first measurement to populate history.",
        es = "Tome su primera medición para llenar el historial.",
    )

    val LATEST_MEASUREMENT: String get() = localized(
        en = "Latest measurement",
        es = "Medición más reciente",
    )

    val LATEST_CAPTURED: String get() = localized(
        en = "Latest measurement captured.",
        es = "Se capturó la medición más reciente.",
    )

    val EMPTY_HINT: String get() = localized(
        en = "Measurements you take will be listed here, newest first.",
        es = "Las mediciones que tome aparecerán aquí, la más reciente primero.",
    )

    val MEASUREMENT_DETAILS: String get() = localized(
        en = "Measurement details",
        es = "Detalles de la medición",
    )

    val SELECTED_RUN: String get() = localized(en = "Selected run", es = "Sesión seleccionada")

    val SELECTED: String get() = localized(en = "Selected", es = "Seleccionada")

    val RETRY_UPLOAD: String get() = localized(en = "Retry upload", es = "Reintentar la subida")

    val RETRYING: String get() = localized(en = "Retrying…", es = "Reintentando…")

    val SYNC: String get() = localized(en = "Sync", es = "Sincronizar")

    val SYNC_UNKNOWN_TAP_REFRESH: String get() = localized(
        en = "Sync status unknown. Tap refresh.",
        es = "Estado de sincronización desconocido. Toque actualizar.",
    )

    val ALL_SYNCED: String get() = localized(
        en = "All records are synced.",
        es = "Todos los registros están sincronizados.",
    )

    fun pendingQueue(measurements: Int, submissions: Int): String = localized(
        en = "Pending sync queue: $measurements measurement " +
            "record${if (measurements == 1) "" else "s"}, $submissions submission " +
            "record${if (submissions == 1) "" else "s"}.",
        es = "Cola de sincronización pendiente: $measurements " +
            "registro${if (measurements == 1) "" else "s"} de medición, $submissions " +
            "registro${if (submissions == 1) "" else "s"} de envío.",
    )

    /** frozenApp `n_measurements`, with the cap note appended. */
    fun measurementCount(total: Int, showing: Int): String {
        val capped = showing < total
        return localized(
            en = "$total measurement${if (total == 1) "" else "s"}" +
                if (capped) " — showing the $showing most recent" else "",
            es = "$total medici${if (total == 1) "ón" else "ones"}" +
                if (capped) " — mostrando l${if (showing == 1) "a" else "as"} $showing más " +
                    "reciente${if (showing == 1) "" else "s"}" else "",
        )
    }

    /** One list row: the three results of a single run. */
    fun runSummary(index: Int, latency: String, download: String, upload: String): String = localized(
        en = "Run $index: $latency latency, $download download, $upload upload",
        es = "Sesión $index: $latency de latencia, $download de descarga, $upload de subida",
    )

    /** frozenApp `latency`, `download`, `upload`. */
    fun detailBlock(latency: String, download: String, upload: String, sync: String): String =
        localized(
            en = "Latency: $latency\nDownload: $download\nUpload: $upload\nSync: $sync",
            es = "Latencia: $latency\nDescarga: $download\nSubida: $upload\nSincronización: $sync",
        )

    /** frozenApp `measurement_history`, shortened for the navigation bar. */
    val NAV_TITLE: String get() = localized(en = "History", es = "Historial")

    val EXPORT_DATA: String get() = localized(en = "Export data", es = "Exportar los datos")
}
