package edu.gatech.cc.cellwatch.domain.telephony.managers

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfo.CONNECTION_PRIMARY_SERVING
import android.telephony.CellInfo.CONNECTION_SECONDARY_SERVING
import android.telephony.CellInfo.CONNECTION_UNKNOWN
import android.telephony.CellInfo.UNAVAILABLE
import android.telephony.CellInfo.UNAVAILABLE_LONG
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.TextUtils
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.PermissionManager
import edu.gatech.cc.cellwatch.data.model.Cell
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import java.util.Objects

enum class NetworkConnectionType {
    NONE, WIFI, CELLULAR
}

object TelephonyInfoManager {
    private val TAG = this::class.simpleName
    private val appContext = CellWatchApp.applicationContext()
    private var telephonyManager: TelephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as
            TelephonyManager
    private var availableNetworks = HashSet<Network>()
    val connectivityManager = appContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build(),

            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    availableNetworks.add(network)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    availableNetworks.remove(network)
                }
            }
        )
    }

    fun getNetworkMobileCountryCode(): String? {
        val networkOperator: String = Objects.requireNonNull(telephonyManager).networkOperator
        var mcc: String? = null;

        if (!TextUtils.isEmpty(networkOperator)) {
            mcc = networkOperator.substring(0, 3)
        }

        Log.d(TAG, "net_mcc = $mcc")
        return mcc;
    }

    fun getNetworkMobileNetworkCode(): String? {
        val networkOperator: String = Objects.requireNonNull(telephonyManager).networkOperator
        var mnc: String? = null;

        if (!TextUtils.isEmpty(networkOperator)) {
            mnc = networkOperator.substring(3)
        }

        Log.d(TAG, "net_mnc = $mnc")
        return mnc;
    }

    fun getSimMobileCountryCode(): String? {
        val simOperator: String = Objects.requireNonNull(telephonyManager).simOperator
        var mcc: String? = null;

        if (!TextUtils.isEmpty(simOperator)) {
            mcc = simOperator.substring(0, 3)
        }

        Log.d(TAG, "sim_mcc = $mcc")
        return mcc;
    }

    fun getSimMobileNetworkCode(): String? {
        val simOperator: String = Objects.requireNonNull(telephonyManager).simOperator
        var mnc: String? = null;

        if (!TextUtils.isEmpty(simOperator)) {
            mnc = simOperator.substring(3)
        }

        Log.d(TAG, "net_mcc = $mnc")
        return mnc;
    }

    fun getProviderName(): String {
        return telephonyManager?.networkOperatorName?.lowercase()?.trim() ?: "unknown"
    }

    fun getConnectionType(): NetworkConnectionType {
        var result = NetworkConnectionType.NONE // Returns connection type. 0: none; 1: mobile data; 2: wift
        val cm = connectivityManager
        cm.run {
            cm.activeNetworkInfo?.run {
                if (type == ConnectivityManager.TYPE_WIFI) {
                    result = NetworkConnectionType.WIFI
                } else if (type == ConnectivityManager.TYPE_MOBILE) {
                    result = NetworkConnectionType.CELLULAR
                }
            }
        }
        return result
    }

    fun getCellularDataNetworkType(): Int? {
        if (!PermissionManager.checkPermission()) return null

        val connectivityManager = appContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork)

        if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true) {
            return null
        }

        return telephonyManager.dataNetworkType
    }

    fun getActiveNetworkSubType(cells: List<CellInfo>): String? {
        // must be one of 1X, EVDO, WCDMA, GSM, HSPA, HSPA+, LTE, NRSA, NRNSA
        return when (getCellularDataNetworkType()) {
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1X"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "EVDO"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO"
            TelephonyManager.NETWORK_TYPE_CDMA -> "EVDO"
            TelephonyManager.NETWORK_TYPE_UMTS -> "WCDMA"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GSM"
            TelephonyManager.NETWORK_TYPE_EDGE -> "GSM"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "GSM"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> if (isNRNonStandAlone(cells)) "NRNSA" else "NRSA"
            TelephonyManager.NETWORK_TYPE_IWLAN -> null
            TelephonyManager.NETWORK_TYPE_IDEN -> null
            else -> null
        }
    }

    fun getActiveNetworkGeneration(): String? {
        // based on https://stackoverflow.com/questions/9283765/how-to-determine-if-network-type-is-2g-3g-or-4g
        return when (getCellularDataNetworkType()) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            null -> null
            else -> return "Unknown"
        }
    }

    fun isNRNonStandAlone(cells: List<CellInfo>): Boolean {
        val primary = cells.filter { it.cellConnectionStatus == CONNECTION_PRIMARY_SERVING }
        val secondary = cells.filter { it.cellConnectionStatus == CONNECTION_SECONDARY_SERVING }

        return primary.filterIsInstance<CellInfoLte>().isNotEmpty()
            && secondary.filterIsInstance<CellInfoNr>().isNotEmpty()
    }

    fun getCells(): List<Cell>? {
        if (!PermissionManager.checkPermission()) return null

        val cellInfoList: List<CellInfo> = Objects.requireNonNull(telephonyManager).allCellInfo
        Log.d(TAG, "cellInfoList length = ${cellInfoList.size}")

        return getCells(cellInfoList)
    }

    fun getCells(cellInfos: List<CellInfo>): List<Cell> {
        val activeNetworkSubtype = getActiveNetworkSubType(cellInfos)
        val cells = mutableListOf<Cell>()

        for (cellInfo in cellInfos) {
            val cellSignalStrength = when (cellInfo) {
                is CellInfoCdma -> cellInfo.cellSignalStrength
                is CellInfoGsm -> cellInfo.cellSignalStrength
                is CellInfoLte -> cellInfo.cellSignalStrength
                is CellInfoNr -> cellInfo.cellSignalStrength
                is CellInfoTdscdma -> cellInfo.cellSignalStrength
                is CellInfoWcdma -> cellInfo.cellSignalStrength
                else -> null
            }

            val cellIdentity = when (cellInfo) {
                is CellInfoCdma -> cellInfo.cellIdentity
                is CellInfoGsm -> cellInfo.cellIdentity
                is CellInfoLte -> cellInfo.cellIdentity
                is CellInfoNr -> cellInfo.cellIdentity
                is CellInfoTdscdma -> cellInfo.cellIdentity
                is CellInfoWcdma -> cellInfo.cellIdentity
                else -> null
            }

            // based on https://stackoverflow.com/questions/9283765/how-to-determine-if-network-type-is-2g-3g-or-4g
            val networkGeneration = when (cellInfo) {
                is CellInfoCdma, is CellInfoGsm -> "2G"
                is CellInfoTdscdma, is CellInfoWcdma -> "3G"
                is CellInfoLte -> "4G"
                is CellInfoNr -> "5G"
                else -> "Other"
            }

            // must be one of 1X, EVDO, WCDMA, GSM, HSPA, HSPA+, LTE, NRSA, NRNSA
            val networkSubtype = if (cellInfo.cellConnectionStatus == CONNECTION_PRIMARY_SERVING && activeNetworkSubtype != null) {
                activeNetworkSubtype
            } else {
                when (cellInfo) {
                    is CellInfoCdma -> "EVDO"
                    is CellInfoTdscdma -> "GSM"
                    is CellInfoWcdma -> "WCDMA"
                    is CellInfoGsm -> "GSM"
                    is CellInfoLte -> "LTE"
                    is CellInfoNr -> if (isNRNonStandAlone(cellInfos)) "NRNSA" else "NRSA"
                    else -> null
                }
            }

            val cellId = when (cellIdentity) {
                is CellIdentityGsm -> cellIdentity.cid
                is CellIdentityLte -> cellIdentity.ci
                is CellIdentityNr -> cellIdentity.nci
                is CellIdentityTdscdma -> cellIdentity.cid
                is CellIdentityWcdma -> cellIdentity.cid
                else -> null
            }

            val physicalCellId = when (cellIdentity) {
                is CellIdentityLte -> cellIdentity.pci
                is CellIdentityNr -> cellIdentity.pci
                else -> null
            }

            val spectrumBands = when (cellIdentity) {
                is CellIdentityLte -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cellIdentity.bands else null
                is CellIdentityNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cellIdentity.bands else null
                else -> null
            }

            val spectrumBandwidth = when (cellIdentity) {
                is CellIdentityLte -> cellIdentity.bandwidth / 1000
                else -> null
            }

            val arfcn = when (cellIdentity) {
                is CellIdentityGsm -> cellIdentity.arfcn
                is CellIdentityLte -> cellIdentity.earfcn
                is CellIdentityNr -> cellIdentity.nrarfcn
                is CellIdentityTdscdma -> cellIdentity.uarfcn
                is CellIdentityWcdma -> cellIdentity.uarfcn
                else -> null
            }

            val signalStrength = cellSignalStrength?.dbm

            val rssi = when (cellSignalStrength) {
                is CellSignalStrengthCdma -> cellSignalStrength.evdoDbm
                is CellSignalStrengthGsm -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cellSignalStrength.rssi else null
                is CellSignalStrengthLte -> cellSignalStrength.rssi
                else -> null
            }

            val rsrp = when (cellSignalStrength) {
                is CellSignalStrengthLte -> cellSignalStrength.rsrp
                is CellSignalStrengthNr -> cellSignalStrength.ssRsrp
                else -> null
            }

            val rsrq = when (cellSignalStrength) {
                is CellSignalStrengthLte -> cellSignalStrength.rsrq
                is CellSignalStrengthNr -> cellSignalStrength.ssRsrq
                else -> null
            }

            val cqi = when (cellSignalStrength) {
                is CellSignalStrengthLte -> cellSignalStrength.cqi
                is CellSignalStrengthNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cellSignalStrength.csiCqiReport.minOrNull() else null
                else -> null
            }

            val sinr = when (cellSignalStrength) {
                is CellSignalStrengthLte -> cellSignalStrength.rssnr
                is CellSignalStrengthNr -> cellSignalStrength.ssSinr
                else -> null
            }

            val csiRsrp = when (cellSignalStrength) {
                is CellSignalStrengthNr -> cellSignalStrength.csiRsrp
                else -> null
            }

            val csiRsrq = when (cellSignalStrength) {
                is CellSignalStrengthNr -> cellSignalStrength.csiRsrq
                else -> null
            }

            val csiSinr = when (cellSignalStrength) {
                is CellSignalStrengthNr -> cellSignalStrength.csiSinr
                else -> null
            }

            val nowInstant = Clock.System.now()
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            val timestampNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cellInfo.timestampMillis * 1000000L
            } else {
                cellInfo.timeStamp
            }

            val cell = Cell(
                timestamp = nowInstant.minus(nowNanos - timestampNanos, DateTimeUnit.NANOSECOND),
                cellId = if (cellId == UNAVAILABLE || cellId == UNAVAILABLE_LONG) null else cellId?.toLong(),
                physicalCellId = if (cellId == UNAVAILABLE) null else physicalCellId,
                cellConnection = if (cellInfo.cellConnectionStatus == CONNECTION_UNKNOWN) null else cellInfo.cellConnectionStatus,
                networkGeneration = networkGeneration,
                networkSubtype = networkSubtype,
                signalStrength = signalStrength,
                rssi = if (rssi == UNAVAILABLE) null else rssi,
                rsrp = if (rsrp == UNAVAILABLE) null else rsrp,
                rsrq = if (rsrq == UNAVAILABLE) null else rsrq,
                sinr = if (sinr == UNAVAILABLE) null else sinr,
                csiRsrp = if (csiRsrp == UNAVAILABLE) null else csiRsrp,
                csiRsrq = if (csiRsrq == UNAVAILABLE) null else csiRsrq,
                csiSinr = if (csiSinr == UNAVAILABLE) null else csiSinr,
                cqi = if (cqi == UNAVAILABLE) null else cqi,
                spectrumBand = spectrumBands?.joinToString(","),
                spectrumBandwidth = if (spectrumBandwidth == UNAVAILABLE) null else spectrumBandwidth?.toFloat(),
                arfcn = if (arfcn == UNAVAILABLE) null else arfcn,
            )

            cells.add(cell)
            Log.d(TAG, "cell (${cells.size}): $cell")
        }

        return cells
    }

    fun isUsingCarrierAggregation(cells: List<Cell>): Boolean {
        // There will only be multiple cells that are currently "serving" (primary or secondary) if
        // carrier aggregation is enabled.
        // See https://www.sharetechnote.com/html/Handbook_LTE_CellType.html
        return cells.filter { it.cellConnection == CONNECTION_PRIMARY_SERVING || it.cellConnection == CONNECTION_SECONDARY_SERVING }.size > 1
    }

    fun isNetworkAvailable(): Boolean {
        return availableNetworks.isNotEmpty()
    }

    fun isNetworkConnected(): Boolean {
        return telephonyManager.dataState == TelephonyManager.DATA_CONNECTED
    }

    fun isNetworkRoaming(): Boolean {
        return telephonyManager.isNetworkRoaming
    }

    // returns function to stop watching
    fun watchCells(onChange: (List<Cell>) -> Unit): () -> Unit {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object: TelephonyCallback(), TelephonyCallback.CellInfoListener {
                override fun onCellInfoChanged(cellInfos: MutableList<CellInfo>) {
                    onChange(getCells(cellInfos))
                }
            }

            telephonyManager.registerTelephonyCallback(appContext.mainExecutor, callback)
            return fun() { telephonyManager.unregisterTelephonyCallback(callback) }
        } else {
            val listener = object: PhoneStateListener() {
                override fun onCellInfoChanged(cellInfos: MutableList<CellInfo>) {
                    if (!PermissionManager.checkPermission()) return
                    super.onCellInfoChanged(cellInfos)
                    onChange(getCells(cellInfos))
                }
            }
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CELL_INFO)
            return fun() { telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE) }
        }
    }

    fun isCellularDataEnabled(): Boolean? {
        if (!PermissionManager.checkPermission()) return null
        return telephonyManager.isDataEnabled
    }

    fun getDisplayGeneration(cells: List<Cell>): String {
        return when {
            cells.find {
                it.networkGeneration == "5G" && it.cellConnection == CONNECTION_PRIMARY_SERVING
            } != null -> "5G"
            cells.find {
                it.networkGeneration == "5G" && it.cellConnection == CONNECTION_SECONDARY_SERVING
            } != null -> "4G/5G"
            else -> {
                val cell = cells.find { it.cellConnection == CONNECTION_PRIMARY_SERVING }
                    ?: cells.getOrNull(0)
                cell?.networkGeneration ?: ""
            }
        }
    }
}
