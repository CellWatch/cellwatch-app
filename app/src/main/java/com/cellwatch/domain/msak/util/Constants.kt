package com.cellwatch.domain.msak.util

import java.nio.charset.Charset

// constants from the protocol itself
const val THROUGHPUT_WS_PROTO = "net.measurementlab.throughput.v1"
const val THROUGHPUT_MEASUREMENT_INTERVAL_MILLIS = 250L
const val THROUGHPUT_AVG_MEASUREMENT_INTERVAL_MILLIS = 250L
const val THROUGHPUT_MAX_MEASUREMENT_INTERVAL_MILLIS = 400L
const val THROUGHPUT_MIN_MEASUREMENT_INTERVAL_MILLIS = 100L
const val THROUGHPUT_MIN_MESSAGE_SIZE = 1 shl 10
const val THROUGHPUT_MAX_SCALED_MESSAGE_SIZE = 1 shl 20
const val THROUGHPUT_MESSAGE_SCALING_FRACTION = 16
const val LATENCY_DURATION = 5000L

// constants defined by us
const val THROUGHPUT_STREAMS = 3
const val THROUGHPUT_STREAM_DELAY = 0L // delay between launching each stream
const val THROUGHPUT_MAX_MILLIS = 10000L // must be at least 5 seconds more than max warmup
const val THROUGHPUT_MAX_WARMUP_MILLIS = 4000L
const val THROUGHPUT_USER_AGENT = "CellWatch/test"
const val LATENCY_END_DELAY = 2000L
val LATENCY_CHARSET = Charset.forName("utf-8")
const val MSAK_CONNECT_TIMEOUT_MILLIS = 5000L
const val MSAK_READ_TIMEOUT_MILLIS = 5000L
const val MSAK_WRITE_TIMEOUT_MILLIS = 5000L

// websocket status codes
const val WS_CODE_NORMAL_CLOSURE = 1000
const val WS_CODE_GOING_AWAY = 1001
const val WS_CODE_INTERNAL_ERROR = 1011