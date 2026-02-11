package edu.gatech.cc.cellwatch.core.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.ConcurrentHashMap

actual object SecureKeyStore {
    private const val STORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_ALIAS = "cellwatch-kmp-master-wrap-key"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val WRAPPED_PREFS = "cellwatch_secure_keys"
    private const val WRAPPED_KEY_PREFIX = "wrapped_key_"

    @Volatile
    private var appContext: Context? = null
    private val robolectricFallbackKeys = ConcurrentHashMap<String, String>()

    actual fun initialize(platformContext: Any?) {
        val context = platformContext as? Context
            ?: throw IllegalArgumentException(
                "SecureKeyStore.initialize on Android requires an android.content.Context"
            )
        appContext = context.applicationContext
    }

    actual fun getOrCreateKeyBase64(keyId: String): String {
        if (isRobolectric()) {
            return robolectricFallbackKeys.computeIfAbsent(keyId) { Encryptor.generateKeyBase64() }
        }

        val existing = getWrappedKey(keyId)?.let(::unwrapKeyBytes)
        if (existing != null) return existing

        val generated = Encryptor.generateKeyBase64()
        putWrappedKey(keyId, wrapKeyBytes(generated))
        return generated
    }

    actual fun deleteKey(keyId: String) {
        if (isRobolectric()) {
            robolectricFallbackKeys.remove(keyId)
            return
        }

        val context = requireContext()
        context.getSharedPreferences(WRAPPED_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(prefKey(keyId))
            .apply()
    }

    private fun requireContext(): Context {
        return appContext ?: throw IllegalStateException(
            "SecureKeyStore is not initialized. Call SecureKeyStore.initialize(context) first."
        )
    }

    private fun getWrappedKey(keyId: String): ByteArray? {
        val context = requireContext()
        val encoded = context.getSharedPreferences(WRAPPED_PREFS, Context.MODE_PRIVATE)
            .getString(prefKey(keyId), null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun putWrappedKey(keyId: String, wrapped: ByteArray) {
        val context = requireContext()
        val encoded = Base64.encodeToString(wrapped, Base64.NO_WRAP)
        context.getSharedPreferences(WRAPPED_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(prefKey(keyId), encoded)
            .apply()
    }

    private fun wrapKeyBytes(keyBase64: String): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val ciphertext = cipher.doFinal(keyBase64.encodeToByteArray())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun unwrapKeyBytes(wrapped: ByteArray): String {
        if (wrapped.isEmpty()) {
            throw IllegalStateException("Wrapped key payload is empty")
        }
        val ivSize = wrapped[0].toInt()
        if (ivSize <= 0 || wrapped.size <= ivSize) {
            throw IllegalStateException("Wrapped key payload is malformed")
        }
        val iv = wrapped.copyOfRange(1, 1 + ivSize)
        val ciphertext = wrapped.copyOfRange(1 + ivSize, wrapped.size)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return plaintext.decodeToString()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keystore = try {
            KeyStore.getInstance(STORE_PROVIDER).apply { load(null) }
        } catch (e: KeyStoreException) {
            throw IllegalStateException("AndroidKeyStore is unavailable in this runtime", e)
        }
        val existing = keystore.getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun prefKey(keyId: String): String = WRAPPED_KEY_PREFIX + keyId

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
}
