package edu.gatech.cc.cellwatch.domain.model

data class TcpTuple(
    val remoteAddress: String,
    val remotePort: Int,
    val timestamp: Long,
)
