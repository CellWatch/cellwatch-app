package edu.gatech.cc.cellwatch.domain.msak

const val LOCATE_THROUGHPUT_PATH = "msak/throughput1"
const val LOCATE_LATENCY_PATH = "msak/latency1"
const val THROUGHPUT_DOWNLOAD_PATH = "throughput/v1/download"
const val THROUGHPUT_UPLOAD_PATH = "throughput/v1/upload"
const val LATENCY_AUTHORIZE_PATH = "latency/v1/authorize"
const val LATENCY_RESULT_PATH = "latency/v1/result"
const val THROUGHPUT_WS_PROTO = "net.measurementlab.throughput.v1"
const val THROUGHPUT_AVG_MEASUREMENT_INTERVAL_MILLIS = 250L
const val THROUGHPUT_MAX_MEASUREMENT_INTERVAL_MILLIS = 400L
const val THROUGHPUT_MIN_MEASUREMENT_INTERVAL_MILLIS = 100L
const val THROUGHPUT_MIN_MESSAGE_SIZE = 1 shl 10
const val THROUGHPUT_MAX_SCALED_MESSAGE_SIZE = 1 shl 20
const val THROUGHPUT_MESSAGE_SCALING_FRACTION = 16
val LATENCY_CHARSET = Charsets.UTF_8
const val LATENCY_DURATION = 5000L
