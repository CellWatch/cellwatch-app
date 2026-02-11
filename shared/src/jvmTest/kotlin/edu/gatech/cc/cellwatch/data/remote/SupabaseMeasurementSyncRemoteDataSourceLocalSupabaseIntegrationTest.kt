package edu.gatech.cc.cellwatch.data.remote

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.sync.DuplicateKeyError
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.io.File
import java.util.Properties
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SupabaseMeasurementSyncRemoteDataSourceLocalSupabaseIntegrationTest {

    @Test
    fun insertAndGetMeasurement_roundTripsAgainstLocalSupabase() = runBlocking {
        val config = loadLocalSupabaseConfig()
        val deviceId = UUID.randomUUID().toString()
        val authStore = InMemoryDeviceAuthStore(deviceId)
        val remote = SupabaseMeasurementSyncRemoteDataSource(config, authStore)

        val now = Clock.System.now()
        val measurementId = UUID.randomUUID().toString()
        val measurement = Measurement(
            id = measurementId,
            groupId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            type = "latency",
            timestamp = now,
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

        val inserted = remote.insertMeasurement(measurement)
        assertEquals(measurementId, inserted.id)

        val fetched = remote.getMeasurementById(measurementId)
        assertEquals(measurementId, fetched.id)
        assertEquals(deviceId, fetched.deviceId)
        assertEquals("latency", fetched.type)
        assertNotNull(authStore.secret)
        Unit
    }

    @Test
    fun insertMeasurement_duplicateIdThrowsDuplicateKey() = runBlocking {
        val config = loadLocalSupabaseConfig()
        val deviceId = UUID.randomUUID().toString()
        val remote = SupabaseMeasurementSyncRemoteDataSource(config, InMemoryDeviceAuthStore(deviceId))

        val measurementId = UUID.randomUUID().toString()
        val measurement = Measurement(
            id = measurementId,
            groupId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            type = "latency",
            timestamp = Clock.System.now(),
        )

        remote.insertMeasurement(measurement)
        assertFailsWith<DuplicateKeyError> {
            remote.insertMeasurement(measurement)
        }
        Unit
    }

    @Test
    fun insertFccSubmission_succeedsAgainstLocalSupabase() = runBlocking {
        val config = loadLocalSupabaseConfig()
        val deviceId = UUID.randomUUID().toString()
        val remote = SupabaseMeasurementSyncRemoteDataSource(config, InMemoryDeviceAuthStore(deviceId))

        val submissionId = UUID.randomUUID().toString()
        val submission = FccSubmission(
            id = submissionId,
            deviceId = deviceId,
            provider = "test-provider",
            submitted = false,
        )

        val inserted = remote.insertFccSubmission(submission)
        assertEquals(submissionId, inserted.id)
        assertEquals(deviceId, inserted.deviceId)
    }
}

private class InMemoryDeviceAuthStore(
    private val deviceId: String,
) : DeviceAuthStore {
    var secret: String? = null

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}

private fun loadLocalSupabaseConfig(): SupabaseConnectionConfig {
    val propsFile = findCellwatchProperties(File(System.getProperty("user.dir")))
    val props = Properties()
    if (propsFile != null && propsFile.exists()) {
        propsFile.inputStream().use(props::load)
    }

    val rawUrl = props.getProperty("SUPABASE_LOCAL_URL")?.trim()?.removeSurrounding("\"")
        ?: "http://127.0.0.1:54321"
    val normalizedUrl = rawUrl.replace("10.0.2.2", "127.0.0.1")
    val apiKey = props.getProperty("SUPABASE_LOCAL_API_KEY")?.trim()?.removeSurrounding("\"")
        ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

    return SupabaseConnectionConfig(
        url = normalizedUrl,
        apiKey = apiKey,
    )
}

private fun findCellwatchProperties(startDir: File): File? {
    var current: File? = startDir
    while (current != null) {
        val candidate = File(current, "cellwatch.properties")
        if (candidate.exists()) return candidate
        current = current.parentFile
    }
    return null
}
