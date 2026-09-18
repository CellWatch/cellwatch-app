package edu.gatech.cc.cellwatch.core.util

/**
 * stdout rather than NSLog, deliberately.
 *
 * NSLog's varargs/format bridging can segfault under Kotlin/Native - msak hit
 * this and switched to stdout for the same reason (see its commented-out
 * Log.ios.kt), and reintroducing NSLog here crashed a unit test outright.
 *
 * The cost is that stdout is only visible while something is attached to it
 * (Xcode, or `devicectl device process launch --console`), so these lines do
 * not survive an untethered run the way unified-log entries would. A logger
 * that crashes the app is worse than one that is sometimes invisible.
 */
internal actual fun writePlatformLog(level: LogLevel, tag: String, message: String) {
    println("[${level.name.first()}] $tag: $message")
}
