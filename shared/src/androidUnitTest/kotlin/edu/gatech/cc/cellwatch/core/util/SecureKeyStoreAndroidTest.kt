package edu.gatech.cc.cellwatch.core.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureKeyStoreAndroidTest {
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecureKeyStore.initialize(context)
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
