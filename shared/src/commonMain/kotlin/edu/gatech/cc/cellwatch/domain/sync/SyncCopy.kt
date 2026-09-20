package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.localization.isSpanish
import edu.gatech.cc.cellwatch.domain.localization.localized
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Everything the sync surfaces say, in both languages.
 *
 * Unlike [edu.gatech.cc.cellwatch.domain.consent.ConsentCopy], none of this
 * has a frozenApp original to copy: frozenApp had no sync status vocabulary.
 * The Spanish here is therefore mine and unreviewed. That is an acceptable
 * trade for status text - it tells a user what the app is doing, not what
 * they are agreeing to - but it should not be mistaken for the reviewed
 * consent wording.
 *
 * Interpolated strings are written as ordinary templates in both languages
 * rather than as a shared format string with positional holes. Spanish does
 * not always want the number in the same place, and a translator reading
 * `"Faltan $n registros"` can see what they are changing.
 */
object SyncCopy {

    val FCC_NOTE: String get() = localized(
        en = "Submission to the FCC happens separately, later.",
        es = "El envío a la FCC ocurre por separado, más tarde.",
    )

    val UPLOADING: String get() = localized(
        en = "Uploading to the CellWatch server…",
        es = "Subiendo al servidor de CellWatch…",
    )

    val NOTHING_TO_UPLOAD: String get() = localized(
        en = "Nothing to upload yet.",
        es = "Todavía no hay nada que subir.",
    )

    val TAKE_A_MEASUREMENT: String get() = localized(
        en = "Take a measurement to get started.",
        es = "Tome una medición para comenzar.",
    )

    val NOT_UPLOADED_YET: String get() = localized(
        en = "Not uploaded yet.",
        es = "Todavía no se ha subido nada.",
    )

    val WILL_BE_RETRIED: String get() = localized(
        en = " They are saved on this device and will be retried. ",
        es = " Están guardados en este dispositivo y se volverán a intentar. ",
    )

    val NOTHING_UPLOADED_SUCCESSFULLY: String get() = localized(
        en = "Nothing has uploaded successfully yet. ",
        es = "Todavía no se ha subido nada con éxito. ",
    )

    val NO_PENDING_UPLOADS: String get() = localized(
        en = "No pending uploads.",
        es = "No hay subidas pendientes.",
    )

    val SYNC_IN_PROGRESS: String get() = localized(
        en = "Sync in progress.",
        es = "Sincronización en curso.",
    )

    val SYNC_COMPLETE: String get() = localized(en = "Sync complete.", es = "Sincronización completa.")

    val SYNC_PARTIAL: String get() = localized(
        en = "Sync partially completed.",
        es = "Sincronización completada parcialmente.",
    )

    val SYNC_FAILED: String get() = localized(en = "Sync failed.", es = "La sincronización falló.")

    val SYNC_STILL_PENDING: String get() = localized(
        en = "Sync still pending.",
        es = "La sincronización sigue pendiente.",
    )

    fun lastAttemptFailed(at: String?): String = localized(
        en = "Last upload attempt failed${at?.let { " $it" }.orEmpty()}.",
        es = "El último intento de subida falló${at?.let { " $it" }.orEmpty()}.",
    )

    fun lastUploaded(at: String): String = localized(
        en = "Last uploaded $at.",
        es = "Subido por última vez $at.",
    )

    fun lastSuccessfulUpload(at: String): String = localized(
        en = "Last successful upload was $at. ",
        es = "La última subida con éxito fue $at. ",
    )

    fun totalUploaded(count: Int): String = localized(
        en = "$count record${if (count == 1) "" else "s"} uploaded to the CellWatch server. ",
        es = "$count registro${if (count == 1) "" else "s"} subido${if (count == 1) "" else "s"} " +
            "al servidor de CellWatch. ",
    )

    fun pendingPhrase(pendingRecords: Int): String = when (pendingRecords) {
        0 -> localized(
            en = "Nothing is waiting to upload.",
            es = "No hay nada esperando para subir.",
        )
        1 -> localized(
            en = "1 record is waiting to upload.",
            es = "1 registro está esperando para subir.",
        )
        else -> localized(
            en = "$pendingRecords records are waiting to upload.",
            es = "$pendingRecords registros están esperando para subir.",
        )
    }

    /**
     * Relative for anything recent, absolute beyond that.
     *
     * A bare timestamp for something a minute old reads as stale, and a
     * relative one for something four days old is useless for deciding
     * whether to worry.
     */
    fun describe(at: Instant, now: Instant, timeZone: TimeZone): String {
        val seconds = (now - at).inWholeSeconds
        return when {
            seconds < 60 -> localized(en = "just now", es = "ahora mismo")
            seconds < 3_600 -> {
                val minutes = seconds / 60
                localized(
                    en = "$minutes minute${if (minutes == 1L) "" else "s"} ago",
                    es = "hace $minutes minuto${if (minutes == 1L) "" else "s"}",
                )
            }
            seconds < 86_400 -> {
                val hours = seconds / 3_600
                localized(
                    en = "$hours hour${if (hours == 1L) "" else "s"} ago",
                    es = "hace $hours hora${if (hours == 1L) "" else "s"}",
                )
            }
            else -> localized(
                en = "on ${formatDate(at, timeZone)}",
                es = "el ${formatDate(at, timeZone)}",
            )
        }
    }

    /**
     * Day, month, year and 24-hour clock in both languages.
     *
     * The month names were a hardcoded English list, so a fully Spanish
     * screen still said "7 Sep 2026 at 14:03". Spanish abbreviations are
     * lower case, and the connector is "a las", not "at".
     *
     * The order stays day-month-year in both, which is right for Spanish and
     * is what frozenApp used for English too.
     */
    fun formatDate(at: Instant, timeZone: TimeZone): String {
        val local = at.toLocalDateTime(timeZone)
        val months = if (isSpanish()) MONTHS_ES else MONTHS_EN
        val month = months[local.monthNumber - 1]
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        val at24 = localized(en = "at", es = "a las")
        return "${local.dayOfMonth} $month ${local.year} $at24 $hour:$minute"
    }

    private val MONTHS_EN = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    private val MONTHS_ES = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic",
    )
}
