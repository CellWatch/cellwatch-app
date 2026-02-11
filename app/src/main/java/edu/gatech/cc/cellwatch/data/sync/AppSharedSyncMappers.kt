package edu.gatech.cc.cellwatch.data.sync

import edu.gatech.cc.cellwatch.data.model.Cell as AppCell
import edu.gatech.cc.cellwatch.data.model.FccSubmission as AppFccSubmission
import edu.gatech.cc.cellwatch.data.model.LatencyData as AppLatencyData
import edu.gatech.cc.cellwatch.data.model.Location as AppLocation
import edu.gatech.cc.cellwatch.data.model.Measurement as AppMeasurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData as AppUploadDownloadData
import edu.gatech.cc.cellwatch.domain.model.Cell as SharedCell
import edu.gatech.cc.cellwatch.domain.model.FccSubmission as SharedFccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData as SharedLatencyData
import edu.gatech.cc.cellwatch.domain.model.Location as SharedLocation
import edu.gatech.cc.cellwatch.domain.model.Measurement as SharedMeasurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType as SharedNetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData as SharedUploadDownloadData
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType as AppNetworkConnectionType

fun AppMeasurement.toShared(): SharedMeasurement = SharedMeasurement(
    id = id,
    groupId = groupId,
    campaignId = campaignId,
    sessionId = sessionId,
    deviceId = deviceId,
    deviceManufacturer = deviceManufacturer,
    deviceModel = deviceModel,
    deviceOsName = deviceOsName,
    deviceOsVersion = deviceOsVersion,
    appName = appName,
    provider = provider,
    type = type,
    timestamp = timestamp,
    duration = duration,
    scheduled = scheduled,
    success = success,
    carrierAggregation = carrierAggregation,
    networkConnected = networkConnected,
    networkAvailable = networkAvailable,
    networkRoaming = networkRoaming,
    simMcc = simMcc,
    simMnc = simMnc,
    netMcc = netMcc,
    netMnc = netMnc,
    extraData = extraData,
    createdOn = createdOn,
    updatedOn = updatedOn,
    uploadDownloadData = uploadDownloadData?.toShared(),
    latencyData = latencyData?.toShared(),
    locations = locations?.map { it.toShared() },
    cells = cells?.map { it.toShared() },
    connectionType = connectionType?.toShared(),
    cellularDataEnabled = cellularDataEnabled,
    uploadTime = uploadTime,
    appVersion = appVersion,
)

fun AppFccSubmission.toShared(): SharedFccSubmission = SharedFccSubmission(
    id = id,
    challengeDataId = challengeDataId,
    contactName = contactName,
    contactEmail = contactEmail,
    contactPhone = contactPhone,
    deviceTimestamp = deviceTimestamp,
    serverTimestamp = serverTimestamp,
    sourceIp = sourceIp,
    sourcePort = sourcePort,
    deviceId = deviceId,
    deviceImei = deviceImei,
    deviceTac = deviceTac,
    deviceType = deviceType,
    deviceManufacturer = deviceManufacturer,
    deviceModel = deviceModel,
    deviceOsName = deviceOsName,
    appName = appName,
    appVersion = appVersion,
    provider = provider,
    simCountryCode = simCountryCode,
    simNetworkCode = simNetworkCode,
    netCountryCode = netCountryCode,
    netNetworkCode = netNetworkCode,
    inVehicle = inVehicle,
    externalAntenna = externalAntenna,
    submitted = submitted,
    submittedOn = submittedOn,
    submission = submission,
    submissionResponse = submissionResponse,
    createdOn = createdOn,
    updatedOn = updatedOn,
    uploadTime = uploadTime,
)

private fun AppUploadDownloadData.toShared(): SharedUploadDownloadData = SharedUploadDownloadData(
    id = id,
    measurementId = measurementId,
    warmupDuration = warmupDuration,
    warmupBytes = warmupBytes,
    duration = duration,
    bytes = bytes,
    bytesPerSec = bytesPerSec,
    applicationBytes = applicationBytes,
    applicationBytesPerSec = applicationBytesPerSec,
    servers = servers,
    createdOn = createdOn,
    updatedOn = updatedOn,
)

private fun AppLatencyData.toShared(): SharedLatencyData = SharedLatencyData(
    id = id,
    measurementId = measurementId,
    rtt = rtt,
    jitter = jitter,
    sent = sent,
    received = received,
    servers = servers,
    createdOn = createdOn,
    updatedOn = updatedOn,
)

private fun AppLocation.toShared(): SharedLocation = SharedLocation(
    id = id,
    timestamp = timestamp,
    lat = lat,
    lon = lon,
    accuracy = accuracy,
    speed = speed,
    speedAccuracy = speedAccuracy,
    heading = heading,
    measurementId = measurementId,
    createdOn = createdOn,
    updatedOn = updatedOn,
)

private fun AppCell.toShared(): SharedCell = SharedCell(
    id = id,
    timestamp = timestamp,
    cellId = cellId,
    physicalCellId = physicalCellId,
    cellConnection = cellConnection,
    networkGeneration = networkGeneration,
    networkSubtype = networkSubtype,
    signalStrength = signalStrength,
    rssi = rssi,
    rsrp = rsrp,
    rsrq = rsrq,
    sinr = sinr,
    csiRsrp = csiRsrp,
    csiRsrq = csiRsrq,
    csiSinr = csiSinr,
    cqi = cqi,
    spectrumBand = spectrumBand,
    spectrumBandwidth = spectrumBandwidth,
    arfcn = arfcn,
    measurementId = measurementId,
    createdOn = createdOn,
    updatedOn = updatedOn,
)

private fun AppNetworkConnectionType.toShared(): SharedNetworkConnectionType = when (this) {
    AppNetworkConnectionType.NONE -> SharedNetworkConnectionType.NONE
    AppNetworkConnectionType.WIFI -> SharedNetworkConnectionType.WIFI
    AppNetworkConnectionType.CELLULAR -> SharedNetworkConnectionType.CELLULAR
}
