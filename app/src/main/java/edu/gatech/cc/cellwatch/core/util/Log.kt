package edu.gatech.cc.cellwatch.core.util

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

object Log {
    const val VERBOSE = android.util.Log.VERBOSE
    const val DEBUG = android.util.Log.DEBUG
    const val INFO = android.util.Log.INFO
    const val WARN = android.util.Log.WARN
    const val ERROR = android.util.Log.ERROR

    init {
        Firebase.crashlytics.setCustomKey("debug", BuildConfig.DEBUG)
        val deviceId = runBlocking { CellWatchApp.localDataStore.getDeviceId.firstOrNull() }
        Firebase.crashlytics.setCustomKey("device_id", deviceId ?: "NULL")
    }

    private fun log(level: Int, tag: String?, message: String?, throwable: Throwable?) {
        if (level >= INFO) {
            val msg = listOfNotNull(tag, message, throwable?.message).joinToString(" ")
            if (msg.isNotEmpty()) Firebase.crashlytics.log(msg)
        }

        if (level >= WARN) {
            Firebase.crashlytics.recordException(throwable ?: Exception(message))
        }

        if (BuildConfig.DEBUG) {
            android.util.Log.println(level, tag, listOfNotNull(message, throwable?.stackTraceToString()).joinToString(" "))
        }
    }

    fun v(tag: String?, message: String?, throwable: Throwable? = null) {
        log(VERBOSE, tag, message, throwable)
    }

    fun d(tag: String?, message: String?, throwable: Throwable? = null) {
        log(DEBUG, tag, message, throwable)
    }

    fun i(tag: String?, message: String?, throwable: Throwable? = null) {
        log(INFO, tag, message, throwable)
    }

    fun w(tag: String?, message: String?, throwable: Throwable? = null) {
        log(WARN, tag, message, throwable)
    }

    fun e(tag: String?, message: String?, throwable: Throwable? = null) {
        log(ERROR, tag, message, throwable)
    }
}