package com.cellwatch.domain.ndt8.model

data class Ndt8LocateServer(
    val machine: String,
    val location: Ndt8LocateServerLocation?,
    val urls: Map<String, String>,
)