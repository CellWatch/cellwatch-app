package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.msak.LATENCY_AUTHORIZE_PATH
import edu.gatech.cc.cellwatch.msak.LATENCY_RESULT_PATH
import edu.gatech.cc.cellwatch.msak.Server
import edu.gatech.cc.cellwatch.msak.THROUGHPUT_DOWNLOAD_PATH
import edu.gatech.cc.cellwatch.msak.THROUGHPUT_UPLOAD_PATH

class UnreachableServer(host: String): Server(host, null, mapOf(
    "ws:///$THROUGHPUT_UPLOAD_PATH" to "ws://0.0.0.0",
    "ws:///$THROUGHPUT_DOWNLOAD_PATH" to "ws://0.0.0.0",
    "http:///$LATENCY_AUTHORIZE_PATH" to "ws://0.0.0.0",
    "http:///$LATENCY_RESULT_PATH" to "ws://0.0.0.0",
))