package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfig
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfigResolver
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfigFactory
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfig
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfile
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfiles
import java.io.File
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
            SyncRuntimeConfigFactory.fromRaw(
                allowRemote = true,
                localUrl = props.getProperty("SUPABASE_LOCAL_URL"),
                localApiKey = props.getProperty("SUPABASE_LOCAL_API_KEY"),
                remoteUrl = props.getProperty("SUPABASE_URL"),
                remoteApiKey = props.getProperty("SUPABASE_API_KEY"),
            )
        } else {
            resolvePublicMsakLocalSupabaseRuntimeProfile(workingDir, props).syncConfig
        }
    }

    companion object {
        const val DEFAULT_LOCAL_URL: String = "http://127.0.0.1:54321"
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

fun resolvePublicMsakLocalSupabaseRuntimeProfile(
    workingDir: File = File(System.getProperty("user.dir") ?: "."),
    preloadedProperties: Properties? = null,
): RuntimeSyncMsakProfile {
    val props = preloadedProperties ?: loadCellwatchProperties(workingDir)
    return RuntimeSyncMsakProfiles.publicMsakLocalSupabase(
        localSupabaseUrl = props.getProperty("SUPABASE_LOCAL_URL"),
        localSupabaseApiKey = props.getProperty("SUPABASE_LOCAL_API_KEY"),
    )
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
