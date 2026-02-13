package edu.gatech.cc.cellwatch.domain.runtime

/**
 * Shared runtime-mode UI contract used by platform harness apps.
 * Keeps mode-cycling, labels, and profile resolution behavior consistent.
 */
data class RuntimeModeUiResolution(
    val snapshot: RuntimeSyncMsakProfileSnapshot?,
    val errorMessage: String?,
)

class RuntimeModeUiBridge {
    fun nextMsakMode(current: RuntimeMsakMode): RuntimeMsakMode {
        return when (current) {
            RuntimeMsakMode.PUBLIC -> RuntimeMsakMode.STAGING
            RuntimeMsakMode.STAGING -> RuntimeMsakMode.LOCAL
            RuntimeMsakMode.LOCAL -> RuntimeMsakMode.PUBLIC
        }
    }

    fun nextSupabaseMode(current: RuntimeSupabaseMode): RuntimeSupabaseMode {
        return when (current) {
            RuntimeSupabaseMode.LOCAL -> RuntimeSupabaseMode.TESTING
            RuntimeSupabaseMode.TESTING -> RuntimeSupabaseMode.LIVE
            RuntimeSupabaseMode.LIVE -> RuntimeSupabaseMode.LOCAL
        }
    }

    fun msakModeLabel(mode: RuntimeMsakMode): String = mode.name

    fun supabaseModeLabel(mode: RuntimeSupabaseMode): String = mode.name

    fun resolve(config: RuntimeProfileConfig): RuntimeModeUiResolution {
        return runCatching {
            RuntimeModeUiResolution(
                snapshot = RuntimeProfileResolver.resolveSnapshot(config),
                errorMessage = null,
            )
        }.getOrElse { error ->
            RuntimeModeUiResolution(
                snapshot = null,
                errorMessage = error.message ?: error::class.simpleName ?: "runtime resolution failed",
            )
        }
    }
}
