package edu.gatech.cc.cellwatch.domain.consent

/**
 * What the user is asked to agree to, worded exactly as frozenApp worded it.
 *
 * Copied verbatim from `frozenApp/src/main/res/values/strings.xml` rather
 * than rewritten. This is consent text for a study that publishes location
 * data: the wording presumably went through review, and paraphrasing it would
 * quietly change what people agreed to. The product app previously showed a
 * single invented checkbox - "I acknowledge the FCC challenge sharing terms" -
 * which linked to nothing and disclosed none of this.
 *
 * Shared rather than per-platform so the two apps cannot drift into asking
 * for different consent.
 *
 * Not yet translated. frozenApp ships a Spanish `values-es`; reproducing it
 * needs a localisation mechanism this app does not have, and is recorded as
 * outstanding rather than silently dropped.
 */
object ConsentCopy {

    const val DATA_USE_TITLE = "Data Use"

    const val DATA_USE_SHARED =
        "The data you collect with CellWatch will be shared with the public, including the " +
            "general location and time of your measurements."

    const val DATA_USE_RESEARCH = "Your CellWatch data may also be used for research purposes."

    const val DATA_USE_POLICY = "Please read our privacy policy for details."

    const val PRIVACY_POLICY_LABEL = "Read our Privacy Policy"

    const val PRIVACY_POLICY_URL =
        "https://sites.gatech.edu/cellwatch/android-app/app-privacy-policy/"

    const val COLLECTION_MODE_TITLE = "Collection Mode"

    const val COLLECTION_MODE_DESCRIPTION =
        "Would you like to work towards a formal FCC challenge? You can update this later in " +
            "the app settings.\n\nIn both modes your data will be shared with the public, " +
            "including general locations and timestamps, and may be used by the CellWatch team " +
            "for research purposes."

    const val CHALLENGE_MODE_TITLE = "FCC Challenge Mode"

    const val CHALLENGE_MODE_DESCRIPTION =
        "Measurements will be sent to the FCC as challenge data. The FCC may make public your " +
            "exact GPS location, provider, and other relevant details."

    const val TESTING_MODE_TITLE = "Testing Mode"

    const val TESTING_MODE_DESCRIPTION = "Measurements will not be sent to the FCC."

    const val FCC_INFO_TITLE = "FCC Information"

    const val FCC_INFO_DESCRIPTION =
        "If you would like to work toward an official FCC challenge, this information must be " +
            "submitted to the FCC with your measurements. This information will never be " +
            "shared with the public."

    const val FCC_ACKNOWLEDGEMENT =
        "I acknowledge that my service provider may share necessary customer-specific " +
            "information with the FCC (such as IP address and service plan details)."
}
