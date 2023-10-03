package com.cellwatch.domain.telephony.managers

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
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
import android.telephony.TelephonyManager
import android.util.Log
import com.cellwatch.CellWatchApp
import com.cellwatch.core.util.PermissionManager
import com.cellwatch.data.model.Cell
import kotlinx.datetime.Clock
import java.util.Objects

enum class NetworkConnectionType {
    NONE, WIFI, CELLULAR, VPN
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
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build(),
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

    fun getConnectionType(): NetworkConnectionType {
        var result = NetworkConnectionType.NONE // Returns connection type. 0: none; 1: mobile data; 2: wifi; 3: vpn
        val cm = connectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.run {
                cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                    if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        result = NetworkConnectionType.WIFI
                    } else if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        result = NetworkConnectionType.CELLULAR
                    } else if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)){
                        result = NetworkConnectionType.VPN
                    }
                }
            }
        } else {
            cm.run {
                cm.activeNetworkInfo?.run {
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        result = NetworkConnectionType.WIFI
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        result = NetworkConnectionType.CELLULAR
                    } else if(type == ConnectivityManager.TYPE_VPN) {
                        result = NetworkConnectionType.VPN
                    }
                }
            }
        }
        return result
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

    fun getCells(): List<Cell>? {
        if (!PermissionManager.checkPermission()) return null

        val cellInfoList: List<CellInfo> = Objects.requireNonNull(telephonyManager).allCellInfo
        Log.d(TAG, "cellInfoList length = ${cellInfoList.size}")
        val cells = mutableListOf<Cell>()
        val timestamp = Clock.System.now()

        // TODO: filter only registered cells?
        for (cellInfo in cellInfoList) {
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

            // TODO: is this correct?
            val networkGeneration = when (cellInfo) {
                is CellInfoCdma, is CellInfoTdscdma, is CellInfoWcdma -> "3G"
                is CellInfoGsm -> "2G"
                is CellInfoLte -> "4G"
                is CellInfoNr -> "5G"
                else -> "Other"
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

            val cell = Cell(
                timestamp = timestamp,
                cellId = if (cellId == UNAVAILABLE || cellId == UNAVAILABLE_LONG) null else cellId?.toLong(),
                physicalCellId = if (cellId == UNAVAILABLE) null else physicalCellId,
                cellConnection = if (cellInfo.cellConnectionStatus == CONNECTION_UNKNOWN) null else cellInfo.cellConnectionStatus,
                networkGeneration = networkGeneration,
                networkSubtype = getNetworkSubType(), // TODO: get for this cell, not just for current connection
                signalStrength = signalStrength,
                rssi = if (rssi == UNAVAILABLE) null else rssi,
                rsrp = if (rsrp == UNAVAILABLE) null else rsrp,
                rsrq = if (rsrq == UNAVAILABLE) null else rsrq,
                sinr = if (sinr == UNAVAILABLE) null else sinr,
                csiRsrp = if (csiRsrp == UNAVAILABLE) null else csiRsrp,
                csiRsrq = if (csiRsrq == UNAVAILABLE) null else csiRsrq,
                csiSinr = if (csiSinr == UNAVAILABLE) null else csiSinr,
                cqi = if (cqi == UNAVAILABLE) null else cqi,
                spectrumBand = spectrumBands?.joinToString(","), // TODO: is this formatted correctly
                spectrumBandwidth = if (spectrumBandwidth == UNAVAILABLE) null else spectrumBandwidth?.toFloat(),
                arfcn = if (arfcn == UNAVAILABLE) null else arfcn,
            )

            cells.add(cell)
            Log.d(TAG, "cell (${cells.size}): $cell")
        }

        return cells
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
}