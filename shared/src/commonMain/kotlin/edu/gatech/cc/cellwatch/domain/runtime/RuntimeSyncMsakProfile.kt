package edu.gatech.cc.cellwatch.domain.runtime

import edu.gatech.cc.cellwatch.data.sync.SyncRemoteProfile
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfig
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfigFactory
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfig
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateConfig
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment

data class RuntimeSyncMsakProfile(
    val msakConfig: MsakLocateConfig,
    val syncConfig: SyncRuntimeConfig,
    val syncTarget: SyncTransportTarget = SyncTransportTarget.LOCAL,
) {
    fun toSyncRemoteProfile(): SyncRemoteProfile.Supabase {
        return syncConfig.toSupabaseProfile(target = syncTarget)
    }

    fun resolveSyncSupabaseConfig(): SyncSupabaseConfig = syncConfig.resolve(syncTarget)
}

object RuntimeSyncMsakProfiles {
    fun publicMsakLocalSupabase(
        localSupabaseUrl: String? = null,
        localSupabaseApiKey: String? = null,
        userAgent: String = "cellwatch-phase3-public-msak-local-supabase",
    ): RuntimeSyncMsakProfile {
        return RuntimeSyncMsakProfile(
            msakConfig = MsakLocateConfig(
                environment = MsakLocateEnvironment.PROD,
                userAgent = userAgent,
            ),
            syncConfig = SyncRuntimeConfigFactory.fromRaw(
                allowRemote = false,
                localUrl = localSupabaseUrl,
                localApiKey = localSupabaseApiKey,
            ),
            syncTarget = SyncTransportTarget.LOCAL,
        )
    }
}

data class RuntimeSyncMsakProfileSnapshot(
    val msakEnvironment: MsakLocateEnvironment,
    val msakUserAgent: String?,
    val msakLocalServerHost: String?,
    val msakLocalServerSecure: Boolean,
    val supabaseUrl: String,
    val supabaseApiKey: String,
)

class RuntimeSyncMsakProfileBridge {
    fun resolvePublicMsakLocalSupabase(
        localSupabaseUrl: String?,
        localSupabaseApiKey: String?,
        userAgent: String = "cellwatch-phase3-public-msak-local-supabase",
    ): RuntimeSyncMsakProfileSnapshot {
        val profile = RuntimeSyncMsakProfiles.publicMsakLocalSupabase(
            localSupabaseUrl = localSupabaseUrl,
            localSupabaseApiKey = localSupabaseApiKey,
            userAgent = userAgent,
        )
        val resolved = profile.resolveSyncSupabaseConfig()
        return RuntimeSyncMsakProfileSnapshot(
            msakEnvironment = profile.msakConfig.environment,
            msakUserAgent = profile.msakConfig.userAgent,
            msakLocalServerHost = profile.msakConfig.localServerHost,
            msakLocalServerSecure = profile.msakConfig.localServerSecure,
            supabaseUrl = resolved.url,
            supabaseApiKey = resolved.apiKey,
        )
    }
}
