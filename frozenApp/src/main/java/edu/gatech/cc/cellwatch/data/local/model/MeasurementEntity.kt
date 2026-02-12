package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.datetime.Instant
import java.util.UUID

@Entity
data class MeasurementEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

    @ColumnInfo(index = true)
    var groupId: String? = null,

    @ColumnInfo(index = true)
    val campaignId: String? = null,

    @ColumnInfo(index = true)
    val sessionId: String? = null,

    @ColumnInfo(index = true)
    val deviceId: String? = null,

    val deviceManufacturer: String? = null,

    val deviceModel: String? = null,

    val deviceOsName: String? = null,

    val deviceOsVersion: String? = null,

    val appName: String? = null,

    val provider: String? = null,

    val type: String,

    val timestamp: Instant? = null,

    val duration: Long? = null,

    val scheduled: Boolean? = null,

    val success: Boolean? = null,

    val carrierAggregation: Boolean? = null,

    val networkConnected: Boolean? = null,

    val networkAvailable: Boolean? = null,

    val networkRoaming: Boolean? = null,

    val simMcc: String? = null,

    val simMnc: String? = null,

    val netMcc: String? = null,

    val netMnc: String? = null,

    val connectionType: NetworkConnectionType?,

    val cellularDataEnabled: Boolean?,

    val extraData: String? = null,

    val createdOn: Instant? = null,

    val updatedOn: Instant? = null,

    // When was this record pushed to cloud storage?
    var uploadTime: Instant? = null,
    val appVersion: String? = null,
)

fun MeasurementEntity.asExternalModel() = Measurement(
    id,
    groupId,
    campaignId,
    sessionId,
    deviceId,
    deviceManufacturer,
    deviceModel,
    deviceOsName,
    deviceOsVersion,
    appName,
    provider,
    type,
    timestamp,
    duration,
    scheduled,
    success,
    carrierAggregation,
    networkConnected,
    networkAvailable,
    networkRoaming,
    simMcc,
    simMnc,
    netMcc,
    netMnc,
    extraData,
    createdOn,
    updatedOn,
    connectionType = connectionType,
    cellularDataEnabled = cellularDataEnabled,
    uploadTime = uploadTime,
    appVersion = appVersion,
)
