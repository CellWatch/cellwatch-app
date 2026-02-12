package edu.gatech.cc.cellwatch.domain.runtime

import edu.gatech.cc.cellwatch.data.sync.SyncRemoteProfile
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfig
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfigFactory
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfig
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateConfig
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment

enum class RuntimeMsakMode {
    LOCAL,
    STAGING,
    PUBLIC,
}

enum class RuntimeSupabaseMode {
    LOCAL,
    TESTING,
    LIVE,
}

data class RuntimeSyncMsakProfile(
    val msakMode: RuntimeMsakMode,
    val supabaseMode: RuntimeSupabaseMode,
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
    fun fromModes(
        msakMode: RuntimeMsakMode = RuntimeMsakMode.PUBLIC,
        supabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL,
        localSupabaseUrl: String? = null,
        localSupabaseApiKey: String? = null,
        testingSupabaseUrl: String? = null,
        testingSupabaseApiKey: String? = null,
        liveSupabaseUrl: String? = null,
        liveSupabaseApiKey: String? = null,
        allowRemoteSupabase: Boolean = false,
        localMsakHost: String? = null,
        localMsakSecure: Boolean = false,
        userAgent: String = "cellwatch-runtime-profile",
    ): RuntimeSyncMsakProfile {
        val (remoteUrl, remoteApiKey) = when (supabaseMode) {
            RuntimeSupabaseMode.LOCAL -> null to null
            RuntimeSupabaseMode.TESTING -> testingSupabaseUrl to testingSupabaseApiKey
            RuntimeSupabaseMode.LIVE -> liveSupabaseUrl to liveSupabaseApiKey
        }
        val syncTarget = when (supabaseMode) {
            RuntimeSupabaseMode.LOCAL -> SyncTransportTarget.LOCAL
            RuntimeSupabaseMode.TESTING, RuntimeSupabaseMode.LIVE -> SyncTransportTarget.REMOTE
        }
        val msakConfig = when (msakMode) {
            RuntimeMsakMode.LOCAL -> MsakLocateConfig(
                environment = MsakLocateEnvironment.LOCAL,
                userAgent = userAgent,
                localServerHost = localMsakHost,
                localServerSecure = localMsakSecure,
            )
            RuntimeMsakMode.STAGING -> MsakLocateConfig(
                environment = MsakLocateEnvironment.STAGING,
                userAgent = userAgent,
            )
            RuntimeMsakMode.PUBLIC -> MsakLocateConfig(
                environment = MsakLocateEnvironment.PROD,
                userAgent = userAgent,
            )
        }
        return RuntimeSyncMsakProfile(
            msakMode = msakMode,
            supabaseMode = supabaseMode,
            msakConfig = msakConfig,
            syncConfig = SyncRuntimeConfigFactory.fromRaw(
                allowRemote = allowRemoteSupabase,
                localUrl = localSupabaseUrl,
                localApiKey = localSupabaseApiKey,
                remoteUrl = remoteUrl,
                remoteApiKey = remoteApiKey,
            ),
            syncTarget = syncTarget,
        )
    }

    fun publicMsakLocalSupabase(
        localSupabaseUrl: String? = null,
        localSupabaseApiKey: String? = null,
        userAgent: String = "cellwatch-phase3-public-msak-local-supabase",
    ): RuntimeSyncMsakProfile {
        return fromModes(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            localSupabaseUrl = localSupabaseUrl,
            localSupabaseApiKey = localSupabaseApiKey,
            allowRemoteSupabase = false,
            userAgent = userAgent,
        )
    }
}

data class RuntimeSyncMsakProfileSnapshot(
    val msakMode: RuntimeMsakMode,
    val supabaseMode: RuntimeSupabaseMode,
    val msakEnvironment: MsakLocateEnvironment,
    val msakUserAgent: String?,
    val msakLocalServerHost: String?,
    val msakLocalServerSecure: Boolean,
    val syncTarget: SyncTransportTarget,
    val supabaseUrl: String,
    val supabaseApiKey: String,
)

class RuntimeSyncMsakProfileBridge {
    @Throws(IllegalStateException::class)
    fun resolveFromModes(
        msakMode: RuntimeMsakMode = RuntimeMsakMode.PUBLIC,
        supabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL,
        localSupabaseUrl: String? = null,
        localSupabaseApiKey: String? = null,
        testingSupabaseUrl: String? = null,
        testingSupabaseApiKey: String? = null,
        liveSupabaseUrl: String? = null,
        liveSupabaseApiKey: String? = null,
        allowRemoteSupabase: Boolean = false,
        localMsakHost: String? = null,
        localMsakSecure: Boolean = false,
        userAgent: String = "cellwatch-runtime-profile",
    ): RuntimeSyncMsakProfileSnapshot {
        val profile = RuntimeSyncMsakProfiles.fromModes(
            msakMode = msakMode,
            supabaseMode = supabaseMode,
            localSupabaseUrl = localSupabaseUrl,
            localSupabaseApiKey = localSupabaseApiKey,
            testingSupabaseUrl = testingSupabaseUrl,
            testingSupabaseApiKey = testingSupabaseApiKey,
            liveSupabaseUrl = liveSupabaseUrl,
            liveSupabaseApiKey = liveSupabaseApiKey,
            allowRemoteSupabase = allowRemoteSupabase,
            localMsakHost = localMsakHost,
            localMsakSecure = localMsakSecure,
            userAgent = userAgent,
        )
        val resolved = profile.resolveSyncSupabaseConfig()
        return RuntimeSyncMsakProfileSnapshot(
            msakMode = profile.msakMode,
            supabaseMode = profile.supabaseMode,
            msakEnvironment = profile.msakConfig.environment,
            msakUserAgent = profile.msakConfig.userAgent,
            msakLocalServerHost = profile.msakConfig.localServerHost,
            msakLocalServerSecure = profile.msakConfig.localServerSecure,
            syncTarget = profile.syncTarget,
            supabaseUrl = resolved.url,
            supabaseApiKey = resolved.apiKey,
        )
    }

    @Throws(IllegalStateException::class)
    fun resolvePublicMsakLocalSupabase(
        localSupabaseUrl: String?,
        localSupabaseApiKey: String?,
        userAgent: String = "cellwatch-phase3-public-msak-local-supabase",
    ): RuntimeSyncMsakProfileSnapshot {
        return resolveFromModes(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            localSupabaseUrl = localSupabaseUrl,
            localSupabaseApiKey = localSupabaseApiKey,
            allowRemoteSupabase = false,
            userAgent = userAgent,
        )
    }
}
