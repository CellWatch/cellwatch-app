package edu.gatech.cc.cellwatch.core.util

import kotlin.test.Test

class SecureKeyStoreIosTest {
    @Test
    fun getOrCreate_returns_stable_key() {
        SecureKeyStoreContract.assertGetOrCreateReturnsStableKey(
            setup = { SecureKeyStore.initialize() },
            getOrCreate = { SecureKeyStore.getOrCreateKeyBase64(it) },
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }

    @Test
    fun delete_rotates_key() {
        SecureKeyStoreContract.assertDeleteRotatesKey(
            setup = { SecureKeyStore.initialize() },
            getOrCreate = { SecureKeyStore.getOrCreateKeyBase64(it) },
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }

    @Test
    fun secure_encryptor_round_trip() {
        SecureEncryptorContract.assertRoundTrip(
            setup = { SecureKeyStore.initialize() },
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }

    @Test
    fun decrypt_fails_after_key_rotation() {
        SecureEncryptorContract.assertDecryptFailsAfterRotation(
            setup = { SecureKeyStore.initialize() },
            getOrCreate = { SecureKeyStore.getOrCreateKeyBase64(it) },
            delete = { SecureKeyStore.deleteKey(it) },
        )
    }
}
