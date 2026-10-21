package edu.gatech.cc.cellwatch.domain.fcc

import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.PermissionManager
import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import github.nisrulz.easydeviceinfo.base.EasyAppMod
import github.nisrulz.easydeviceinfo.base.EasyDeviceMod
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
    fun isUsingCarrierAggregation(cells: List<Cell>): Boolean
    fun isNetworkAvailable(): Boolean
    fun isNetworkConnected(): Boolean
    fun isNetworkRoaming(): Boolean
}

object AndroidMeasurementEnvironment : MeasurementEnvironment {
    override suspend fun getLocation(): Location? = suspendCoroutine { continuation ->
        val context = CellWatchApp.applicationContext()

        if (PermissionManager.checkLocationPermission()) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setGranularity(Granularity.GRANULARITY_FINE)
                    .setMaxUpdateAgeMillis(3000)
                    .build(),
                null
            ).addOnSuccessListener {
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
    override fun isUsingCarrierAggregation(cells: List<Cell>) = TelephonyInfoManager.isUsingCarrierAggregation(cells)
    override fun isNetworkAvailable() = TelephonyInfoManager.isNetworkAvailable()
    override fun isNetworkConnected() = TelephonyInfoManager.isNetworkConnected()
    override fun isNetworkRoaming() = TelephonyInfoManager.isNetworkRoaming()
}
