package edu.gatech.cc.cellwatch.core.util

import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.symmetric.AES
import dev.whyoleg.cryptography.algorithms.symmetric.SymmetricKeySize
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cross-platform authenticated encryption for short secrets.
 *
 * Contract:
 * - AES-256-GCM
 * - Versioned envelope (`cw1:` prefix) encoded as Base64
 * - Optional associated data (AAD) support
 *
 * Key handling:
 * - Callers provide key material as Base64 (RAW 32-byte key).
 * - Generate once via [generateKeyBase64] and persist using platform-secure storage
 *   (Android Keystore/Encrypted prefs, iOS Keychain, etc).
 */
object Encryptor {
    private const val ENVELOPE_PREFIX = "cw1:"

    private val provider by lazy { CryptographyProvider.Default }
    private val aesGcm by lazy { provider.get(AES.GCM) }

    init {
        CryptographyInit.ensureInstalled()
    }

    @OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
    fun generateKeyBase64(): String {
        val key = aesGcm.keyGenerator(SymmetricKeySize.B256).generateKeyBlocking()
        val raw = key.encodeToBlocking(AES.Key.Format.RAW)
        return Base64.encode(raw)
    }

    @OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
    fun encrypt(plaintext: String, keyBase64: String, associatedData: String? = null): String {
        val key = decodeRawKey(keyBase64)
        val cipher = key.cipher()

        val ciphertext = cipher.encryptBlocking(
            plaintext.encodeToByteArray(),
            associatedData?.encodeToByteArray(),
        )

        return ENVELOPE_PREFIX + Base64.encode(ciphertext)
    }

    @OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
    fun decrypt(envelope: String, keyBase64: String, associatedData: String? = null): String {
        if (!envelope.startsWith(ENVELOPE_PREFIX)) {
            throw IllegalArgumentException("Unsupported ciphertext envelope version")
        }

        val ciphertext = try {
            Base64.decode(envelope.removePrefix(ENVELOPE_PREFIX))
        } catch (t: Throwable) {
            throw IllegalArgumentException("Ciphertext envelope is not valid Base64", t)
        }

        val key = decodeRawKey(keyBase64)
        val cipher = key.cipher()

        val plaintext = try {
            cipher.decryptBlocking(ciphertext, associatedData?.encodeToByteArray())
        } catch (t: Throwable) {
            throw IllegalArgumentException("Decryption failed (wrong key, AAD, or tampered data)", t)
        }

        return plaintext.decodeToString()
    }

    @OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
    private fun decodeRawKey(keyBase64: String): AES.GCM.Key {
        val raw = try {
            Base64.decode(keyBase64)
        } catch (t: Throwable) {
            throw IllegalArgumentException("Key must be Base64-encoded RAW AES key bytes", t)
        }

        if (raw.size != 32) {
            throw IllegalArgumentException("Key must be 32 bytes (AES-256), got ${raw.size}")
        }

        return aesGcm.keyDecoder().decodeFromBlocking(AES.Key.Format.RAW, raw)
    }
}
