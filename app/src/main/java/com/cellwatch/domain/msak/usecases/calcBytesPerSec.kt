package com.cellwatch.domain.msak.usecases

fun calcBytesPerSec(bytes: Long, usecs: Long): Double {
    return if (usecs == 0L) 0.0 else bytes.toDouble() / usecs.toDouble() * 1e6
}