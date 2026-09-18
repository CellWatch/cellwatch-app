package edu.gatech.cc.cellwatch.data.remote

import java.io.File
import java.util.Properties
import java.util.UUID

internal class InMemoryDeviceAuthStore(
    private val deviceId: String = UUID.randomUUID().toString(),
) : DeviceAuthStore {
    var secret: String? = null

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}

internal fun loadLocalSupabaseConfig(): SupabaseConnectionConfig {
    val propsFile = findCellwatchProperties(File(System.getProperty("user.dir")))
    val props = Properties()
    if (propsFile != null && propsFile.exists()) {
        propsFile.inputStream().use(props::load)
    }

    val rawUrl = props.getProperty("SUPABASE_LOCAL_URL")?.trim()?.removeSurrounding("\"")
        ?: "http://127.0.0.1:54321"
    val normalizedUrl = rawUrl.replace("10.0.2.2", "127.0.0.1")
    val apiKey = props.getProperty("SUPABASE_LOCAL_API_KEY")?.trim()?.removeSurrounding("\"")
        ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

    return SupabaseConnectionConfig(
        url = normalizedUrl,
        apiKey = apiKey,
    )
}


/**
 * Config for integration tests that can run against either Supabase.
 *
 * Defaults to local Docker; `-Pcellwatch.integration.supabase=testing` targets
 * the hosted TESTING project instead, which avoids standing Docker up just to
 * exercise a round trip. The testing database is disposable and is never
 * forwarded to the FCC, so the rows these tests write are free.
 */
internal fun loadIntegrationSupabaseConfig(): SupabaseConnectionConfig {
    val target = System.getProperty("cellwatch.integration.supabase", "local").trim().lowercase()
    if (target != "testing") return loadLocalSupabaseConfig()

    val propsFile = findCellwatchProperties(File(System.getProperty("user.dir")))
    val props = Properties()
    checkNotNull(propsFile) { "cellwatch.properties not found; cannot target hosted TESTING" }
    propsFile.inputStream().use(props::load)

    fun required(key: String): String =
        props.getProperty(key)?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
            ?: error("$key missing from ${propsFile.absolutePath}")

    return SupabaseConnectionConfig(
        url = required("SUPABASE_TESTING_URL"),
        apiKey = required("SUPABASE_TESTING_API_KEY"),
    )
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
