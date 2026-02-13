package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.androidtestapp.BuildConfig
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfig
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfigResolver
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfig
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeMsakMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeProfileConfig
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeProfileResolver
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSupabaseMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfile
import java.io.File
import java.net.URI
import java.util.Properties

enum class SupabaseTarget {
    LOCAL,
    REMOTE,
}

data class SupabaseEnvironment(
    val target: SupabaseTarget,
    val url: String,
    val apiKey: String,
)

interface SupabaseEnvironmentProvider : SyncSupabaseConfigResolver {
    fun resolve(target: SupabaseTarget = SupabaseTarget.LOCAL): SupabaseEnvironment
}

class CellwatchPropertiesSupabaseEnvironmentProvider(
    private val workingDir: File = File(System.getProperty("user.dir") ?: "."),
    private val allowRemote: Boolean = System.getenv("CELLWATCH_ALLOW_REMOTE_SUPABASE") == "true",
) : SupabaseEnvironmentProvider {

    override fun resolve(target: SupabaseTarget): SupabaseEnvironment {
        val config = runtimeConfig()
        val mappedTarget = when (target) {
            SupabaseTarget.LOCAL -> SyncTransportTarget.LOCAL
            SupabaseTarget.REMOTE -> SyncTransportTarget.REMOTE
        }
        val resolved = config.resolve(mappedTarget)
        return SupabaseEnvironment(
            target = target,
            url = resolved.url,
            apiKey = resolved.apiKey,
        )
    }

    override fun resolve(target: SyncTransportTarget): SyncSupabaseConfig {
        return runtimeConfig().resolve(target)
    }

    private fun loadProperties(): Properties {
        val props = Properties()
        val file = findCellwatchProperties(workingDir)
        if (file != null && file.exists()) {
            file.inputStream().use(props::load)
        }
        return props
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

    private fun runtimeConfig() = run {
        val props = loadProperties()
        if (allowRemote) {
            resolveRuntimeProfileFromProperties(
                workingDir = workingDir,
                preloadedProperties = props,
                msakMode = RuntimeMsakMode.PUBLIC,
                supabaseMode = RuntimeSupabaseMode.LIVE,
                allowRemoteSupabase = true,
            ).syncConfig
        } else {
            resolveRuntimeProfileFromProperties(
                workingDir = workingDir,
                preloadedProperties = props,
                msakMode = RuntimeMsakMode.PUBLIC,
                supabaseMode = RuntimeSupabaseMode.LOCAL,
                allowRemoteSupabase = false,
            ).syncConfig
        }
    }

    companion object {
        const val DEFAULT_LOCAL_URL: String = "http://10.0.2.2:54321"
    }
}

class FixedSupabaseEnvironmentProvider(
    private val runtimeConfig: SyncRuntimeConfig,
) : SupabaseEnvironmentProvider {
    override fun resolve(target: SupabaseTarget): SupabaseEnvironment {
        val mappedTarget = when (target) {
            SupabaseTarget.LOCAL -> SyncTransportTarget.LOCAL
            SupabaseTarget.REMOTE -> SyncTransportTarget.REMOTE
        }
        val resolved = runtimeConfig.resolve(mappedTarget)
        return SupabaseEnvironment(target = target, url = resolved.url, apiKey = resolved.apiKey)
    }

    override fun resolve(target: SyncTransportTarget): SyncSupabaseConfig {
        return runtimeConfig.resolve(target)
    }
}

fun resolveRuntimeProfileFromProperties(
    workingDir: File = File(System.getProperty("user.dir") ?: "."),
    preloadedProperties: Properties? = null,
    msakMode: RuntimeMsakMode = RuntimeMsakMode.PUBLIC,
    supabaseMode: RuntimeSupabaseMode = RuntimeSupabaseMode.LOCAL,
    allowRemoteSupabase: Boolean = false,
): RuntimeSyncMsakProfile {
    val props = preloadedProperties ?: loadCellwatchProperties(workingDir)
    val configuredLocalMsakHost = props.getProperty("MSAK_LOCAL_SERVER_HOST")
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotEmpty() }
    val resolvedLocalMsakHost = when (msakMode) {
        RuntimeMsakMode.LOCAL -> configuredLocalMsakHost ?: DEFAULT_ANDROID_LOCAL_MSAK_HOST
        else -> configuredLocalMsakHost
    }
    val strictRuntimeConfig = props.getProperty("CELLWATCH_STRICT_RUNTIME_CONFIG")
        ?.trim()
        ?.trim('"')
        ?.toBooleanStrictOrNull()
        ?: DEFAULT_STRICT_RUNTIME_CONFIG
    val config = RuntimeProfileConfig(
        msakMode = msakMode,
        supabaseMode = supabaseMode,
        localSupabaseUrl = normalizeAndroidLocalSupabaseUrl(props.getProperty("SUPABASE_LOCAL_URL"))
            ?: BuildConfig.CELLWATCH_LOCAL_SUPABASE_URL,
        localSupabaseApiKey = props.getProperty("SUPABASE_LOCAL_SERVICE_KEY")
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
            ?: props.getProperty("SUPABASE_LOCAL_API_KEY")
                ?.trim()
                ?.trim('"')
                ?.takeIf { it.isNotEmpty() }
            ?: BuildConfig.CELLWATCH_LOCAL_SUPABASE_API_KEY
                .takeIf { it.isNotBlank() },
        testingSupabaseUrl = props.getProperty("SUPABASE_TESTING_URL"),
        testingSupabaseApiKey = props.getProperty("SUPABASE_TESTING_API_KEY"),
        liveSupabaseUrl = props.getProperty("SUPABASE_URL"),
        liveSupabaseApiKey = props.getProperty("SUPABASE_API_KEY"),
        allowRemoteSupabase = allowRemoteSupabase,
        strictSupabaseConfig = strictRuntimeConfig,
        localMsakHost = resolvedLocalMsakHost,
        localMsakSecure = props.getProperty("MSAK_LOCAL_SERVER_SECURE")
            ?.trim()
            ?.trim('"')
            ?.toBooleanStrictOrNull()
            ?: false,
    )
    return RuntimeProfileResolver.resolveProfile(config)
}

private fun loadCellwatchProperties(workingDir: File): Properties {
    val props = Properties()
    val file = findCellwatchProperties(workingDir)
    if (file != null && file.exists()) {
        file.inputStream().use(props::load)
    }
    return props
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

private fun normalizeAndroidLocalSupabaseUrl(raw: String?): String? {
    val value = raw?.trim()?.trim('"')?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return value
    val host = uri.host ?: return value
    val normalizedHost = when (host) {
        "127.0.0.1", "localhost", "10.0.3.2" -> "10.0.2.2"
        else -> host
    }
    if (normalizedHost == host) return value
    return runCatching {
        URI(
            uri.scheme,
            uri.userInfo,
            normalizedHost,
            uri.port,
            uri.path,
            uri.query,
            uri.fragment,
        ).toString()
    }.getOrElse { value }
}

private const val DEFAULT_ANDROID_LOCAL_MSAK_HOST = "10.0.2.2:8080"
private const val DEFAULT_ANDROID_LOCAL_SUPABASE_URL = "http://10.0.2.2:54321"
private const val DEFAULT_STRICT_RUNTIME_CONFIG = true
