package edu.gatech.cc.cellwatch.domain.maphome

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * What the map home screen says, in both languages.
 *
 * Mine, not frozenApp's - frozenApp's map screen had no status panel - so the
 * Spanish is unreviewed. See
 * [edu.gatech.cc.cellwatch.domain.sync.SyncCopy] for why that is acceptable
 * here and not for the consent disclosures.
 */
object MapHomeCopy {

    /** The product name. Not translated, and not expected to be. */
    const val TITLE = "CellWatch"

    val SUBTITLE: String get() = localized(en = "Map home", es = "Mapa principal")

    val MAP_PANEL_TITLE: String get() = localized(
        en = "Measurement map",
        es = "Mapa de mediciones",
    )

    val SYNC_UNKNOWN: String get() = localized(
        en = "Sync status unknown.",
        es = "Estado de sincronización desconocido.",
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

    val NO_SAVED_MEASUREMENTS: String get() = localized(
        en = "No saved measurements yet. Take a measurement to populate the map.",
        es = "Todavía no hay mediciones guardadas. Tome una medición para llenar el mapa.",
    )

    fun showingRuns(count: Int): String = when (count) {
        1 -> localized(
            en = "Showing 1 saved run on the map.",
            es = "Mostrando 1 sesión guardada en el mapa.",
        )
        else -> localized(
            en = "Showing $count saved runs on the map (newest first).",
            es = "Mostrando $count sesiones guardadas en el mapa (la más reciente primero).",
        )
    }

    val COMPLETE_PROFILE_FIRST: String get() = localized(
        en = "Complete your profile before taking a measurement.",
        es = "Complete su perfil antes de tomar una medición.",
    )

    val PROFILE_SAVED: String get() = localized(
        en = "Profile saved. Start a measurement when ready.",
        es = "Perfil guardado. Comience una medición cuando esté listo.",
    )

    val RECENT_PENDING: String get() = localized(
        en = "Recent measurements available. Some uploads are pending.",
        es = "Hay mediciones recientes disponibles. Algunas subidas están pendientes.",
    )

    val RECENT_SYNCED: String get() = localized(
        en = "Recent measurements available and synced.",
        es = "Hay mediciones recientes disponibles y sincronizadas.",
    )

    val RECENT_AVAILABLE: String get() = localized(
        en = "Recent measurements available.",
        es = "Hay mediciones recientes disponibles.",
    )

    val NO_POINTS_YET: String get() = localized(
        en = "No measurement location points available yet.",
        es = "Todavía no hay puntos de ubicación de mediciones.",
    )

    fun showingPoints(points: Int, cells: Int): String = localized(
        en = "Showing $points point${if (points == 1) "" else "s"} in $cells grid " +
            "cell${if (cells == 1) "" else "s"}.",
        es = "Mostrando $points punto${if (points == 1) "" else "s"} en $cells " +
            "celda${if (cells == 1) "" else "s"} de la cuadrícula.",
    )

    /** frozenApp `back_to_map` and `measure`. */
    val BACK_TO_MAP: String get() = localized(en = "Back to map", es = "Regresar al mapa")
    val MEASURE: String get() = localized(en = "Measure", es = "Medir")

    /** frozenApp `hex`, for the overlay toggle. */
    val HEX_GRID: String get() = localized(en = "Hex grid", es = "Cuadrícula hexagonal")

    val SHOW_POINTS_ONLY: String get() = localized(
        en = "Show points only",
        es = "Mostrar solo los puntos",
    )

    val SHOW_COVERAGE_GRID: String get() = localized(
        en = "Show coverage grid",
        es = "Mostrar la cuadrícula de cobertura",
    )
}
