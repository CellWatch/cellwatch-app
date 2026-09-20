package edu.gatech.cc.cellwatch.domain.maphome

import com.beriukhov.h3.H3
import com.beriukhov.h3.LatLng

actual object H3Grid {
    actual val isSupported: Boolean = true

    actual fun cellAt(latitude: Double, longitude: Double, resolution: Int): String? =
        runCatching { H3.geoToH3(LatLng(latitude, longitude), resolution).toHexString() }
            .getOrNull()

    actual fun boundaryOf(cell: String): List<H3Vertex> =
        runCatching { H3.vertices(cell).map { H3Vertex(it.lat, it.lng) } }
            .getOrDefault(emptyList())
}
