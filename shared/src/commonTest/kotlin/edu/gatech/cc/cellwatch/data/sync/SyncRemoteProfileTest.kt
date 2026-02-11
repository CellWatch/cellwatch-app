package edu.gatech.cc.cellwatch.data.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRemoteProfileTest {

    @Test
    fun defaultFactory_resolvesSupabaseConfigFromProfile() {
        val provider = RecordingSupabaseProvider()
        val factory = DefaultSyncRemoteDataSourceFactory(provider)
        val resolver = object : SyncSupabaseConfigResolver {
            override fun resolve(target: SyncTransportTarget): SyncSupabaseConfig {
                return when (target) {
                    SyncTransportTarget.LOCAL -> SyncSupabaseConfig("http://127.0.0.1:54321", "local-key")
                    SyncTransportTarget.REMOTE -> SyncSupabaseConfig("https://example.supabase.co", "remote-key")
                }
            }
        }

        factory.create(
            SyncRemoteProfile.Supabase(
                configResolver = resolver,
                target = SyncTransportTarget.LOCAL,
            ),
        )
        factory.create(
            SyncRemoteProfile.Supabase(
                configResolver = resolver,
                target = SyncTransportTarget.REMOTE,
            ),
        )

        assertEquals("http://127.0.0.1:54321", provider.configs[0].url)
        assertEquals("local-key", provider.configs[0].apiKey)
        assertEquals("https://example.supabase.co", provider.configs[1].url)
        assertEquals("remote-key", provider.configs[1].apiKey)
    }
}

private class RecordingSupabaseProvider : SyncRemoteDataSourceProvider {
    val configs = mutableListOf<SyncSupabaseConfig>()

    override fun create(config: SyncSupabaseConfig): MeasurementSyncRemoteDataSource {
        configs += config
        return object : MeasurementSyncRemoteDataSource {
            override suspend fun insertMeasurement(measurement: Measurement): Measurement = measurement
            override suspend fun getMeasurementById(id: String): Measurement {
                error("not used in this test")
            }
            override suspend fun insertFccSubmission(submission: FccSubmission): FccSubmission = submission
        }
    }
}
