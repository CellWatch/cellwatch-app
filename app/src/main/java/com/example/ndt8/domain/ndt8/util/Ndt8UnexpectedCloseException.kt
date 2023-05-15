package com.example.ndt8.domain.ndt8.util

class Ndt8UnexpectedCloseException(
    code: Int,
    reason: String?,
): Throwable("websocket closed with unexpected code: $code $reason") {}