package edu.gatech.cc.cellwatch.core.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Encryptor {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALGO = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val KEY_SIZE = 256
    private const val TAG_LENGTH = 128
    private const val ALIAS = "cellwatch-device-key"

    private val store by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }
    private val generator by lazy { KeyGenerator.getInstance(ALGO, PROVIDER) }
    private val cipher by lazy { Cipher.getInstance("$ALGO/$BLOCK_MODE/$PADDING") }
    private val charset by lazy { charset("UTF-8") }

    init {
        if (!store.containsAlias(ALIAS)) makeKey()
    }

    private fun makeKey(): SecretKey {
        return generator.apply {
            init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setKeySize(KEY_SIZE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private val key: SecretKey
        get() = store.getEntry(ALIAS, null).let {
            if (it !is KeyStore.SecretKeyEntry) {
                throw RuntimeException("expected SecretKeyEntry, got $it")
            }
            it.secretKey
        }

    fun encrypt(data: String): String {
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val dataBytes = data.toByteArray(charset)
        val encryptedBytes = cipher.doFinal(dataBytes)
        val allBytes = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encryptedBytes
        return Base64.encodeToString(allBytes, Base64.NO_WRAP)
    }

    fun decrypt(data: String): String {
        val allBytes = Base64.decode(data, Base64.NO_WRAP)
        val ivSize = allBytes[0].toInt()
        val iv = allBytes.copyOfRange(1, ivSize + 1)
        val dataBytes = allBytes.copyOfRange(ivSize + 1, allBytes.size)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        return cipher.doFinal(dataBytes).toString(charset)
    }
}