package com.cellwatch.domain.msak.throughput

class UnexpectedCloseException(
    code: Int,
    reason: String?,
): Throwable("websocket closed with unexpected code: $code $reason") {}