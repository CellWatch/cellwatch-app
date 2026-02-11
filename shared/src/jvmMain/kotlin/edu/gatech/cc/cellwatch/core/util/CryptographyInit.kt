package edu.gatech.cc.cellwatch.core.util

actual object CryptographyInit {
    actual fun ensureInstalled() {
        // No-op for JVM target in current test/runtime setup.
    }
}
