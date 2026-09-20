package edu.gatech.cc.cellwatch.domain.consent

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Checks the Spanish column is really Spanish.
 *
 * On the JVM the device language is `Locale.getDefault()`, which a test can
 * swap - so this is the one source set that can read the same property twice
 * and compare. The common test can only see whatever language the host
 * happens to report.
 *
 * What it guards is the mistake `ConsentCopy` invites: pasting the English
 * string into the `es` slot, which no compiler and no screenshot of an
 * English device would catch.
 */
class ConsentCopyLocaleTest {

    private val original: Locale = Locale.getDefault()

    @AfterTest
    fun restore() {
        Locale.setDefault(original)
    }

    private val disclosures = listOf(
        "DATA_USE_TITLE" to ConsentCopy::DATA_USE_TITLE,
        "DATA_USE_SHARED" to ConsentCopy::DATA_USE_SHARED,
        "DATA_USE_RESEARCH" to ConsentCopy::DATA_USE_RESEARCH,
        "DATA_USE_POLICY" to ConsentCopy::DATA_USE_POLICY,
        "PRIVACY_POLICY_LABEL" to ConsentCopy::PRIVACY_POLICY_LABEL,
        "CONTINUE" to ConsentCopy::CONTINUE,
        "ACKNOWLEDGEMENT_REQUIRED" to ConsentCopy::ACKNOWLEDGEMENT_REQUIRED,
        "CHANGE_LATER" to ConsentCopy::CHANGE_LATER,
        "TESTING_SUMMARY" to ConsentCopy::TESTING_SUMMARY,
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

    @Test
    fun everyStringDiffersBetweenEnglishAndSpanish() {
        disclosures.forEach { (name, property) ->
            Locale.setDefault(Locale.ENGLISH)
            val english = property.get()
            Locale.setDefault(Locale.forLanguageTag("es-US"))
            val spanish = property.get()

            assertTrue(english.isNotBlank(), "$name has no English copy")
            assertTrue(spanish.isNotBlank(), "$name has no Spanish copy")
            assertNotEquals(english, spanish, "$name is the same in both languages")
        }
    }

    @Test
    fun theUrlIsNotTranslated() {
        // Deliberately `const`: one published policy, one address, whatever
        // language the app is speaking.
        Locale.setDefault(Locale.forLanguageTag("es-US"))
        assertTrue(ConsentCopy.PRIVACY_POLICY_URL.startsWith("https://sites.gatech.edu/cellwatch/"))
    }
}
