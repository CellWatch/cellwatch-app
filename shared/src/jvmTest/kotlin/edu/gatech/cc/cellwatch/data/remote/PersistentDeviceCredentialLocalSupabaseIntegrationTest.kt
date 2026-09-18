package edu.gatech.cc.cellwatch.data.remote

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the two faults that let a device silently stop syncing, neither of
 * which the existing integration tests could catch: they mint a fresh random id
 * per run (so registration always succeeded) and set Measurement.deviceId on
 * the fixture by hand (so RLS was always satisfied). Production did neither.
 */
private class MemoryCredentialStorage(var value: String? = null) : DeviceCredentialStorage {
    override fun read(): String? = value
    override fun write(value: String) { this.value = value }
    override fun clear() { value = null }
}

class PersistentDeviceCredentialLocalSupabaseIntegrationTest {

    @Test
    fun insertStampsOwnershipSoRlsAccepts_andTheCredentialSurvivesRestart() = runBlocking {
        val config = loadIntegrationSupabaseConfig()
        val storage = MemoryCredentialStorage()

        // --- first launch: registers, uploads, persists the credential ---
        val firstRun = SupabaseMeasurementSyncRemoteDataSource(
            config,
            PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it }),
        )

        // deviceId deliberately NOT set: production never set it, and RLS
        // rejects a null device_id because verified_device_id() = NULL is NULL.
        val firstId = UUID.randomUUID().toString()
        val inserted = firstRun.insertMeasurement(measurement(firstId))

        assertNotNull(inserted.deviceId, "insert must stamp the authenticated device id")
        assertEquals(inserted.deviceId, firstRun.getMeasurementById(firstId).deviceId)
        assertNotNull(storage.value, "the credential must be persisted after registering")

        // --- second launch: same storage, new objects ---
        // Re-registering the stored id would fail ("already registered"), which
        // is precisely how sync died on a real device.
        val secondRun = SupabaseMeasurementSyncRemoteDataSource(
            config,
            PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it }),
        )
        val secondId = UUID.randomUUID().toString()
        val second = secondRun.insertMeasurement(measurement(secondId))

        assertEquals(inserted.deviceId, second.deviceId, "the same device identity must be reused")
    }

    @Test
    fun aCredentialMissingItsSecretIsReplacedRatherThanReRegistered() = runBlocking {
        val config = loadIntegrationSupabaseConfig()

        // Register once so the id genuinely exists server-side.
        val storage = MemoryCredentialStorage()
        val first = SupabaseMeasurementSyncRemoteDataSource(config, PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it }))
        val strandedId = first.insertMeasurement(measurement(UUID.randomUUID().toString())).deviceId
        assertNotNull(strandedId)

        // Now simulate the secret being lost while the id survives.
        storage.value = """{"deviceId":"$strandedId","deviceSecret":""}"""

        val recovered = SupabaseMeasurementSyncRemoteDataSource(config, PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it }))
        val inserted = recovered.insertMeasurement(measurement(UUID.randomUUID().toString()))

        assertTrue(
            inserted.deviceId != strandedId,
            "a stranded id must be abandoned; re-registering it fails permanently",
        )
        Unit
    }

    private fun measurement(id: String) = Measurement(
        id = id,
        groupId = UUID.randomUUID().toString(),
        type = "latency",
        timestamp = Clock.System.now(),
        connectionType = NetworkConnectionType.CELLULAR,
        cellularDataEnabled = true,
        latencyData = LatencyData(
            id = UUID.randomUUID().toString(),
            rtt = 22,
            jitter = 5,
            sent = 10,
            received = 10,
            servers = listOf("example.org"),
        ),
    )
}
