package com.cellwatch.data.core.model

import com.cellwatch.data.local.model.UploadDownloadDataEntity
import com.cellwatch.data.network.model.NetworkUploadDownloadData

fun NetworkUploadDownloadData.asEntity() = UploadDownloadDataEntity(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes, applicationBytes,
    servers = ArrayList(servers),
    createdOn,
    updatedOn
)

fun UploadDownloadDataEntity.asNetworkModel() = NetworkUploadDownloadData(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes, applicationBytes,
    servers = ArrayList(servers),
    createdOn,
    updatedOn
)