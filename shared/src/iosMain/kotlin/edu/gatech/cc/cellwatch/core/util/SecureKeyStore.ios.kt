package edu.gatech.cc.cellwatch.core.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
actual object SecureKeyStore {
    private const val SERVICE = "edu.gatech.cc.cellwatch.securekeys"
    private const val ERR_SEC_NOT_AVAILABLE = -25291
    private val unavailableFallbackKeys = mutableMapOf<String, String>()

    actual fun initialize(platformContext: Any?) {
        // iOS implementation does not require explicit initialization.
    }

    actual fun getOrCreateKeyBase64(keyId: String): String {
        val existing = readKey(keyId)
        if (existing != null) return existing

        val generated = Encryptor.generateKeyBase64()
        writeKey(keyId, generated)
        return generated
    }

    actual fun deleteKey(keyId: String) {
        val query = createQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(SERVICE),
            kSecAttrAccount to cfString(keyId),
        )

        val status = SecItemDelete(query)
        if (status == ERR_SEC_NOT_AVAILABLE) {
            unavailableFallbackKeys.remove(keyId)
            return
        }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw IllegalStateException("Keychain delete failed for '$keyId' with status=$status")
        }
    }

    private fun readKey(keyId: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val query = createQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(SERVICE),
            kSecAttrAccount to cfString(keyId),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )

        val status = SecItemCopyMatching(query, result.ptr)
        if (status == ERR_SEC_NOT_AVAILABLE) {
            return unavailableFallbackKeys[keyId]
        }
        if (status == errSecItemNotFound) return null
        if (status != errSecSuccess) {
            throw IllegalStateException("Keychain read failed for '$keyId' with status=$status")
        }

        val data = result.value as? CFDataRef
            ?: throw IllegalStateException("Keychain returned unexpected value for '$keyId'")

        val bytes = CFDataGetBytePtr(data)
            ?.readBytes(CFDataGetLength(data).toInt())
            ?: throw IllegalStateException("Keychain returned empty bytes for '$keyId'")
        bytes.decodeToString()
    }

    private fun writeKey(keyId: String, value: String) {
        val data = value.encodeToByteArray().toCFData()

        // Replace existing value by delete + add.
        deleteKey(keyId)

        val attrs = createQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(SERVICE),
            kSecAttrAccount to cfString(keyId),
            kSecValueData to data,
        )

        val status = SecItemAdd(attrs, null)
        if (status == ERR_SEC_NOT_AVAILABLE) {
            unavailableFallbackKeys[keyId] = value
            return
        }
        if (status != errSecSuccess) {
            throw IllegalStateException("Keychain write failed for '$keyId' with status=$status")
        }
    }

    private fun createQuery(vararg entries: Pair<CFTypeRef?, CFTypeRef?>): CFDictionaryRef {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, entries.size.toLong(), null, null)
            ?: throw IllegalStateException("Failed to allocate CFDictionary for keychain query")

        entries.forEach { (key, value) ->
            CFDictionarySetValue(query, key, value)
        }

        return query
    }

    private fun cfString(value: String): CFStringRef {
        return CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)
            ?: throw IllegalStateException("Failed to create CFString")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCFData(): CFDataRef {
    return usePinned { pinned ->
        CFDataCreate(
            allocator = kCFAllocatorDefault,
            bytes = pinned.addressOf(0).reinterpret(),
            length = size.toLong(),
        )
    } ?: throw IllegalStateException("Failed to allocate CFData for key material")
}
