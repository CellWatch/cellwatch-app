package edu.gatech.cc.cellwatch.domain.runtime

/**
 * Shared runtime config contract used by all app/test entrypoints.
 * Validation is strict and deterministic so misconfiguration fails early.
 */
object RuntimeProfileContract {
    const val KEY_ALLOW_REMOTE_SUPABASE = "CELLWATCH_ALLOW_REMOTE_SUPABASE"
    const val KEY_STRICT_RUNTIME_CONFIG = "CELLWATCH_STRICT_RUNTIME_CONFIG"
    const val KEY_LOCAL_SUPABASE_URL = "SUPABASE_LOCAL_URL"
    const val KEY_LOCAL_SUPABASE_API_KEY = "SUPABASE_LOCAL_API_KEY"
    const val KEY_LOCAL_SUPABASE_SERVICE_KEY = "SUPABASE_LOCAL_SERVICE_KEY"
    const val KEY_LOCAL_SUPABASE_SERVICE_ROLE_KEY = "SUPABASE_LOCAL_SERVICE_ROLE_KEY"
    const val KEY_REMOTE_SUPABASE_URL = "SUPABASE_URL"
    const val KEY_REMOTE_SUPABASE_API_KEY = "SUPABASE_API_KEY"
    const val KEY_TESTING_SUPABASE_URL = "SUPABASE_TESTING_URL"
    const val KEY_TESTING_SUPABASE_API_KEY = "SUPABASE_TESTING_API_KEY"
    const val KEY_LOCAL_MSAK_HOST = "MSAK_LOCAL_SERVER_HOST"
    const val KEY_LOCAL_MSAK_SECURE = "MSAK_LOCAL_SERVER_SECURE"

    fun requireValid(config: RuntimeProfileConfig) {
        if (config.msakMode == RuntimeMsakMode.LOCAL) {
            check(!config.localMsakHost.isNullOrBlank()) {
                "missing $KEY_LOCAL_MSAK_HOST for local MSAK runtime mode"
            }
        }
        when (config.supabaseMode) {
            RuntimeSupabaseMode.LOCAL -> {
                if (config.strictSupabaseConfig) {
                    check(!config.localSupabaseUrl.isNullOrBlank()) {
                        "missing $KEY_LOCAL_SUPABASE_URL in strict runtime config"
                    }
                    check(!config.localSupabaseApiKey.isNullOrBlank()) {
                        "missing $KEY_LOCAL_SUPABASE_API_KEY in strict runtime config"
                    }
                }
            }
            RuntimeSupabaseMode.TESTING -> {
                check(config.allowRemoteSupabase) {
                    "testing supabase target requires $KEY_ALLOW_REMOTE_SUPABASE=true"
                }
                check(!config.testingSupabaseUrl.isNullOrBlank()) {
                    "missing $KEY_TESTING_SUPABASE_URL for testing supabase runtime mode"
                }
                check(!config.testingSupabaseApiKey.isNullOrBlank()) {
                    "missing $KEY_TESTING_SUPABASE_API_KEY for testing supabase runtime mode"
                }
            }
            RuntimeSupabaseMode.LIVE -> {
                check(config.allowRemoteSupabase) {
                    "live supabase target requires $KEY_ALLOW_REMOTE_SUPABASE=true"
                }
                check(!config.liveSupabaseUrl.isNullOrBlank()) {
                    "missing $KEY_REMOTE_SUPABASE_URL for live supabase runtime mode"
                }
                check(!config.liveSupabaseApiKey.isNullOrBlank()) {
                    "missing $KEY_REMOTE_SUPABASE_API_KEY for live supabase runtime mode"
                }
            }
        }
    }
}
