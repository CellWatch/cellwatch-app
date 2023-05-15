package com.example.ndt8.data.core.model

import com.example.ndt8.data.local.model.UploadDownloadDataEntity
import com.example.ndt8.data.network.model.NetworkUploadDownloadData

fun NetworkUploadDownloadData.asEntity() = UploadDownloadDataEntity(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes,
    servers = ArrayList(servers),
    createdOn,
    updatedOn
)

fun UploadDownloadDataEntity.asNetworkModel() = NetworkUploadDownloadData(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes,
    servers = ArrayList(servers),
    createdOn,
    updatedOn
)