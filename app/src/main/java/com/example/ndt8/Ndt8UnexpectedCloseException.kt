package com.example.ndt8

class Ndt8UnexpectedCloseException(
    code: Int,
    reason: String?,
): Throwable("websocket closed with unexpected code: $code $reason") {}