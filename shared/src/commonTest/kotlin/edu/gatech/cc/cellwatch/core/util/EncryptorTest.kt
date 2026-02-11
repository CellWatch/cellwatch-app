package edu.gatech.cc.cellwatch.core.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class EncryptorTest {

    @Test
    fun encrypt_then_decrypt_returns_original_plaintext() {
        val key = Encryptor.generateKeyBase64()
        val plaintext = "hello cellwatch"

        val encrypted = Encryptor.encrypt(plaintext, key)
        val decrypted = Encryptor.decrypt(encrypted, key)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encrypt_with_same_key_produces_different_ciphertext_due_to_random_iv() {
        val key = Encryptor.generateKeyBase64()
        val plaintext = "same plaintext"

        val encrypted1 = Encryptor.encrypt(plaintext, key)
        val encrypted2 = Encryptor.encrypt(plaintext, key)

        assertNotEquals(encrypted1, encrypted2)
        assertEquals(plaintext, Encryptor.decrypt(encrypted1, key))
        assertEquals(plaintext, Encryptor.decrypt(encrypted2, key))
    }

    @Test
    fun decrypt_fails_with_wrong_key() {
        val key = Encryptor.generateKeyBase64()
        val wrongKey = Encryptor.generateKeyBase64()
        val encrypted = Encryptor.encrypt("secret", key)

        assertFailsWith<IllegalArgumentException> {
            Encryptor.decrypt(encrypted, wrongKey)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun decrypt_fails_for_tampered_ciphertext() {
        val key = Encryptor.generateKeyBase64()
        val encrypted = Encryptor.encrypt("secret", key)

        val prefix = "cw1:"
        assertTrue(encrypted.startsWith(prefix))
        val body = Base64.decode(encrypted.removePrefix(prefix))
        body[body.lastIndex] = (body.last().toInt() xor 0x01).toByte()
        val tampered = prefix + Base64.encode(body)

        assertFailsWith<IllegalArgumentException> {
            Encryptor.decrypt(tampered, key)
        }
    }

    @Test
    fun decrypt_fails_for_invalid_envelope_version() {
        val key = Encryptor.generateKeyBase64()
        assertFailsWith<IllegalArgumentException> {
            Encryptor.decrypt("legacy:abc", key)
        }
    }
}
