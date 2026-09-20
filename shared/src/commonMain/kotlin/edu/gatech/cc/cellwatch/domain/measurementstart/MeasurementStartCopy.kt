package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.localization.localized

/**
 * The pre-flight screen: what is checked before a run, and what blocks one.
 *
 * "Medir de todas maneras", "En un vehículo en movimiento", "Cancelar" and
 * "Permiso de ubicación requerido" are frozenApp's, which ran the same
 * pre-flight. The rest is mine and unreviewed.
 */
object MeasurementStartCopy {

    val TITLE: String get() = localized(en = "Start measurement", es = "Comenzar la medición")

    val CONDITIONS_CHECKED: String get() = localized(
        en = "Conditions are checked when you start.",
        es = "Las condiciones se revisan cuando usted comienza.",
    )

    val PREFLIGHT_PASSED: String get() = localized(
        en = "Preflight passed. You can start measuring.",
        es = "La revisión previa pasó. Puede comenzar a medir.",
    )

    /** frozenApp `location_permission_required`, expanded into a sentence. */
    val LOCATION_REQUIRED: String get() = localized(
        en = "Location permission is required before starting measurement.",
        es = "Se requiere el permiso de ubicación antes de comenzar la medición.",
    )

    val COMPLETE_PROFILE: String get() = localized(
        en = "Complete profile setup before starting measurement.",
        es = "Complete su perfil antes de comenzar la medición.",
    )

    val WIFI_DETECTED: String get() = localized(
        en = "Wi-Fi detected. Choose Measure anyway or Cancel.",
        es = "Se detectó WiFi. Elija Medir de todas maneras o Cancelar.",
    )

    val PREFLIGHT_BLOCKED: String get() = localized(
        en = "Preflight blocked. Review requirements and try again.",
        es = "La revisión previa bloqueó la medición. Revise los requisitos e inténtelo de nuevo.",
    )

    val WIFI_WARNING: String get() = localized(
        en = "It looks like you are connected to Wi-Fi or network path is unknown. " +
            "If you proceed, your measurement may not be submitted to the FCC.",
        es = "Parece que está conectado al WiFi, o no se conoce la ruta de la red. Si continúa, " +
            "puede que su medición no se envíe a la FCC.",
    )

    /** frozenApp `in_moving_vehicle`. */
    val IN_MOVING_VEHICLE: String get() = localized(
        en = "I am in a moving vehicle",
        es = "Estoy en un vehículo en movimiento",
    )

    /** frozenApp `measure_anyway` and `cancel_button`. */
    val MEASURE_ANYWAY: String get() = localized(
        en = "Measure anyway",
        es = "Medir de todas maneras",
    )

    val CANCEL: String get() = localized(en = "Cancel", es = "Cancelar")

    val STOP_MEASUREMENT: String get() = localized(
        en = "Stop measurement",
        es = "Detener la medición",
    )

    val BEFORE_YOU_START: String get() = localized(
        en = "Before you start",
        es = "Antes de comenzar",
    )

    val WHAT_HAPPENS: String get() = localized(
        en = "A measurement runs three tests and takes about half a minute. Keep the app open " +
            "until it finishes.",
        es = "Una medición hace tres pruebas y toma como medio minuto. Mantenga la aplicación " +
            "abierta hasta que termine.",
    )
}
