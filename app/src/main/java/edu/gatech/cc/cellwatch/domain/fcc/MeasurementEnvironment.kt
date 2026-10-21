package edu.gatech.cc.cellwatch.domain.fcc

import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.core.util.PermissionManager
import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import github.nisrulz.easydeviceinfo.base.EasyAppMod
import github.nisrulz.easydeviceinfo.base.EasyDeviceMod
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Everything [MeasurementTest.run] reads from the phone around a measurement: location, cells,
 * network state and device details. The app always uses [AndroidMeasurementEnvironment]; unit tests
 * pass a fake so that run() (and the success flag it computes) can be exercised on the JVM.
 */
interface MeasurementEnvironment {
    suspend fun getLocation(): Location?

    /**
     * Neither the start nor the end of the test produced a location, and the BDC rejects a test
     * without one: ask once more for a fresh fix. Returns null rather than throwing so the
     * measurement is still saved.
     */
    suspend fun retryLocation(): Location?

    fun getConnectionType(): NetworkConnectionType
    fun isCellularDataEnabled(): Boolean?
    fun getCells(): List<Cell>?
    fun getActiveNetworkGeneration(): String?

    /** Calls [onChange] whenever the cells change, until the returned function is called. */
    fun watchCells(onChange: (List<Cell>) -> Unit): () -> Unit

    suspend fun getDeviceId(): String?
    fun getDeviceManufacturer(): String?
    fun getDeviceModel(): String?
    fun getDeviceOsVersion(): String?
    fun getAppName(): String?
    fun getProviderName(): String?
    fun getSimMobileCountryCode(): String?
    fun getSimMobileNetworkCode(): String?
    fun getNetworkMobileCountryCode(): String?
    fun getNetworkMobileNetworkCode(): String?
    fun isNetworkAvailable(): Boolean
    fun isNetworkConnected(): Boolean
    fun isNetworkRoaming(): Boolean
}

object AndroidMeasurementEnvironment : MeasurementEnvironment {
    private val TAG = this::class.simpleName
    private const val LOCATION_RETRY_MILLIS = 10_000L

    override suspend fun getLocation(): Location? = getLocation(
        CurrentLocationRequest.Builder()
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setMaxUpdateAgeMillis(3000)
            .build()
    )

    /** Uses GPS this time, for at most [LOCATION_RETRY_MILLIS]. */
    override suspend fun retryLocation(): Location? {
        Log.i(TAG, "no location for this test; retrying")

        return try {
            getLocation(
                CurrentLocationRequest.Builder()
                    .setGranularity(Granularity.GRANULARITY_FINE)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(3000)
                    .setDurationMillis(LOCATION_RETRY_MILLIS)
                    .build()
            ).also { if (it == null) Log.i(TAG, "location retry found no location") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "location retry failed", e)
            null
        }
    }

    private suspend fun getLocation(request: CurrentLocationRequest): Location? = suspendCoroutine { continuation ->
        val context = CellWatchApp.applicationContext()

        if (PermissionManager.checkLocationPermission()) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.getCurrentLocation(request, null).addOnSuccessListener {
                continuation.resume(Location.fromAndroidLocation(it))
            }.addOnFailureListener {
                continuation.resumeWithException(it)
            }
        } else {
            continuation.resumeWithException(Exception("no location permission"))
        }
    }

    override fun getConnectionType() = TelephonyInfoManager.getConnectionType()
    override fun isCellularDataEnabled() = TelephonyInfoManager.isCellularDataEnabled()
    override fun getCells() = TelephonyInfoManager.getCells()
    override fun getActiveNetworkGeneration() = TelephonyInfoManager.getActiveNetworkGeneration()
    override fun watchCells(onChange: (List<Cell>) -> Unit) = TelephonyInfoManager.watchCells(onChange)

    override suspend fun getDeviceId(): String? = CellWatchApp.settingsRepository.getDeviceId()
    override fun getDeviceManufacturer(): String? = EasyDeviceMod(CellWatchApp.applicationContext()).manufacturer
    override fun getDeviceModel(): String? = Build.MODEL
    override fun getDeviceOsVersion(): String? = EasyDeviceMod(CellWatchApp.applicationContext()).osVersion
    override fun getAppName(): String? = EasyAppMod(CellWatchApp.applicationContext()).appName
    override fun getProviderName(): String? = TelephonyInfoManager.getProviderName()
    override fun getSimMobileCountryCode() = TelephonyInfoManager.getSimMobileCountryCode()
    override fun getSimMobileNetworkCode() = TelephonyInfoManager.getSimMobileNetworkCode()
    override fun getNetworkMobileCountryCode() = TelephonyInfoManager.getNetworkMobileCountryCode()
    override fun getNetworkMobileNetworkCode() = TelephonyInfoManager.getNetworkMobileNetworkCode()
    override fun isNetworkAvailable() = TelephonyInfoManager.isNetworkAvailable()
    override fun isNetworkConnected() = TelephonyInfoManager.isNetworkConnected()
    override fun isNetworkRoaming() = TelephonyInfoManager.isNetworkRoaming()
}
