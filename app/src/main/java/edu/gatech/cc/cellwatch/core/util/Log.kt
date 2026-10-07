package edu.gatech.cc.cellwatch.core.util

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.msak.Logger
import edu.gatech.cc.cellwatch.msak.setLogger
import kotlinx.coroutines.runBlocking

object Log : Logger {
    const val VERBOSE = android.util.Log.VERBOSE
    const val DEBUG = android.util.Log.DEBUG
    const val INFO = android.util.Log.INFO
    const val WARN = android.util.Log.WARN
    const val ERROR = android.util.Log.ERROR

    init {
        Firebase.crashlytics.setCustomKey("debug", BuildConfig.DEBUG)
        val deviceId = runBlocking { CellWatchApp.settingsRepository.getDeviceId() }
        Firebase.crashlytics.setCustomKey("device_id", deviceId ?: "NULL")

        setLogger(this)
    }

    private fun log(level: Int, tag: String?, message: String?, throwable: Throwable?) {
        if (level >= INFO) {
            val msg = listOfNotNull(tag, message, throwable?.message).joinToString(" ")
            if (msg.isNotEmpty()) Firebase.crashlytics.log(redactSecrets(msg))
        }

        if (level >= WARN) {
            Firebase.crashlytics.recordException(redactSecrets(throwable ?: Exception(message)))
        }

        if (BuildConfig.DEBUG) {
            android.util.Log.println(level, tag, redactSecrets(listOfNotNull(message, throwable?.stackTraceToString()).joinToString(" ")))
        }
    }

    override fun v(tag: String?, message: String?, throwable: Throwable?) {
        log(VERBOSE, tag, message, throwable)
    }

    override fun d(tag: String?, message: String?, throwable: Throwable?) {
        log(DEBUG, tag, message, throwable)
    }

    override fun i(tag: String?, message: String?, throwable: Throwable?) {
        log(INFO, tag, message, throwable)
    }

    override fun w(tag: String?, message: String?, throwable: Throwable?) {
        log(WARN, tag, message, throwable)
    }

    override fun e(tag: String?, message: String?, throwable: Throwable?) {
        log(ERROR, tag, message, throwable)
    }
}

// Supabase client errors put every request header in their message, including the device secret
// and API key, so everything this logger emits is scrubbed before it leaves the app.
private val SECRET_HEADER = Regex("""(?i)\b(x-device-secret|authorization|apikey)(=\[[^\]]*\]|\s*:\s*[^\s,\]}]+)""")
private val JWT = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
private const val REDACTED = "[REDACTED]"

internal fun redactSecrets(text: String): String =
    JWT.replace(SECRET_HEADER.replace(text) { "${it.groupValues[1]}=$REDACTED" }, REDACTED)

/**
 * Returns [throwable] itself if none of the messages in its cause chain contain secrets, and
 * otherwise a copy of the chain with redacted messages and the original stack traces.
 */
internal fun redactSecrets(throwable: Throwable): Throwable {
    val chain = generateSequence(throwable) { t -> t.cause?.takeIf { it !== t } }.take(10).toList()
    if (chain.none { t -> t.message?.let { redactSecrets(it) != it } == true }) return throwable

    return chain.foldRight<Throwable, Throwable?>(null) { t, cause ->
        val message = listOfNotNull(t.javaClass.name, t.message?.let { redactSecrets(it) }).joinToString(": ")
        RedactedException(message, cause).apply { stackTrace = t.stackTrace }
    }!!
}

private class RedactedException(message: String, cause: Throwable?) : Exception(message, cause)