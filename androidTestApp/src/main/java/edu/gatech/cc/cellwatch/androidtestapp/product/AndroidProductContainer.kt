package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.core.content.pm.PackageInfoCompat
import edu.gatech.cc.cellwatch.androidtestapp.BuildConfig
import edu.gatech.cc.cellwatch.androidtestapp.onboarding.AndroidOnboardingProfileStore
import edu.gatech.cc.cellwatch.androidtestapp.sync.resolveRuntimeProfileConfigFromProperties
import edu.gatech.cc.cellwatch.data.remote.AndroidDeviceCredentialStorage
import edu.gatech.cc.cellwatch.data.remote.DeviceCredentialStorage
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.app.ProductContainer
import edu.gatech.cc.cellwatch.domain.app.ProductPlatformServices
import edu.gatech.cc.cellwatch.domain.app.ProductSubmissionIdentity
import edu.gatech.cc.cellwatch.domain.capability.AndroidPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeMsakMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeProfileResolver
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSupabaseMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfile

/**
 * Android half of the product composition root.
 *
 * Supplies only what the platform can build; everything downstream is assembled
 * by the shared [ProductContainer].
 */
class AndroidProductServices(
    context: Context,
    override val runtimeProfile: RuntimeSyncMsakProfile,
) : ProductPlatformServices {

    private val appContext = context.applicationContext

    override val database: CellwatchDatabase = CellwatchDatabase(
        AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = appContext,
            // Persistent and product-owned. The harnesses open a fresh
            // uuid-named database per run for isolation; doing that here would
            // empty History after every measurement.
            name = PRODUCT_DATABASE_NAME,
        ),
    )

    override val capabilityProvider: PlatformCapabilityProvider =
        AndroidPlatformCapabilityProvider(appContext)

    override val credentialStorage: DeviceCredentialStorage =
        AndroidDeviceCredentialStorage(appContext)

    override val tcpTupleUrl: String? = BuildConfig.CELLWATCH_TCP_TUPLE_URL.takeIf { it.isNotBlank() }

    override val appSource: String = APP_SOURCE

    override fun submissionIdentity(): ProductSubmissionIdentity {
        val profile = AndroidOnboardingProfileStore(appContext).loadProfile()
        return ProductSubmissionIdentity(
            appName = resolveAppName(),
            appVersion = resolveAppVersion(),
            contactName = profile?.name,
            contactEmail = profile?.email,
            contactPhone = profile?.phone,
        )
    }

    private fun resolveAppName(): String = runCatching {
        appContext.packageManager.getApplicationLabel(appContext.applicationInfo)?.toString()?.trim().orEmpty()
    }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "CellWatch"

    private fun resolveAppVersion(): String = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val versionName = info.versionName ?: BuildConfig.VERSION_NAME
        "$versionName (${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrElse { BuildConfig.VERSION_NAME }

    private companion object {
        const val PRODUCT_DATABASE_NAME = "cellwatch.db"
        const val APP_SOURCE = "cellwatch-android"
    }
}

/**
 * Process-wide holder. One container, so one database connection and one
 * device credential.
 *
 * Resolution can fail - `RuntimeProfileContract.requireValid` rejects an
 * incomplete configuration - and that is reported rather than swallowed, so the
 * shell can route to `Destination.BlockingError` instead of presenting a
 * Measure button that cannot work.
 */
object AndroidProductContainer {
    private var cached: ProductContainer? = null
    private var failure: Throwable? = null

    fun resolve(context: Context): Result<ProductContainer> {
        cached?.let { return Result.success(it) }
        failure?.let { return Result.failure(it) }
        return runCatching {
            val config = resolveRuntimeProfileConfigFromProperties(
                msakMode = runCatching { RuntimeMsakMode.valueOf(BuildConfig.CELLWATCH_DEFAULT_MSAK_MODE) }
                    .getOrDefault(RuntimeMsakMode.PUBLIC),
                supabaseMode = runCatching { RuntimeSupabaseMode.valueOf(BuildConfig.CELLWATCH_DEFAULT_SUPABASE_MODE) }
                    .getOrDefault(RuntimeSupabaseMode.LOCAL),
                allowRemoteSupabase = BuildConfig.CELLWATCH_ALLOW_REMOTE_SUPABASE,
                allowLiveSupabase = BuildConfig.CELLWATCH_ALLOW_LIVE_SUPABASE,
            )
            ProductContainer(
                AndroidProductServices(context, RuntimeProfileResolver.resolveProfile(config)),
            )
        }.onSuccess { cached = it }.onFailure { failure = it }
    }
}
