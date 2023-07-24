package com.cellwatch.domain.msak.model

data class LocateServer(
    val machine: String,
    val location: LocateServerLocation?,
    val urls: Map<String, String>,
)