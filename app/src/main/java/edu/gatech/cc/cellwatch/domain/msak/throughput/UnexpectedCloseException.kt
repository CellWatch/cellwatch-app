package edu.gatech.cc.cellwatch.domain.msak.throughput

class UnexpectedCloseException(
    code: Int,
    reason: String?,
): Exception("websocket closed with unexpected code: $code $reason") {}
