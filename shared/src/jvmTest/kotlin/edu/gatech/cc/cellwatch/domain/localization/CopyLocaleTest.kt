package edu.gatech.cc.cellwatch.domain.localization

import edu.gatech.cc.cellwatch.domain.app.ShellCopy
import edu.gatech.cc.cellwatch.domain.consent.ConsentCopy
import edu.gatech.cc.cellwatch.domain.export.ExportCopy
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionOutcomeMessage
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementFailureMessage
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeCopy
import edu.gatech.cc.cellwatch.domain.measurementhistory.HistoryCopy
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunCopy
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartCopy
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingCopy
import edu.gatech.cc.cellwatch.domain.profile.ProfileCopy
import edu.gatech.cc.cellwatch.domain.settings.SettingsCopy
import edu.gatech.cc.cellwatch.domain.sync.SyncCopy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Checks the Spanish column is really Spanish, for every copy object.
 *
 * On the JVM the device language is `Locale.getDefault()`, which a test can
 * swap - so this is the one source set that can read the same property twice
 * and compare. The common test can only see whatever language the host
 * happens to report.
 *
 * Reflection rather than a hand-written list of names. A list is a second
 * place to remember to edit, and the failure mode is silent: a string added
 * to a copy object and left in English would simply never be checked. Every
 * `String` property with a getter is swept, so a new one is covered the
 * moment it is written.
 *
 * `const val`s are excluded automatically - they compile to static fields
 * with no getter - which is the right boundary. The handful that exist are
 * deliberately untranslated: the product name, the `--` placeholder, the
 * privacy policy URL, and the developer-only "not built yet" stub.
 */
class CopyLocaleTest {

    private val original: Locale = Locale.getDefault()

    @AfterTest
    fun restore() {
        Locale.setDefault(original)
    }

    private val copyObjects: List<Any> = listOf(
        ConsentCopy,
        ExportCopy,
        FccSubmissionOutcomeMessage,
        HistoryCopy,
        MeasurementFailureMessage,
        MapHomeCopy,
        MeasurementRunCopy,
        MeasurementStartCopy,
        OnboardingCopy,
        ProfileCopy,
        SettingsCopy,
        ShellCopy,
        SyncCopy,
    )

    private fun stringProperties(target: Any): List<Pair<String, () -> String>> =
        target.javaClass.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == String::class.java }
            .sortedBy { it.name }
            .map { method ->
                "${target.javaClass.simpleName}.${method.name.removePrefix("get")}" to
                    { method.invoke(target) as String }
            }

    @Test
    fun everyStringDiffersBetweenEnglishAndSpanish() {
        val checked = mutableListOf<String>()
        copyObjects.forEach { target ->
            stringProperties(target).forEach { (name, read) ->
                Locale.setDefault(Locale.ENGLISH)
                val english = read()
                Locale.setDefault(Locale.forLanguageTag("es-US"))
                val spanish = read()

                assertTrue(english.isNotBlank(), "$name has no English copy")
                assertTrue(spanish.isNotBlank(), "$name has no Spanish copy")
                assertNotEquals(english, spanish, "$name is the same in both languages")
                checked += name
            }
        }
        // A reflection sweep that silently matched nothing would pass every
        // assertion above and prove nothing.
        assertTrue(checked.size >= 140, "only ${checked.size} strings were swept: $checked")
    }

    @Test
    fun theUrlIsNotTranslated() {
        // Deliberately `const`: one published policy, one address, whatever
        // language the app is speaking.
        Locale.setDefault(Locale.forLanguageTag("es-US"))
        assertTrue(ConsentCopy.PRIVACY_POLICY_URL.startsWith("https://sites.gatech.edu/cellwatch/"))
    }

    @Test
    fun theExportedOutcomeIsAlwaysEnglish() {
        // The extended export lands in a file that may be read by the FCC or
        // the research team. It must not change language with the phone.
        Locale.setDefault(Locale.forLanguageTag("es-US"))
        val outcome = FccSubmissionOutcomeMessage.submitted(ENGLISH)
        assertTrue(
            outcome == "This measurement will be submitted to the FCC.",
            "export outcome followed the device language: $outcome",
        )
    }
}
