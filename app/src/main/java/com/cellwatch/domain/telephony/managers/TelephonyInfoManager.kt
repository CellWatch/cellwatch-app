package com.cellwatch.domain.telephony.managers

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellSignalStrengthLte
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.cellwatch.CellWatchApp
import com.cellwatch.core.util.PermissionManager
import com.cellwatch.data.model.Cell
import kotlinx.datetime.Clock
import java.util.Objects
import kotlin.math.sign

object TelephonyInfoManager {
    private val TAG = this::class.simpleName
    private val appContext = CellWatchApp.applicationContext()
    private var telephonyManager: TelephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as
            TelephonyManager

    fun getNetworkGeneration(): String? {
        if (!PermissionManager.checkPermission()) return null

        // ConnectionManager instance
        val connectivityManager = appContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork)

        if (networkCapabilities!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return when (Objects.requireNonNull(telephonyManager).dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN, 19 -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> "UNKNOWN"
            }
        }
        return null
    }

    fun getNetworkSubType(): String? {
        if (!PermissionManager.checkPermission()) return null

        // ConnectionManager instance
        val connectivityManager = appContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val currentNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork)

        if (networkCapabilities!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return when (Objects.requireNonNull(telephonyManager).dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
                TelephonyManager.NETWORK_TYPE_1xRTT -> "1X"
                TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
                TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSPA+"
                TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "SCDMA"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
                TelephonyManager.NETWORK_TYPE_NR -> "NR"
                else -> "UNKNOWN"
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getCells(): List<Cell>? {
        if (!PermissionManager.checkPermission()) return null

        val cellInfoList: List<CellInfo> = Objects.requireNonNull(telephonyManager).allCellInfo

//        if (PermissionManager.checkPermission()) {
//            cellInfoList = Objects.requireNonNull(telephonyManager).allCellInfo
//        } else return null

        Log.d(TAG, "cellInfoList length = ${cellInfoList.size}")

        val cells = mutableListOf<Cell>()

        var cellIndex = 0

        for (cellInfo in cellInfoList) {
            when (cellInfo) {
                is CellInfoLte -> {
                    Log.d(TAG, "======= Cell($cellIndex) =======")
                    cellIndex++
                    val cellInfoLte = cellInfo as CellInfoLte
                    val signalStrength: CellSignalStrengthLte = cellInfo.cellSignalStrength
                    Log.d(TAG, "*** LTE Connection ***")
                    Log.d(TAG, "cid = ${cellInfo.cellIdentity.ci}")
                    Log.d(TAG, "pci = ${cellInfo.cellIdentity.pci}")
                    Log.d(TAG, "dbm = ${signalStrength.dbm}")
                    Log.d(TAG, "rsrp = ${signalStrength.rsrp}")
                    Log.d(TAG, "rsrq = ${signalStrength.rsrq}")
                    Log.d(TAG, "cqi = ${signalStrength.cqi}")
                    Log.d(TAG, "rssnr = ${signalStrength.rssnr}")
                    Log.d(TAG, "rssi = ${signalStrength.rssi}")
                    Log.d(TAG, "level = ${signalStrength.level}")
                    Log.d(TAG, "asuLevel = ${signalStrength.asuLevel}")
                    Log.d(TAG, "cellInfo = ${cellInfo.cellIdentity}")

                    val networkGeneration = getNetworkGeneration()
                    val networkSubtype = getNetworkSubType()

                    val sigStrength = when (networkGeneration) {
                        "3G", "2G" -> signalStrength.rssi
                        "4G", "5G" -> signalStrength.rsrp
                        else -> null
                    }

                    val cellIdentity: CellIdentityLte = cellInfo.cellIdentity

                    Log.d(TAG, "Number of Bands: ${cellIdentity.bands.size}")
                    val bands = cellIdentity.bands.joinToString(prefix = "[", separator = ", ", postfix = "]")
                    Log.d(TAG, "bands = $bands")
                    Log.d(TAG, "spectrumBandwidth = ${cellIdentity.bandwidth.toFloat()}")

                    cells.add(
                        Cell(
                            timestamp = Clock.System.now(),
                            cellId = cellInfo.cellIdentity.ci,
                            physicalCellId = cellInfo.cellIdentity.pci,
                            cellConnection = null, // TODO
                            networkGeneration = networkGeneration,
                            networkSubtype = networkSubtype,
                            signalStrength = sigStrength,
                            rssi = signalStrength.rssi,
                            rsrp = signalStrength.rsrp,
                            rsrq = signalStrength.rsrq,
                            sinr = if (networkGeneration == "3G") null else signalStrength.rssnr,
                            csiRsrp = if (networkGeneration == "5G") signalStrength.rsrp else null,
                            csiRsrq = if (networkGeneration == "5G") signalStrength.rsrq else null,
                            csiSinr = if (networkGeneration == "5G") signalStrength.rssnr else null,
                            cqi = if (networkGeneration == "3G") null else signalStrength.cqi,
                            spectrumBand = cellIdentity.bands.toString(),
//                            spectrumBand = "TODO_BAND", // cellIdentity.bands.toString(),
                            spectrumBandwidth = cellIdentity.bandwidth.toFloat(),
                            arfcn = cellIdentity.earfcn
                        )
                    )
                }

                is CellInfoGsm -> {
                    Log.d(TAG, "*** GSM Connection ***")
                    Log.d(TAG, "GSM Cell Identity = ${cellInfo.cellIdentity}")
                    Log.d(TAG, "GSM Signal Strength = ${cellInfo.cellSignalStrength}")
                }
            }
        }

        return cells
    }
}