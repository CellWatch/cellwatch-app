package edu.gatech.cc.cellwatch.core.util

const val DEFAULT_SECURE_KEY_ID = "cellwatch-device-secret-key"

/**
 * Platform secure storage for encryption key material.
 *
 * Android actual expects [initialize] with an Android Context before use.
 * iOS and JVM actuals ignore initialization.
 */
expect object SecureKeyStore {
    fun initialize(platformContext: Any? = null)
    fun getOrCreateKeyBase64(keyId: String = DEFAULT_SECURE_KEY_ID): String
    fun deleteKey(keyId: String = DEFAULT_SECURE_KEY_ID)
}

/**
 * Integration layer that combines platform secure key storage with shared Encryptor.
 */
object SecureEncryptor {
    fun encrypt(
        plaintext: String,
        keyId: String = DEFAULT_SECURE_KEY_ID,
        associatedData: String? = null,
    ): String {
        val key = SecureKeyStore.getOrCreateKeyBase64(keyId)
        return Encryptor.encrypt(plaintext, key, associatedData)
    }

    fun decrypt(
        envelope: String,
        keyId: String = DEFAULT_SECURE_KEY_ID,
        associatedData: String? = null,
    ): String {
        val key = SecureKeyStore.getOrCreateKeyBase64(keyId)
        return Encryptor.decrypt(envelope, key, associatedData)
    }
}
