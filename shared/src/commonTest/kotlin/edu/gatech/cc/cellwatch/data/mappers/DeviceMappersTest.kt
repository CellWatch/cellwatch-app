package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.domain.model.Device
import edu.gatech.cc.cellwatch.db.DeviceEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceMappersTest {

    @Test
    fun toDomain_maps_all_fields_correctly() {
        val entity = DeviceEntity(
            deviceId = "dev-1",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 9",
            deviceOsName = "Android",
            deviceOsVersion = "14",
            appName = "CellWatch"
        )

        val domain = entity.toDomain()

        assertEquals("dev-1", domain.deviceId)
        assertEquals("Google", domain.deviceManufacturer)
        assertEquals("Pixel 9", domain.deviceModel)
        assertEquals("Android", domain.deviceOsName)
        assertEquals("14", domain.deviceOsVersion)
        assertEquals("CellWatch", domain.appName)
    }

    @Test
    fun toDomain_handles_null_fields_with_defaults() {
        val entity = DeviceEntity(
            deviceId = "dev-2",
            deviceManufacturer = null,
            deviceModel = null,
            deviceOsName = null,
            deviceOsVersion = null,
            appName = null
        )

        val domain = entity.toDomain()

        assertEquals("dev-2", domain.deviceId)
        assertEquals("", domain.deviceManufacturer)
        assertEquals("", domain.deviceModel)
        assertEquals("", domain.deviceOsName)
        assertEquals("", domain.deviceOsVersion)
        assertEquals("", domain.appName)
    }

    @Test
    fun round_trip_domain_to_row_and_back_preserves_values() {
        val original = Device(
            deviceId = "dev-3",
            deviceManufacturer = "Apple",
            deviceModel = "iPhone 16",
            deviceOsName = "iOS",
            deviceOsVersion = "18",
            appName = "CellWatch"
        )

        val row = original.toRow()
        val back = row.toDomain()

        assertEquals(original.deviceId, back.deviceId)
        assertEquals(original.deviceManufacturer, back.deviceManufacturer)
        assertEquals(original.deviceModel, back.deviceModel)
        assertEquals(original.deviceOsName, back.deviceOsName)
        assertEquals(original.deviceOsVersion, back.deviceOsVersion)
        assertEquals(original.appName, back.appName)
    }
}