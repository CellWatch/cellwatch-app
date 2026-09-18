package edu.gatech.cc.cellwatch.core.util

/**
 * Minimal logging for shared code.
 *
 * `shared/` had no logging at all - the legacy `Log.kt` in this package is
 * commented out in its entirety - so shared code could compute something and
 * then silently discard it. That cost real time: the detected network
 * interface and the sync report explaining why nothing uploaded were both
 * already being computed, and neither could be seen until a diagnostic was
 * added by hand.
 *
 * Deliberately small. No levels beyond the four, no formatting, no crash
 * reporting. Reinstating Crashlytics is tracked separately as a low-priority
 * item in doc/PARITY_AND_FCC_CORRECTNESS_PLAN.md - it is a third-party SDK
 * collecting from an app that gathers location traces, so it is a governance
 * decision rather than a technical one. Nothing here depends on that answer.
 */
object SharedLog {
    fun d(tag: String, message: String) = writePlatformLog(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = writePlatformLog(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        writePlatformLog(LogLevel.WARN, tag, compose(message, throwable))
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        writePlatformLog(LogLevel.ERROR, tag, compose(message, throwable))

    private fun compose(message: String, throwable: Throwable?): String =
        if (throwable == null) message else "$message (${throwable::class.simpleName}: ${throwable.message})"
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

internal expect fun writePlatformLog(level: LogLevel, tag: String, message: String)
