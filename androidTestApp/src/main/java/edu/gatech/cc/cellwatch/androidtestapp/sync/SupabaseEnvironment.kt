package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfig
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfigResolver
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
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
        val props = loadProperties()
        return when (target) {
            SupabaseTarget.LOCAL -> {
                val localUrl = props.getProperty("SUPABASE_LOCAL_URL")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.replace("10.0.2.2", "127.0.0.1")
                    ?: DEFAULT_LOCAL_URL
                val localKey = props.getProperty("SUPABASE_LOCAL_API_KEY")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?: DEFAULT_LOCAL_API_KEY
                SupabaseEnvironment(
                    target = SupabaseTarget.LOCAL,
                    url = localUrl,
                    apiKey = localKey,
                )
            }

            SupabaseTarget.REMOTE -> {
                if (!allowRemote) {
                    throw IllegalStateException(
                        "remote supabase target is blocked; set CELLWATCH_ALLOW_REMOTE_SUPABASE=true to enable",
                    )
                }
                val remoteUrl = props.getProperty("SUPABASE_URL")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?: throw IllegalStateException("missing SUPABASE_URL in cellwatch.properties")
                val remoteKey = props.getProperty("SUPABASE_API_KEY")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?: throw IllegalStateException("missing SUPABASE_API_KEY in cellwatch.properties")
                if (remoteUrl.contains("127.0.0.1") || remoteUrl.contains("localhost")) {
                    throw IllegalStateException("SUPABASE_URL points to local host but REMOTE target was requested")
                }
                SupabaseEnvironment(
                    target = SupabaseTarget.REMOTE,
                    url = remoteUrl,
                    apiKey = remoteKey,
                )
            }
        }
    }

    override fun resolve(target: SyncTransportTarget): SyncSupabaseConfig {
        val env = resolve(
            when (target) {
                SyncTransportTarget.LOCAL -> SupabaseTarget.LOCAL
                SyncTransportTarget.REMOTE -> SupabaseTarget.REMOTE
            }
        )
        return SyncSupabaseConfig(
            url = env.url,
            apiKey = env.apiKey,
        )
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

    companion object {
        const val DEFAULT_LOCAL_URL: String = "http://127.0.0.1:54321"
        const val DEFAULT_LOCAL_API_KEY: String =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
    }
}
