package com.example.ndtm

const val NDTM_WS_PROTO = "net.measurementlab.ndt.m"
const val NDTM_USER_AGENT = "CellWatch/test"
const val NDTM_MAX_MILLIS = 9000L // must be at least 5 seconds more than max warmup
const val NDTM_MAX_WARMUP_MILLIS = 3000L
const val NDTM_MEASUREMENT_INTERVAL_MILLIS = 250L
const val NDTM_MIN_MESSAGE_SIZE = 1 shl 10
const val NDTM_MAX_SCALED_MESSAGE_SIZE = 1 shl 20
const val NDTM_MESSAGE_SCALING_FRACTION = 16

const val WS_CODE_NORMAL_CLOSURE = 1000
const val WS_CODE_GOING_AWAY = 1001
const val WS_CODE_INTERNAL_ERROR = 1011