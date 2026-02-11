package edu.gatech.cc.cellwatch.data.sync

import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncRemoteDataSource

sealed interface SyncRemoteProfile {
    data class Supabase(
        val configResolver: SyncSupabaseConfigResolver,
        val target: SyncTransportTarget = SyncTransportTarget.LOCAL,
    ) : SyncRemoteProfile
}

interface SyncRemoteDataSourceFactory {
    fun create(profile: SyncRemoteProfile): MeasurementSyncRemoteDataSource
}

class DefaultSyncRemoteDataSourceFactory(
    private val supabaseProvider: SyncRemoteDataSourceProvider,
) : SyncRemoteDataSourceFactory {
    override fun create(profile: SyncRemoteProfile): MeasurementSyncRemoteDataSource {
        return when (profile) {
            is SyncRemoteProfile.Supabase -> supabaseProvider.create(
                profile.configResolver.resolve(profile.target),
            )
        }
    }
}
