package edu.gatech.cc.cellwatch.core.util

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import edu.gatech.cc.cellwatch.BuildConfig

object Log {
    val VERBOSE = android.util.Log.VERBOSE
    val DEBUG = android.util.Log.DEBUG
    val INFO = android.util.Log.INFO
    val WARN = android.util.Log.WARN
    val ERROR = android.util.Log.ERROR

    init {
        Firebase.crashlytics.setCustomKey("debug", BuildConfig.DEBUG)
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