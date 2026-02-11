package edu.gatech.cc.cellwatch.core.util

import kotlin.test.BeforeTest
import kotlin.test.Test

class SecureKeyStoreIosTest {
    @BeforeTest
    fun setup() {
        SecureKeyStore.setFallbackAllowedForTests(true)
        SecureKeyStore.initialize()
    }

    @Test
    fun getOrCreate_returns_stable_key() {
        SecureKeyStoreContract.assertGetOrCreateReturnsStableKey(
            setup = {},
            getOrCreate = { SecureKeyStore.getOrCreateKeyBase64(it) },
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }

    @Test
    fun delete_rotates_key() {
        SecureKeyStoreContract.assertDeleteRotatesKey(
            setup = {},
            getOrCreate = { SecureKeyStore.getOrCreateKeyBase64(it) },
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }

    @Test
    fun secure_encryptor_round_trip() {
        SecureEncryptorContract.assertRoundTrip(
            setup = {},
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }

    @Test
    fun decrypt_fails_after_key_rotation() {
        SecureEncryptorContract.assertDecryptFailsAfterRotation(
            setup = {},
            getOrCreate = { SecureKeyStore.getOrCreateKeyBase64(it) },
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }
}
