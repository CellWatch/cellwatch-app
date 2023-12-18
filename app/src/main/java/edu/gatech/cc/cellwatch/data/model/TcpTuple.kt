package edu.gatech.cc.cellwatch.data.model

data class TcpTuple(
    val remoteAddress: String,
    val remotePort: Int,
    val timestamp: Long
)
