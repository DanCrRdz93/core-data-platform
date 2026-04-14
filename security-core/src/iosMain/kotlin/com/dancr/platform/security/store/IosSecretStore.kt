package com.dancr.platform.security.store

import com.dancr.platform.security.error.Diagnostic
import com.dancr.platform.security.error.SecurityError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

// Integration: uses platform.Security framework via cinterop (available by default in iosMain).
// Key APIs: SecItemAdd, SecItemCopyMatching, SecItemUpdate, SecItemDelete
// Key constants: kSecClass, kSecAttrService, kSecAttrAccount, kSecValueData, kSecAttrAccessible

class IosSecretStore(
    private val config: KeychainConfig = KeychainConfig()
) : SecretStore {

    override suspend fun putString(key: String, value: String): Unit = runSecure {
        // TODO:
        // 1. Encode value to NSData via toByteArray().toNSData()
        // 2. Build query with baseQuery(key) + kSecValueData
        // 3. SecItemAdd(query, null)
        // 4. If errSecDuplicateItem → build update attrs, SecItemUpdate(baseQuery, attrs)
        // 5. Check OSStatus, throw if error
        TODO("Implement with SecItemAdd / SecItemUpdate")
    }

    override suspend fun getString(key: String): String? = runSecure {
        // TODO:
        // 1. Build query with baseQuery(key) + kSecReturnData + kSecMatchLimitOne
        // 2. SecItemCopyMatching(query, result)
        // 3. If errSecItemNotFound → return null
        // 4. Cast result to NSData, decode to String
        TODO("Implement with SecItemCopyMatching")
    }

    override suspend fun putBytes(key: String, value: ByteArray): Unit = runSecure {
        // TODO: Same as putString but store raw ByteArray as NSData directly
        TODO("Implement with SecItemAdd / SecItemUpdate for raw bytes")
    }

    override suspend fun getBytes(key: String): ByteArray? = runSecure {
        // TODO: Same as getString but return raw NSData as ByteArray
        TODO("Implement with SecItemCopyMatching for raw bytes")
    }

    override suspend fun remove(key: String): Unit = runSecure {
        // TODO:
        // 1. SecItemDelete(baseQuery(key))
        // 2. Ignore errSecItemNotFound (idempotent delete)
        TODO("Implement with SecItemDelete")
    }

    override suspend fun clear(): Unit = runSecure {
        // TODO:
        // 1. Build broad query: kSecClass=kSecClassGenericPassword + kSecAttrService
        // 2. SecItemDelete(broadQuery)
        TODO("Implement with SecItemDelete using service-scoped query")
    }

    override suspend fun contains(key: String): Boolean = runSecure {
        // TODO:
        // 1. Build query with baseQuery(key) + kSecMatchLimitOne (no kSecReturnData)
        // 2. SecItemCopyMatching(query, null)
        // 3. Return status == errSecSuccess
        TODO("Implement with SecItemCopyMatching without returning data")
    }

    // -- Internal helpers --

    // TODO: Build the base NSMutableDictionary for a given key:
    //
    // private fun baseQuery(key: String): NSMutableDictionary {
    //     val query = NSMutableDictionary()
    //     query[kSecClass] = kSecClassGenericPassword
    //     query[kSecAttrService] = config.serviceName
    //     query[kSecAttrAccount] = key
    //     config.accessGroup?.let { query[kSecAttrAccessGroup] = it }
    //     query[kSecAttrAccessible] = config.accessibility.toSecAttr()
    //     return query
    // }

    // TODO: Map KeychainAccessibility enum to kSecAttrAccessible constants:
    //
    // private fun KeychainAccessibility.toSecAttr(): CFStringRef = when (this) {
    //     WHEN_UNLOCKED                       -> kSecAttrAccessibleWhenUnlocked
    //     AFTER_FIRST_UNLOCK                  -> kSecAttrAccessibleAfterFirstUnlock
    //     WHEN_PASSCODE_SET_THIS_DEVICE_ONLY  -> kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
    //     WHEN_UNLOCKED_THIS_DEVICE_ONLY      -> kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    //     AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY -> kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
    // }

    private suspend fun <T> runSecure(block: () -> T): T =
        withContext(Dispatchers.IO) { block() }

    // -- Error mapping --

    @Suppress("unused")
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
