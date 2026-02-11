package edu.gatech.cc.cellwatch.core.util

actual object CryptographyInit {
    actual fun ensureInstalled() {
        // No-op until cryptography provider wiring is enabled for iOS.
    }
}
