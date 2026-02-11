package edu.gatech.cc.cellwatch.core.util

import java.util.concurrent.ConcurrentHashMap

actual object SecureKeyStore {
    private val keys = ConcurrentHashMap<String, String>()

    actual fun initialize(platformContext: Any?) {
        // JVM implementation does not require explicit initialization.
    }

    actual fun getOrCreateKeyBase64(keyId: String): String {
        return keys.computeIfAbsent(keyId) { Encryptor.generateKeyBase64() }
    }

    actual fun deleteKey(keyId: String) {
        keys.remove(keyId)
    }
}
