package com.dancr.platform.security.store

import com.dancr.platform.security.error.Diagnostic
import com.dancr.platform.security.error.SecurityError
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.Security.*

class IosSecretStore(
    private val config: KeychainConfig = KeychainConfig()
) : SecretStore {

    override suspend fun putString(key: String, value: String): Unit = runSecure {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw SecurityError.SecureStorageFailure(
                diagnostic = Diagnostic(description = "Failed to encode string to NSData")
            )
        putData(key, data)
    }

    override suspend fun getString(key: String): String? = runSecure {
        val data = getData(key) ?: return@runSecure null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    override suspend fun putBytes(key: String, value: ByteArray): Unit = runSecure {
        val data = value.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = value.size.toULong())
        }
        putData(key, data)
    }

    override suspend fun getBytes(key: String): ByteArray? = runSecure {
        val data = getData(key) ?: return@runSecure null
        data.toByteArray()
    }

    override suspend fun remove(key: String): Unit = runSecure {
        val query = baseQuery(key)
        val status = SecItemDelete(query)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw mapOSStatus(status.toInt(), "delete")
        }
    }

    override suspend fun clear(): Unit = runSecure {
        val query = NSMutableDictionary().apply {
            setValue(kSecClassGenericPassword, forKey = kSecClass as String)
            setValue(config.serviceName, forKey = kSecAttrService as String)
        }
        val status = SecItemDelete(query)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw mapOSStatus(status.toInt(), "clear")
        }
    }

    override suspend fun contains(key: String): Boolean = runSecure {
        val query = baseQuery(key).apply {
            setValue(kSecMatchLimitOne, forKey = kSecMatchLimit as String)
        }
        val status = SecItemCopyMatching(query, null)
        when (status) {
            errSecSuccess -> true
            errSecItemNotFound -> false
            else -> throw mapOSStatus(status.toInt(), "contains")
        }
    }

    // -- Internal helpers --

    private fun putData(key: String, data: NSData) {
        val query = baseQuery(key).apply {
            setValue(data, forKey = kSecValueData as String)
        }
        var status = SecItemAdd(query, null)
        if (status == errSecDuplicateItem) {
            val updateAttrs = NSMutableDictionary().apply {
                setValue(data, forKey = kSecValueData as String)
            }
            status = SecItemUpdate(baseQuery(key), updateAttrs)
        }
        if (status != errSecSuccess) {
            throw mapOSStatus(status.toInt(), "put")
        }
    }

    private fun getData(key: String): NSData? {
        val query = baseQuery(key).apply {
            setValue(kCFBooleanTrue, forKey = kSecReturnData as String)
            setValue(kSecMatchLimitOne, forKey = kSecMatchLimit as String)
        }
        memScoped {
            val result = alloc<ObjCObjectVar<*>>()
            val status = SecItemCopyMatching(query, result.ptr)
            return when (status) {
                errSecSuccess -> result.value as? NSData
                errSecItemNotFound -> null
                else -> throw mapOSStatus(status.toInt(), "get")
            }
        }
    }

    private fun baseQuery(key: String): NSMutableDictionary {
        val query = NSMutableDictionary()
        query.setValue(kSecClassGenericPassword, forKey = kSecClass as String)
        query.setValue(config.serviceName, forKey = kSecAttrService as String)
        query.setValue(key, forKey = kSecAttrAccount as String)
        config.accessGroup?.let {
            query.setValue(it, forKey = kSecAttrAccessGroup as String)
        }
        query.setValue(
            config.accessibility.toSecAttr(),
            forKey = kSecAttrAccessible as String
        )
        return query
    }

    private fun KeychainAccessibility.toSecAttr(): Any? = when (this) {
        KeychainAccessibility.WHEN_UNLOCKED -> kSecAttrAccessibleWhenUnlocked
        KeychainAccessibility.AFTER_FIRST_UNLOCK -> kSecAttrAccessibleAfterFirstUnlock
        KeychainAccessibility.WHEN_PASSCODE_SET_THIS_DEVICE_ONLY -> kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
        KeychainAccessibility.WHEN_UNLOCKED_THIS_DEVICE_ONLY -> kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        KeychainAccessibility.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY -> kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        return ByteArray(length).apply {
            usePinned { pinned ->
                this@toByteArray.getBytes(pinned.addressOf(0), length.toULong())
            }
        }
    }

    private suspend fun <T> runSecure(block: () -> T): T =
        withContext(Dispatchers.IO) { block() }

    // -- Error mapping --

    private fun mapOSStatus(status: Int, operation: String): SecurityError.SecureStorageFailure =
        SecurityError.SecureStorageFailure(
            diagnostic = Diagnostic(
                description = "Keychain $operation failed with OSStatus $status",
                metadata = mapOf(
                    "store" to "ios_keychain",
                    "service" to config.serviceName,
                    "osStatus" to status.toString()
                )
            )
        )
}
