package edu.gatech.cc.cellwatch.data.core.model

import edu.gatech.cc.cellwatch.data.local.model.UploadDownloadDataEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkUploadDownloadData

fun NetworkUploadDownloadData.asEntity() = UploadDownloadDataEntity(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes, applicationBytes,
    servers = servers?.let { ArrayList(it) },
    createdOn,
    updatedOn
)

fun UploadDownloadDataEntity.asNetworkModel() = NetworkUploadDownloadData(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes, applicationBytes,
    servers = servers?.let { ArrayList(it) },
    createdOn,
    updatedOn
)
