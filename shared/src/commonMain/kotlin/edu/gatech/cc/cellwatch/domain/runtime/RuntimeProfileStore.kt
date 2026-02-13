package edu.gatech.cc.cellwatch.domain.runtime

import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateConfig

/**
 * Canonical runtime profile payload intended for app-owned persistence and resolution.
 * This keeps mode + endpoint/key wiring in one shared contract across Android/iOS.
 */
data class RuntimeProfileConfig(
    val msakMode: RuntimeMsakMode = RuntimeMsakMode.PUBLIC,
    val supabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL,
    val allowRemoteSupabase: Boolean = false,
    val strictSupabaseConfig: Boolean = true,
    val localSupabaseUrl: String? = null,
    val localSupabaseApiKey: String? = null,
    val testingSupabaseUrl: String? = null,
    val testingSupabaseApiKey: String? = null,
    val liveSupabaseUrl: String? = null,
    val liveSupabaseApiKey: String? = null,
    val localMsakHost: String? = null,
    val localMsakSecure: Boolean = false,
    val userAgent: String = "cellwatch-runtime-profile",
)

interface RuntimeProfileStore {
    fun load(): RuntimeProfileConfig?
    fun save(config: RuntimeProfileConfig)
    fun clear()
}

class InMemoryRuntimeProfileStore(
    private var config: RuntimeProfileConfig? = null,
) : RuntimeProfileStore {
    override fun load(): RuntimeProfileConfig? = config

    override fun save(config: RuntimeProfileConfig) {
        this.config = config
    }

    override fun clear() {
        config = null
    }
}

object RuntimeProfileResolver {
    fun resolveProfile(config: RuntimeProfileConfig): RuntimeSyncMsakProfile {
        return RuntimeSyncMsakProfiles.fromModes(
            msakMode = config.msakMode,
            supabaseMode = config.supabaseMode,
            localSupabaseUrl = config.localSupabaseUrl,
            localSupabaseApiKey = config.localSupabaseApiKey,
            testingSupabaseUrl = config.testingSupabaseUrl,
            testingSupabaseApiKey = config.testingSupabaseApiKey,
            liveSupabaseUrl = config.liveSupabaseUrl,
            liveSupabaseApiKey = config.liveSupabaseApiKey,
            allowRemoteSupabase = config.allowRemoteSupabase,
            strictSupabaseConfig = config.strictSupabaseConfig,
            localMsakHost = config.localMsakHost,
            localMsakSecure = config.localMsakSecure,
            userAgent = config.userAgent,
        )
    }

    fun resolveSnapshot(config: RuntimeProfileConfig): RuntimeSyncMsakProfileSnapshot {
        val profile = resolveProfile(config)
        val resolved = profile.resolveSyncSupabaseConfig()
        val msakConfig: MsakLocateConfig = profile.msakConfig
        return RuntimeSyncMsakProfileSnapshot(
            msakMode = profile.msakMode,
            supabaseMode = profile.supabaseMode,
            msakEnvironment = msakConfig.environment,
            msakUserAgent = msakConfig.userAgent,
            msakLocalServerHost = msakConfig.localServerHost,
            msakLocalServerSecure = msakConfig.localServerSecure,
            syncTarget = profile.syncTarget,
            supabaseUrl = resolved.url,
            supabaseApiKey = resolved.apiKey,
        )
    }

    fun resolveSnapshot(
        store: RuntimeProfileStore,
        fallback: RuntimeProfileConfig,
    ): RuntimeSyncMsakProfileSnapshot {
        val config = store.load() ?: fallback
        return resolveSnapshot(config)
    }
}

class RuntimeProfileResolverBridge {
    fun resolveProfile(config: RuntimeProfileConfig): RuntimeSyncMsakProfile =
        RuntimeProfileResolver.resolveProfile(config)

    fun resolveSnapshot(config: RuntimeProfileConfig): RuntimeSyncMsakProfileSnapshot =
        RuntimeProfileResolver.resolveSnapshot(config)

    fun resolveSnapshot(
        store: RuntimeProfileStore,
        fallback: RuntimeProfileConfig,
    ): RuntimeSyncMsakProfileSnapshot = RuntimeProfileResolver.resolveSnapshot(store, fallback)
}
