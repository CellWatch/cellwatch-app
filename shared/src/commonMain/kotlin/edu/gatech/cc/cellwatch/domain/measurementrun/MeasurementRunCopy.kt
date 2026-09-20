package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * What the run screen says while a measurement is happening.
 *
 * The stage headers are frozenApp's, Spanish included: this app runs the same
 * three tests in the same order, and there was no reason to invent new words
 * for "Midiendo latencia". The states frozenApp had no equivalent for -
 * anything about syncing, and the cancelled case - are mine, and unreviewed.
 */
object MeasurementRunCopy {

    // Stage headers - frozenApp `measuring`, `finding_server`,
    // `measuring_latency`, `measuring_download`, `measuring_upload`,
    // `measurement_complete`, `measurement_failed`.
    val MEASURING: String get() = localized(en = "Measuring", es = "Midiendo")
    val FINDING_SERVER: String get() = localized(en = "Finding server", es = "Encontrando servidor")
    val MEASURING_LATENCY: String get() = localized(
        en = "Measuring latency",
        es = "Midiendo latencia",
    )
    val MEASURING_DOWNLOAD: String get() = localized(
        en = "Measuring download speed",
        es = "Midiendo velocidad de descarga",
    )
    val MEASURING_UPLOAD: String get() = localized(
        en = "Measuring upload speed",
        es = "Midiendo velocidad de subida",
    )
    val MEASUREMENT_COMPLETE: String get() = localized(
        en = "Measurement complete",
        es = "Medición completa",
    )
    val MEASUREMENT_FAILED: String get() = localized(
        en = "Measurement failed",
        es = "Medición fallida",
    )

    /** frozenApp had no cancel, so this one is mine. */
    val MEASUREMENT_CANCELLED: String get() = localized(
        en = "Measurement cancelled",
        es = "Medición cancelada",
    )

    fun measurementFailed(reason: String): String = localized(
        en = "Measurement failed: $reason",
        es = "Medición fallida: $reason",
    )

    // Upload state of a finished run. "Subido" and "Pendiente" are
    // frozenApp's; the other two are mine.
    val UPLOADED: String get() = localized(en = "Uploaded", es = "Subido")
    val PENDING_SYNC: String get() = localized(en = "Pending sync", es = "Pendiente de sincronizar")
    val NOT_UPLOADED: String get() = localized(en = "Not uploaded", es = "No subido")
    val IN_PROGRESS: String get() = localized(en = "In progress", es = "En curso")

    val COMPLETE_AND_SYNCED: String get() = localized(
        en = "Measurement complete. Results saved and synced.",
        es = "Medición completa. Los resultados se guardaron y se sincronizaron.",
    )

    val COMPLETE_SYNC_ATTEMPTED: String get() = localized(
        en = "Measurement complete. Results saved and sync attempted.",
        es = "Medición completa. Los resultados se guardaron y se intentó sincronizar.",
    )

    val FAILED_TRY_AGAIN: String get() = localized(
        en = "Measurement failed. Please try again.",
        es = "La medición falló. Por favor inténtelo de nuevo.",
    )

    /** frozenApp `measurement_in_progress`, with its full stop added. */
    val IN_PROGRESS_SENTENCE: String get() = localized(
        en = "Measurement in progress.",
        es = "Medición en curso.",
    )

    /**
     * Placeholder for a value that has not arrived, and the two floor
     * readings. Units are not translated - "ms" and "Mbps" are the same in
     * both, which is why frozenApp's `%1$d ms` and `%1$d Mbps` are identical
     * across its two resource files.
     */
    const val NO_VALUE = "--"
    const val SUB_MILLISECOND = "<1 ms"
    const val SUB_TENTH_MBPS = "<0.1 Mbps"

    /** frozenApp `measurement_notifications_name`, used as the screen title. */
    val TITLE: String get() = localized(en = "Measurement", es = "Medición")

    /** frozenApp `take_another_measurement`. */
    val MEASURE_AGAIN: String get() = localized(en = "Measure again", es = "Medir otra vez")

    /** frozenApp `latency`, `download`, `upload` - the three test names. */
    val LATENCY: String get() = localized(en = "Latency", es = "Latencia")
    val DOWNLOAD: String get() = localized(en = "Download", es = "Descarga")
    val UPLOAD: String get() = localized(en = "Upload", es = "Subida")

    /**
     * The same word in both languages, so it is `const` rather than a
     * `localized` pair that happens to repeat itself - which is also what
     * keeps CopyLocaleTest's "every string must differ" rule absolute
     * instead of carrying an exception list.
     *
     * It stays "Jitter" because that is the term the FCC's own submission
     * format uses, and anyone comparing the app against their BDC file
     * should see the same word.
     */
    const val JITTER = "Jitter"

    val PACKET_LOSS: String get() = localized(
        en = "Packet loss",
        es = "Pérdida de paquetes",
    )

    fun packetLoss(percent: String, lost: Int, sent: Int): String = localized(
        en = "$percent% ($lost of $sent lost)",
        es = "$percent% ($lost de $sent perdidos)",
    )

    val SYNC: String get() = localized(en = "Sync", es = "Sincronización")

    /** frozenApp `pending`. */
    val PENDING: String get() = localized(en = "Pending", es = "Pendiente")

    fun syncLabel(value: String): String = "$SYNC: $value"
}
