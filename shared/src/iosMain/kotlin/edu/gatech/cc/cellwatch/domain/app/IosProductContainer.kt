package edu.gatech.cc.cellwatch.domain.app

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import edu.gatech.cc.cellwatch.data.remote.DeviceCredentialStorage
import edu.gatech.cc.cellwatch.data.remote.IosDeviceCredentialStorage
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.capability.IosPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeMsakMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeProfileConfig
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeProfileResolver
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSupabaseMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfile
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusStore
import edu.gatech.cc.cellwatch.domain.sync.readPackagedRuntimeProperty
import platform.Foundation.NSBundle

/**
 * iOS half of the product composition root. Mirror of `AndroidProductServices`.
 *
 * Reads the packaged runtime resource rather than taking values from Swift: it
 * is the same resource on simulator, device and a store build, so the app
 * cannot resolve its configuration one way in one place and differently in
 * another.
 */
class IosProductServices(
    override val runtimeProfile: RuntimeSyncMsakProfile,
    private val contact: () -> ProductSubmissionIdentity,
) : ProductPlatformServices {

    override val database: CellwatchDatabase = CellwatchDatabase(
        NativeSqliteDriver(
            schema = CellwatchDatabase.Schema,
            // Persistent, unlike the harnesses' per-run uuid databases. See
            // ProductPlatformServices.
            name = PRODUCT_DATABASE_NAME,
        ),
    )

    override val capabilityProvider: PlatformCapabilityProvider = IosPlatformCapabilityProvider()

    override val credentialStorage: DeviceCredentialStorage = IosDeviceCredentialStorage()

    override val tcpTupleUrl: String? = readPackagedRuntimeProperty("TCP_TUPLE_URL")

    override val appSource: String = APP_SOURCE

    override val syncStatusStore: SyncStatusStore = IosSyncStatusStore()

    override fun submissionIdentity(): ProductSubmissionIdentity = contact()

    companion object {
        const val PRODUCT_DATABASE_NAME = "cellwatch.db"
        const val APP_SOURCE = "cellwatch-ios"

        fun appName(): String =
            bundleString("CFBundleDisplayName") ?: bundleString("CFBundleName") ?: "CellWatch"

        fun appVersion(): String {
            val short = bundleString("CFBundleShortVersionString") ?: "0"
            val build = bundleString("CFBundleVersion") ?: "0"
            return "$short ($build)"
        }

        private fun bundleString(key: String): String? =
            (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() }
    }
}

/**
 * Builds the product container from the packaged runtime resource.
 *
 * Resolution can fail - `RuntimeProfileContract.requireValid` rejects an
 * incomplete configuration - and Swift is expected to route that to
 * `Destination.BlockingError` rather than present a Measure button that cannot
 * work. Exposed as a throwing factory because Kotlin's `Result` does not cross
 * into Swift usefully.
 */
object IosProductContainerFactory {

    private var cached: ProductContainer? = null

    @Throws(IllegalStateException::class, Throwable::class)
    fun create(contact: () -> ProductSubmissionIdentity): ProductContainer {
        cached?.let { return it }
        val profile = RuntimeProfileResolver.resolveProfile(packagedConfig())
        return ProductContainer(IosProductServices(profile, contact)).also { cached = it }
    }

    private fun packagedConfig(): RuntimeProfileConfig = RuntimeProfileConfig(
        msakMode = enumOrDefault(readPackagedRuntimeProperty("CELLWATCH_DEFAULT_MSAK_MODE"), RuntimeMsakMode.PUBLIC),
        supabaseMode = enumOrDefault(
            readPackagedRuntimeProperty("CELLWATCH_DEFAULT_SUPABASE_MODE"),
            RuntimeSupabaseMode.LOCAL,
        ),
        allowRemoteSupabase = truthy(readPackagedRuntimeProperty("CELLWATCH_ALLOW_REMOTE_SUPABASE")),
        allowLiveSupabase = truthy(readPackagedRuntimeProperty("CELLWATCH_ALLOW_LIVE_SUPABASE")),
        localSupabaseUrl = readPackagedRuntimeProperty("SUPABASE_LOCAL_URL"),
        localSupabaseApiKey = readPackagedRuntimeProperty("SUPABASE_LOCAL_API_KEY"),
        testingSupabaseUrl = readPackagedRuntimeProperty("SUPABASE_TESTING_URL"),
        testingSupabaseApiKey = readPackagedRuntimeProperty("SUPABASE_TESTING_API_KEY"),
        liveSupabaseUrl = readPackagedRuntimeProperty("SUPABASE_URL"),
        liveSupabaseApiKey = readPackagedRuntimeProperty("SUPABASE_API_KEY"),
        localMsakHost = readPackagedRuntimeProperty("MSAK_LOCAL_SERVER_HOST"),
        localMsakSecure = truthy(readPackagedRuntimeProperty("MSAK_LOCAL_SERVER_SECURE")),
        userAgent = IosProductServices.APP_SOURCE,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } } ?: fallback

    private fun truthy(raw: String?): Boolean =
        raw?.lowercase() in setOf("true", "yes", "1")
}
