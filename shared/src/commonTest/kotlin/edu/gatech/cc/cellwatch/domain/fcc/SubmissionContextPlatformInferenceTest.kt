package edu.gatech.cc.cellwatch.domain.fcc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubmissionContextPlatformInferenceTest {

    @Test
    fun inferDeviceType_mapsKnownOsNames() {
        assertEquals("iOS", inferDeviceType("iOS"))
        assertEquals("Android", inferDeviceType("Android"))
        assertEquals("iOS", inferDeviceType("iPhone OS"))
        assertEquals("Unknown", inferDeviceType(null))
    }

    @Test
    fun formatDeviceOsName_combinesNameAndVersion() {
        assertEquals("iOS 18.1", formatDeviceOsName("iOS", "18.1"))
        assertEquals("Android", formatDeviceOsName("Android", null))
        assertEquals("17", formatDeviceOsName(null, "17"))
        assertNull(formatDeviceOsName(null, null))
    }
}
