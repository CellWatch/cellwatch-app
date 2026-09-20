package edu.gatech.cc.cellwatch.domain.maphome

/**
 * Unsupported on the JVM.
 *
 * h3-kmp publishes android and ios variants only. The jvm target exists for
 * harness and unit tests, neither of which draws a map, so this reports
 * unavailability rather than pulling in a second, differently-versioned H3
 * whose cells might not match the devices'.
 */
actual object H3Grid {
    actual val isSupported: Boolean = false

    actual fun cellAt(latitude: Double, longitude: Double, resolution: Int): String? = null

    actual fun boundaryOf(cell: String): List<H3Vertex> = emptyList()
}
