package edu.gatech.cc.cellwatch.core.util

actual object CryptographyInit {
    actual fun ensureInstalled() {
        // Android providers auto-register for current usage.
    }
}
