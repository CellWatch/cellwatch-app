package edu.gatech.cc.cellwatch.domain.capability

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

class AndroidPlatformCapabilityProvider(
    private val context: Context,
    private val clock: Clock = Clock.System,
) : PlatformCapabilityProvider {

    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
        val now = clock.now()
        return PlatformCapabilitySnapshot(
            capturedAt = now,
            telephony = captureTelephony(now),
            network = captureNetwork(),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.UNAVAILABLE,
                note = "location capture adapter not yet wired for Android provider",
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                osName = "Android",
                osVersion = Build.VERSION.RELEASE,
                note = null,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun captureTelephony(capturedAt: Instant): TelephonyCapabilitySnapshot {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return TelephonyCapabilitySnapshot(
                support = CapabilitySupport.PERMISSION_DENIED,
                note = "missing READ_PHONE_STATE permission",
            )
        }
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return TelephonyCapabilitySnapshot(
                support = CapabilitySupport.UNAVAILABLE,
                note = "telephony service unavailable",
            )

        val rawCells = runCatching { telephony.allCellInfo }.getOrNull().orEmpty()
        val cells = rawCells.map { toDomainCell(it, capturedAt) }
        val netOperator = telephony.networkOperator
        val simOperator = telephony.simOperator
        val hasAnySignal = listOf(
            telephony.networkOperatorName,
            netOperator,
            simOperator,
        ).any { !it.isNullOrBlank() } || cells.isNotEmpty()

        return TelephonyCapabilitySnapshot(
            support = if (hasAnySignal) CapabilitySupport.PARTIAL else CapabilitySupport.UNAVAILABLE,
            provider = telephony.networkOperatorName?.trim()?.takeIf { it.isNotEmpty() },
            simMcc = parseMcc(simOperator),
            simMnc = parseMnc(simOperator),
            netMcc = parseMcc(netOperator),
            netMnc = parseMnc(netOperator),
            networkGeneration = networkGeneration(telephony.dataNetworkType),
            networkSubtype = networkSubtype(telephony.dataNetworkType),
            cells = cells,
            note = "best-effort Android telephony snapshot",
        )
    }

    @SuppressLint("MissingPermission")
    private fun captureNetwork(): NetworkCapabilitySnapshot {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkCapabilitySnapshot(
                support = CapabilitySupport.UNAVAILABLE,
                note = "connectivity service unavailable",
            )
        val activeNetwork = connectivity.activeNetwork
        val capabilities = activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val connected = capabilities != null
        val connectionType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkConnectionType.CELLULAR
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkConnectionType.WIFI
            connected -> NetworkConnectionType.NONE
            else -> null
        }
        return NetworkCapabilitySnapshot(
            support = CapabilitySupport.PARTIAL,
            connected = connected,
            available = connected,
            roaming = if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                runCatching {
                    (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.isNetworkRoaming
                }.getOrNull()
            } else {
                null
            },
            connectionType = connectionType,
            cellularDataEnabled = null,
            note = "best-effort Android connectivity snapshot",
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun parseMcc(operator: String?): String? {
        if (operator.isNullOrBlank() || operator.length < 3) return null
        return operator.substring(0, 3)
    }

    private fun parseMnc(operator: String?): String? {
        if (operator.isNullOrBlank() || operator.length <= 3) return null
        return operator.substring(3)
    }

    private fun networkGeneration(networkType: Int): String? = when (networkType) {
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
        else -> null
    }

    private fun networkSubtype(networkType: Int): String? = when (networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_UMTS -> "WCDMA"
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        else -> null
    }

    @SuppressLint("NewApi")
    private fun toDomainCell(cellInfo: CellInfo, capturedAt: Instant): Cell {
        val signal = cellInfo.cellSignalStrength
        val identity = when (cellInfo) {
            is CellInfoCdma -> cellInfo.cellIdentity
            is CellInfoGsm -> cellInfo.cellIdentity
            is CellInfoLte -> cellInfo.cellIdentity
            is CellInfoNr -> cellInfo.cellIdentity
            is CellInfoTdscdma -> cellInfo.cellIdentity
            is CellInfoWcdma -> cellInfo.cellIdentity
            else -> null
        }

        return Cell(
            id = UUID.randomUUID().toString(),
            timestamp = capturedAt,
            cellId = when (identity) {
                is CellIdentityLte -> identity.ci.toLong().nullIfUnavailable()
                is CellIdentityNr -> identity.nci.nullIfUnavailable()
                is CellIdentityWcdma -> identity.cid.toLong().nullIfUnavailable()
                is CellIdentityTdscdma -> identity.cid.toLong().nullIfUnavailable()
                is CellIdentityGsm -> identity.cid.toLong().nullIfUnavailable()
                is CellIdentityCdma -> identity.basestationId.toLong().nullIfUnavailable()
                else -> null
            },
            physicalCellId = when (identity) {
                is CellIdentityLte -> identity.pci.nullIfUnavailable()
                is CellIdentityNr -> identity.pci.nullIfUnavailable()
                is CellIdentityWcdma -> identity.psc.nullIfUnavailable()
                is CellIdentityTdscdma -> identity.cpid.nullIfUnavailable()
                is CellIdentityGsm -> identity.arfcn.nullIfUnavailable()
                else -> null
            },
            cellConnection = cellInfo.cellConnectionStatus.nullIfUnavailable(),
            networkGeneration = when (identity) {
                is CellIdentityNr -> "5G"
                is CellIdentityLte -> "4G"
                is CellIdentityWcdma, is CellIdentityTdscdma -> "3G"
                is CellIdentityGsm, is CellIdentityCdma -> "2G"
                else -> null
            },
            networkSubtype = null,
            signalStrength = signal?.dbm?.nullIfUnavailable(),
            rssi = signal?.dbm?.nullIfUnavailable(),
            rsrp = null,
            rsrq = null,
            sinr = null,
            csiRsrp = null,
            csiRsrq = null,
            csiSinr = null,
            cqi = null,
            spectrumBand = null,
            spectrumBandwidth = null,
            arfcn = when (identity) {
                is CellIdentityLte -> identity.earfcn.nullIfUnavailable()
                is CellIdentityNr -> identity.nrarfcn.nullIfUnavailable()
                is CellIdentityWcdma -> identity.uarfcn.nullIfUnavailable()
                is CellIdentityTdscdma -> identity.uarfcn.nullIfUnavailable()
                is CellIdentityGsm -> identity.arfcn.nullIfUnavailable()
                else -> null
            },
            measurementId = null,
            createdOn = capturedAt,
            updatedOn = capturedAt,
        )
    }
}

private fun Int.nullIfUnavailable(): Int? = if (this == Int.MAX_VALUE || this == Int.MIN_VALUE) null else this

private fun Long.nullIfUnavailable(): Long? = if (this == Long.MAX_VALUE || this == Long.MIN_VALUE) null else this
