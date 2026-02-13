package edu.gatech.cc.cellwatch.domain.runtime

/**
 * Shared onboarding/runtime draft contract.
 * Platform UIs should collect/edit this shape, then resolve through RuntimeProfileResolver.
 */
data class RuntimeOnboardingDraft(
    val msakMode: RuntimeMsakMode = RuntimeMsakMode.LOCAL,
    val supabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL,
    val localSupabaseUrl: String? = null,
    val localSupabaseApiKey: String? = null,
    val testingSupabaseUrl: String? = null,
    val testingSupabaseApiKey: String? = null,
    val liveSupabaseUrl: String? = null,
    val liveSupabaseApiKey: String? = null,
    val localMsakHost: String? = null,
    val localMsakSecure: Boolean = false,
) {
    fun toRuntimeProfileConfig(
        allowRemoteSupabase: Boolean,
        strictSupabaseConfig: Boolean = true,
        userAgent: String = "cellwatch-runtime-profile",
    ): RuntimeProfileConfig {
        return RuntimeProfileConfig(
            msakMode = msakMode,
            supabaseMode = supabaseMode,
            allowRemoteSupabase = allowRemoteSupabase,
            strictSupabaseConfig = strictSupabaseConfig,
            localSupabaseUrl = localSupabaseUrl,
            localSupabaseApiKey = localSupabaseApiKey,
            testingSupabaseUrl = testingSupabaseUrl,
            testingSupabaseApiKey = testingSupabaseApiKey,
            liveSupabaseUrl = liveSupabaseUrl,
            liveSupabaseApiKey = liveSupabaseApiKey,
            localMsakHost = localMsakHost,
            localMsakSecure = localMsakSecure,
            userAgent = userAgent,
        )
    }
}

object RuntimeOnboardingContract {
    fun fromConfig(config: RuntimeProfileConfig): RuntimeOnboardingDraft {
        return RuntimeOnboardingDraft(
            msakMode = config.msakMode,
            supabaseMode = config.supabaseMode,
            localSupabaseUrl = config.localSupabaseUrl,
            localSupabaseApiKey = config.localSupabaseApiKey,
            testingSupabaseUrl = config.testingSupabaseUrl,
            testingSupabaseApiKey = config.testingSupabaseApiKey,
            liveSupabaseUrl = config.liveSupabaseUrl,
            liveSupabaseApiKey = config.liveSupabaseApiKey,
            localMsakHost = config.localMsakHost,
            localMsakSecure = config.localMsakSecure,
        )
    }
}
