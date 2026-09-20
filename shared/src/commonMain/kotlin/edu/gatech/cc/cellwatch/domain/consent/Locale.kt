package edu.gatech.cc.cellwatch.domain.consent

/**
 * The device's language, as a two-letter code.
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
 * English is the fallback for anything untranslated, which is the honest
 * behaviour: showing a partially translated consent screen would be worse
 * than showing a consistent one.
 */
internal fun localized(
    en: String,
    es: String,
    language: String = currentLanguageCode(),
): String = if (language.lowercase().startsWith("es")) es else en
