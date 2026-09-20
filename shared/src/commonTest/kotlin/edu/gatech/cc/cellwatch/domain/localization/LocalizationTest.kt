package edu.gatech.cc.cellwatch.domain.localization

import edu.gatech.cc.cellwatch.domain.consent.ConsentCopy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationTest {

    @Test
    fun spanishIsChosenForAnySpanishLanguageTag() {
        assertEquals("es", localized(en = "en", es = "es", language = "es"))
        // Region-qualified tags are what iOS actually reports: a Spanish
        // speaker in the United States gets "es-US" and must still get Spanish.
        assertEquals("es", localized(en = "en", es = "es", language = "es-US"))
        assertEquals("es", localized(en = "en", es = "es", language = "ES"))
    }

    @Test
    fun anythingElseFallsBackToEnglish() {
        assertEquals("en", localized(en = "en", es = "es", language = "en"))
        assertEquals("en", localized(en = "en", es = "es", language = "en-GB"))
        // An untranslated language gets consistent English rather than a
        // half-translated consent screen.
        assertEquals("en", localized(en = "en", es = "es", language = "fr"))
        assertEquals("en", localized(en = "en", es = "es", language = ""))
    }

    @Test
    fun everyDisclosureHasCopyInTheDeviceLanguage() {
        // Whichever language the host reports, no disclosure may come back
        // empty. That the two languages actually differ is checked on the JVM
        // by CopyLocaleTest, which can swap the default locale.
        val disclosures = listOf(
            "DATA_USE_TITLE" to ConsentCopy::DATA_USE_TITLE,
            "DATA_USE_SHARED" to ConsentCopy::DATA_USE_SHARED,
            "DATA_USE_RESEARCH" to ConsentCopy::DATA_USE_RESEARCH,
            "DATA_USE_POLICY" to ConsentCopy::DATA_USE_POLICY,
            "PRIVACY_POLICY_LABEL" to ConsentCopy::PRIVACY_POLICY_LABEL,
            "COLLECTION_MODE_TITLE" to ConsentCopy::COLLECTION_MODE_TITLE,
            "COLLECTION_MODE_DESCRIPTION" to ConsentCopy::COLLECTION_MODE_DESCRIPTION,
            "CHALLENGE_MODE_TITLE" to ConsentCopy::CHALLENGE_MODE_TITLE,
            "CHALLENGE_MODE_DESCRIPTION" to ConsentCopy::CHALLENGE_MODE_DESCRIPTION,
            "TESTING_MODE_TITLE" to ConsentCopy::TESTING_MODE_TITLE,
            "TESTING_MODE_DESCRIPTION" to ConsentCopy::TESTING_MODE_DESCRIPTION,
            "FCC_INFO_TITLE" to ConsentCopy::FCC_INFO_TITLE,
            "FCC_INFO_DESCRIPTION" to ConsentCopy::FCC_INFO_DESCRIPTION,
            "FCC_ACKNOWLEDGEMENT" to ConsentCopy::FCC_ACKNOWLEDGEMENT,
        )

        disclosures.forEach { (name, property) ->
            assertTrue(property.get().isNotBlank(), "$name is blank in the device language")
        }
    }

    @Test
    fun theAcknowledgementNamesWhatTheCarrierReleases() {
        // The sentence that matters most: it must say what is shared and with
        // whom, not merely that something is. An earlier version of this app
        // said only "I acknowledge the FCC challenge sharing terms".
        val text = ConsentCopy.FCC_ACKNOWLEDGEMENT
        assertTrue(text.contains("FCC"), "the acknowledgement does not name the FCC")
        assertTrue(
            text.contains("IP") || text.contains("dirección IP"),
            "the acknowledgement does not say what is released",
        )
    }
}
