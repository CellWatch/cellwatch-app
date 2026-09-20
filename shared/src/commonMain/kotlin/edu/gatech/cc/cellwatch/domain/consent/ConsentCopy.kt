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
 * English and Spanish, both lifted from frozenApp's `values` and `values-es`.
 * The two are kept adjacent at each string rather than in separate files, so
 * a reviewer sees them together and neither can change without the other
 * being visible. These are computed rather than `const`, which is what makes
 * them locale-aware without any call site changing.
 */
object ConsentCopy {

    val DATA_USE_TITLE: String get() = localized(
        en = "Data Use",
        es = "Uso de datos",
    )

    val DATA_USE_SHARED: String get() = localized(
        en = "The data you collect with CellWatch will be shared with the public, including the " +
            "general location and time of your measurements.",
        es = "Los datos que usted recopila con CellWatch se comparte con el público, incluso " +
            "la ubicación y hora generales de sus mediciones.",
    )

    val DATA_USE_RESEARCH: String get() = localized(
        en = "Your CellWatch data may also be used for research purposes.",
        es = "Sus datos de CellWatch también se pueden utilizar para fines de investigación.",
    )

    val DATA_USE_POLICY: String get() = localized(
        en = "Please read our privacy policy for details.",
        es = "Por favor lea nuestra política de privacidad para los detalles.",
    )

    val PRIVACY_POLICY_LABEL: String get() = localized(
        en = "Read our Privacy Policy",
        es = "Leer nuestra política de privacidad",
    )

    /**
     * "Next", reused from frozenApp rather than translating "Continue"
     * myself: it is the word that flow already used, and it is reviewed.
     */
    val CONTINUE: String get() = localized(en = "Next", es = "Siguiente")

    /**
     * Status lines for the consent step.
     *
     * Unlike everything above, these are mine - frozenApp had no equivalent,
     * because it gated its flow differently. The Spanish is therefore not
     * reviewed translation, which matters less than it would for the
     * disclosures but is worth knowing.
     */
    val ACKNOWLEDGEMENT_REQUIRED: String get() = localized(
        en = "Acknowledge the FCC information to continue in challenge mode.",
        es = "Reconozca la información de la FCC para continuar en modo de impugnación.",
    )

    val CHANGE_LATER: String get() = localized(
        en = "You can change this later in Settings.",
        es = "Puede cambiar esto luego en los ajustes.",
    )

    val TESTING_SUMMARY: String get() = localized(
        en = "Measurements will be saved and shared publicly, but not sent to the FCC.",
        es = "Las mediciones se guardarán y se compartirán con el público, pero no se " +
            "enviarán a la FCC.",
    )

    const val PRIVACY_POLICY_URL =
        "https://sites.gatech.edu/cellwatch/android-app/app-privacy-policy/"

    val COLLECTION_MODE_TITLE: String get() = localized(
        en = "Collection Mode",
        es = "Modo de recopilación",
    )

    val COLLECTION_MODE_DESCRIPTION: String get() = localized(
        en = "Would you like to work towards a formal FCC challenge? You can update this later " +
            "in the app settings.\n\nIn both modes your data will be shared with the public, " +
            "including general locations and timestamps, and may be used by the CellWatch " +
            "team for research purposes.",
        es = "¿Le gustaría trabajar para una impugnación oficial con la FCC? Esto se puede " +
            "cambiar luego en los ajustes de la aplicación.\n\nEn ambos modos sus datos se " +
            "compartirán con el público, incluso las ubicaciones y horas generales, y se " +
            "pueden utilizar para fines de investigación por el equipo CellWatch.",
    )

    val CHALLENGE_MODE_TITLE: String get() = localized(
        en = "FCC Challenge Mode",
        es = "Modo de impugnación FCC",
    )

    val CHALLENGE_MODE_DESCRIPTION: String get() = localized(
        en = "Measurements will be sent to the FCC as challenge data. The FCC may make public " +
            "your exact GPS location, provider, and other relevant details.",
        es = "Las mediciones se enviarán a la FCC como datos de impugnación. Puede que la FCC " +
            "haga público la ubicación exacta del GPS, el proveedor y otros detalles " +
            "pertinentes.",
    )

    val TESTING_MODE_TITLE: String get() = localized(
        en = "Testing Mode",
        es = "Modo de prueba",
    )

    val TESTING_MODE_DESCRIPTION: String get() = localized(
        en = "Measurements will not be sent to the FCC.",
        es = "Las mediciones no se enviarán a la FCC.",
    )

    val FCC_INFO_TITLE: String get() = localized(
        en = "FCC Information",
        es = "Información para la FCC",
    )

    val FCC_INFO_DESCRIPTION: String get() = localized(
        en = "If you would like to work toward an official FCC challenge, this information must " +
            "be submitted to the FCC with your measurements. This information will never be " +
            "shared with the public.",
        es = "Si desea trabajar para una impugnación con la FCC, esta información se debe " +
            "enviar a la FCC con sus mediciones. Esta información no se compartirá con el " +
            "público nunca.",
    )

    val FCC_ACKNOWLEDGEMENT: String get() = localized(
        en = "I acknowledge that my service provider may share necessary customer-specific " +
            "information with the FCC (such as IP address and service plan details).",
        es = "Reconozco que mi proveedor de servicio puede compartir la información necesaria, " +
            "específica al cliente, con la FCC (tal como la dirección IP y los detalles " +
            "del plan de servicio).",
    )
}
