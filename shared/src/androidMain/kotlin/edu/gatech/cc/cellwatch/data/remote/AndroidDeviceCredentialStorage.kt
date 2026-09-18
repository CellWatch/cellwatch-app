package edu.gatech.cc.cellwatch.data.remote

import android.content.Context
import android.content.SharedPreferences

/**
 * Device credential storage backed by app-private SharedPreferences.
 *
 * The Android counterpart of IosDeviceCredentialStorage, and app-private for
 * the same reason its iOS sibling avoids the Keychain: the credential must not
 * outlive the installation. An id that survives while its secret does not can
 * never re-register, because register_device refuses an id it already knows
 * and the secret is stored server-side only as a bcrypt hash.
 */
class AndroidDeviceCredentialStorage(
    context: Context,
    private val key: String = "cellwatch.device.credential",
) : DeviceCredentialStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("cellwatch.device", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(key, null)

    override fun write(value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun clear() {
        prefs.edit().remove(key).apply()
    }
}
