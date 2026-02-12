package edu.gatech.cc.cellwatch.data.sync

private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:54321"
private const val DEFAULT_LOCAL_API_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

data class SyncRuntimeConfig(
    val allowRemote: Boolean = false,
    val localUrl: String = DEFAULT_LOCAL_URL,
    val localApiKey: String = DEFAULT_LOCAL_API_KEY,
    val remoteUrl: String? = null,
    val remoteApiKey: String? = null,
) : SyncSupabaseConfigResolver {

    override fun resolve(target: SyncTransportTarget): SyncSupabaseConfig {
        return when (target) {
            SyncTransportTarget.LOCAL -> SyncSupabaseConfig(
                url = localUrl,
                apiKey = localApiKey,
            )
            SyncTransportTarget.REMOTE -> {
                check(allowRemote) {
                    "remote supabase target is blocked; set CELLWATCH_ALLOW_REMOTE_SUPABASE=true to enable"
                }
                val url = remoteUrl?.normalizeValue()
                    ?: error("missing SUPABASE_URL in runtime config")
                val apiKey = remoteApiKey?.normalizeValue()
                    ?: error("missing SUPABASE_API_KEY in runtime config")
                check(!url.contains("127.0.0.1") && !url.contains("localhost")) {
                    "SUPABASE_URL points to local host but REMOTE target was requested"
                }
                SyncSupabaseConfig(
                    url = url,
                    apiKey = apiKey,
                )
            }
        }
    }

    fun toSupabaseProfile(
        target: SyncTransportTarget = SyncTransportTarget.LOCAL,
    ): SyncRemoteProfile.Supabase {
        return SyncRemoteProfile.Supabase(
            configResolver = this,
            target = target,
        )
    }
}

object SyncRuntimeConfigFactory {
    fun fromRaw(
        allowRemote: Boolean = false,
        localUrl: String? = null,
        localApiKey: String? = null,
        remoteUrl: String? = null,
        remoteApiKey: String? = null,
    ): SyncRuntimeConfig {
        return SyncRuntimeConfig(
            allowRemote = allowRemote,
            localUrl = (localUrl ?: DEFAULT_LOCAL_URL).normalizeLocalUrl(),
            localApiKey = (localApiKey ?: DEFAULT_LOCAL_API_KEY).normalizeValue(),
            remoteUrl = remoteUrl?.normalizeValue(),
            remoteApiKey = remoteApiKey?.normalizeValue(),
        )
    }
}

object SyncRuntimeProfileBridge {
    @Throws(IllegalStateException::class)
    fun resolveSupabaseConfig(
        allowRemote: Boolean = false,
        localUrl: String? = null,
        localApiKey: String? = null,
        remoteUrl: String? = null,
        remoteApiKey: String? = null,
        useRemote: Boolean = false,
    ): SyncSupabaseConfig {
        val runtime = SyncRuntimeConfigFactory.fromRaw(
            allowRemote = allowRemote,
            localUrl = localUrl,
            localApiKey = localApiKey,
            remoteUrl = remoteUrl,
            remoteApiKey = remoteApiKey,
        )
        val profile = runtime.toSupabaseProfile(
            target = if (useRemote) SyncTransportTarget.REMOTE else SyncTransportTarget.LOCAL,
        )
        return profile.configResolver.resolve(profile.target)
    }
}

private fun String.normalizeValue(): String = trim().removeSurrounding("\"")

private fun String.normalizeLocalUrl(): String = normalizeValue().replace("10.0.2.2", "127.0.0.1")
