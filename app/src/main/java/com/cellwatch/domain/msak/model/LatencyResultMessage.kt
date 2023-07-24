package com.cellwatch.domain.msak.model

data class LatencyResultMessage(
    val ID: String,
    val StartTime: String,
    val RoundTrips: List<LatencyRoundTrip>,
    val PacketsSent: Int,
    val PacketsReceived: Int,
)
