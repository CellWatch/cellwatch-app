package com.example.ndtm

class NdtMUnexpectedCloseException(
    code: Int,
    reason: String?,
): Throwable("websocket closed with unexpected code: $code $reason") {}