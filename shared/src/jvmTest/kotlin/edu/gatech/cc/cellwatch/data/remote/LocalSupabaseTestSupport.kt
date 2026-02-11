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

private fun findCellwatchProperties(startDir: File): File? {
    var current: File? = startDir
    while (current != null) {
        val candidate = File(current, "cellwatch.properties")
        if (candidate.exists()) return candidate
        current = current.parentFile
    }
    return null
}
