package edu.gatech.cc.cellwatch.core.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureKeyStoreAndroidInstrumentedTest {
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecureKeyStore.initialize(context)
    }

    @Test
    fun getOrCreate_returns_stable_key() {
        val keyId = "instrumented-stable"
        SecureKeyStore.deleteKey(keyId)

        val first = SecureKeyStore.getOrCreateKeyBase64(keyId)
        val second = SecureKeyStore.getOrCreateKeyBase64(keyId)

        assertEquals(first, second)
    }

    @Test
    fun delete_rotates_key() {
        val keyId = "instrumented-rotate"
        SecureKeyStore.deleteKey(keyId)

        val first = SecureKeyStore.getOrCreateKeyBase64(keyId)
        SecureKeyStore.deleteKey(keyId)
        val second = SecureKeyStore.getOrCreateKeyBase64(keyId)

        assertNotEquals(first, second)
    }

    @Test
    fun secure_encryptor_round_trip() {
        val keyId = "instrumented-roundtrip"
        SecureKeyStore.deleteKey(keyId)

        val plaintext = "instrumented-secret"
        val encrypted = SecureEncryptor.encrypt(plaintext, keyId = keyId, associatedData = "aad")
        val decrypted = SecureEncryptor.decrypt(encrypted, keyId = keyId, associatedData = "aad")

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_fails_after_key_rotation() {
        val keyId = "instrumented-rotation-fail"
        SecureKeyStore.deleteKey(keyId)

        val encrypted = SecureEncryptor.encrypt("sensitive", keyId = keyId)
        SecureKeyStore.deleteKey(keyId)
        SecureKeyStore.getOrCreateKeyBase64(keyId)

        assertFailsWith<IllegalArgumentException> {
            SecureEncryptor.decrypt(encrypted, keyId = keyId)
        }
    }
}
