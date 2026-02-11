package edu.gatech.cc.cellwatch.core.util

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

internal object SecureKeyStoreContract {
    fun assertGetOrCreateReturnsStableKey(
        setup: () -> Unit = {},
        getOrCreate: (String) -> String,
        delete: (String) -> Unit,
    ) {
        setup()
        val keyId = uniqueKeyId("stable")
        delete(keyId)

        val key1 = getOrCreate(keyId)
        val key2 = getOrCreate(keyId)

        assertEquals(key1, key2)
        delete(keyId)
    }

    fun assertDeleteRotatesKey(
        setup: () -> Unit = {},
        getOrCreate: (String) -> String,
        delete: (String) -> Unit,
    ) {
        setup()
        val keyId = uniqueKeyId("rotate")
        delete(keyId)

        val first = getOrCreate(keyId)
        delete(keyId)
        val second = getOrCreate(keyId)

        assertNotEquals(first, second)
        delete(keyId)
    }
}

internal object SecureEncryptorContract {
    fun assertRoundTrip(
        setup: () -> Unit = {},
        delete: (String) -> Unit,
    ) {
        setup()
        val keyId = uniqueKeyId("roundtrip")
        delete(keyId)

        val plaintext = "cellwatch-secure-secret"
        val envelope = SecureEncryptor.encrypt(plaintext, keyId = keyId, associatedData = "aad")
        val decrypted = SecureEncryptor.decrypt(envelope, keyId = keyId, associatedData = "aad")

        assertEquals(plaintext, decrypted)
        delete(keyId)
    }

    fun assertDecryptFailsAfterRotation(
        setup: () -> Unit = {},
        getOrCreate: (String) -> String,
        delete: (String) -> Unit,
    ) {
        setup()
        val keyId = uniqueKeyId("rotation-fail")
        delete(keyId)

        val plaintext = "rotation-sensitive-secret"
        val envelope = SecureEncryptor.encrypt(plaintext, keyId = keyId)

        delete(keyId)
        val rotated = getOrCreate(keyId)
        check(rotated.isNotBlank())

        assertFailsWith<IllegalArgumentException> {
            SecureEncryptor.decrypt(envelope, keyId = keyId)
        }
        delete(keyId)
    }
}

private fun uniqueKeyId(prefix: String): String = "$prefix-${Random.nextInt(1_000_000_000)}"
