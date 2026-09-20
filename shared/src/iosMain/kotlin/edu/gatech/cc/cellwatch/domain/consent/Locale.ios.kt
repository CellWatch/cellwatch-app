package edu.gatech.cc.cellwatch.domain.consent

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The first preferred language, not the region locale.
 *
 * `preferredLanguages` is what the user actually ordered in Settings; it
 * yields tags like "es-US", so the caller matches on the prefix rather than
 * equality - a Spanish speaker in the United States should still get Spanish.
 */
actual fun currentLanguageCode(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
