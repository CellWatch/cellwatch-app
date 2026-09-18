package edu.gatech.cc.cellwatch.data.remote

import platform.Foundation.NSUserDefaults

/**
 * Device credential storage backed by NSUserDefaults.
 *
 * NSUserDefaults, not the Keychain, on purpose: the credential is an anonymous
 * install-scoped identifier, and a reinstall SHOULD produce a new one. Keychain
 * entries survive reinstall, which would resurrect an id whose secret is gone -
 * the exact unrecoverable state this store exists to avoid.
 */
class IosDeviceCredentialStorage(
    private val key: String = "cellwatch.device.credential",
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DeviceCredentialStorage {

    override fun read(): String? = defaults.stringForKey(key)

    override fun write(value: String) {
        defaults.setObject(value, key)
        defaults.synchronize()
    }

    override fun clear() {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }
}
