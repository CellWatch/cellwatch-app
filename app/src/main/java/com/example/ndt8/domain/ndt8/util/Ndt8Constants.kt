package com.example.ndt8.domain.ndt8.util

// constants from the protocol itself
const val NDT8_WS_PROTO = "net.measurementlab.ndt.v8"
const val NDT8_AVG_MEASUREMENT_INTERVAL_MILLIS = 250L
const val NDT8_MAX_MEASUREMENT_INTERVAL_MILLIS = 400L
const val NDT8_MIN_MEASUREMENT_INTERVAL_MILLIS = 100L
const val NDT8_MIN_MESSAGE_SIZE = 1 shl 10
const val NDT8_MAX_SCALED_MESSAGE_SIZE = 1 shl 20
const val NDT8_MESSAGE_SCALING_FRACTION = 16

// constants defined by us
const val NDT8_STREAMS = 3
const val NDT8_STREAM_DELAY = 0L // delay between launching each stream
const val NDT8_MAX_MILLIS = 10000L // must be at least 5 seconds more than max warmup
const val NDT8_MAX_WARMUP_MILLIS = 4000L
const val NDT8_USER_AGENT = "CellWatch/test"
const val NDT8_CONNECT_TIMEOUT_MILLIS = 5000L
const val NDT8_READ_TIMEOUT_MILLIS = 5000L
const val NDT8_WRITE_TIMEOUT_MILLIS = 5000L

// websocket status codes
const val WS_CODE_NORMAL_CLOSURE = 1000
const val WS_CODE_GOING_AWAY = 1001
const val WS_CODE_INTERNAL_ERROR = 1011