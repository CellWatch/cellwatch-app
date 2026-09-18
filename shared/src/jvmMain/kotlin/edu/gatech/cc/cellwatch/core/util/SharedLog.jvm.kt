package edu.gatech.cc.cellwatch.core.util

internal actual fun writePlatformLog(level: LogLevel, tag: String, message: String) {
    println("[${level.name.first()}] $tag: $message")
}
