package edu.gatech.cc.cellwatch.core.util

import android.util.Log

internal actual fun writePlatformLog(level: LogLevel, tag: String, message: String) {
    when (level) {
        LogLevel.DEBUG -> Log.d(tag, message)
        LogLevel.INFO -> Log.i(tag, message)
        LogLevel.WARN -> Log.w(tag, message)
        LogLevel.ERROR -> Log.e(tag, message)
    }
}
