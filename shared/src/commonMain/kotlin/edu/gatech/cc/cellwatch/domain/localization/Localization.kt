package edu.gatech.cc.cellwatch.domain.localization

/**
 * The device's language, as a language tag or two-letter code.
 *
 * `expect` rather than a library: moko-resources is the usual KMP answer, but
 * its latest release (0.24.5, February 2025) is built against kotlin-stdlib
 * 1.9.20 - the version family this project had to leave behind to take
 * h3-kmp - and it generates code through a Gradle plugin tied to the compiler.
 * Reading one value per platform costs about ten lines and cannot block a
 * future Kotlin upgrade.
 */
expect fun currentLanguageCode(): String

/**
 * Picks a translation for the device's language.
 *
 * Both arguments are built before either is chosen, so callers can use plain
 * string templates for the interpolated cases - `"$count registros"` - rather
 * than a placeholder syntax nobody can grep for. The cost is one discarded
 * string per call, which is nothing next to the clarity.
 *
 * English is the fallback for anything untranslated, which is the honest
 * behaviour: a partially translated screen would be worse than a consistent
 * one.
 */
internal fun localized(
    en: String,
    es: String,
    language: String = currentLanguageCode(),
): String = if (language.lowercase().startsWith("es")) es else en

/**
 * Whether the device is being addressed in Spanish.
 *
 * For the handful of places that need to branch on language rather than pick
 * between two strings - month abbreviations, say, where the alternative is a
 * twelve-way `localized` call.
 */
internal fun isSpanish(language: String = currentLanguageCode()): Boolean =
    language.lowercase().startsWith("es")

/**
 * The language tag to pass when output must not follow the device.
 *
 * Exported files are the case that matters: an extended export can end up in
 * front of the FCC or the research team, and a sentence that changes language
 * with the phone's settings is a data-quality problem, not a courtesy.
 */
internal const val ENGLISH = "en"
