package com.dancr.platform.security.store

import com.dancr.platform.security.error.Diagnostic
import com.dancr.platform.security.error.SecureStorageException
import com.dancr.platform.security.error.SecurityError
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.*
import platform.Security.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecretStore(
    private val config: KeychainConfig = KeychainConfig()
) : SecretStore {

    override suspend fun putString(key: String, value: String): Unit = runSecure {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw storageException("Failed to encode string to NSData")
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
        baseQuery(key).useCF { cfQuery ->
            val status = SecItemDelete(cfQuery)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw mapOSStatus(status.toInt(), "delete")
            }
        }
    }

    override suspend fun clear(): Unit = runSecure {
        val query = NSMutableDictionary().apply {
            setValue(kSecClassGenericPassword, forKey = kSecClass as String)
            setValue(config.serviceName, forKey = kSecAttrService as String)
        }
        query.useCF { cfQuery ->
            val status = SecItemDelete(cfQuery)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw mapOSStatus(status.toInt(), "clear")
            }
        }
    }

    override suspend fun contains(key: String): Boolean = runSecure {
        val query = baseQuery(key).apply {
            setValue(kSecMatchLimitOne, forKey = kSecMatchLimit as String)
        }
        query.useCF { cfQuery ->
            val status = SecItemCopyMatching(cfQuery, null)
            when (status) {
                errSecSuccess -> true
                errSecItemNotFound -> false
                else -> throw mapOSStatus(status.toInt(), "contains")
            }
        }
    }

    override suspend fun keys(): Set<String> = runSecure {
        val query = NSMutableDictionary().apply {
            setValue(kSecClassGenericPassword, forKey = kSecClass as String)
            setValue(config.serviceName, forKey = kSecAttrService as String)
            setValue(true, forKey = kSecReturnAttributes as String)
            setValue(kSecMatchLimitAll, forKey = kSecMatchLimit as String)
        }
        query.useCF { cfQuery ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(cfQuery, result.ptr)
                when (status) {
                    errSecSuccess -> {
                        val nsResult = CFBridgingRelease(result.value)
                        @Suppress("UNCHECKED_CAST")
                        val items = nsResult as? List<Map<String, Any?>> ?: return@useCF emptySet()
                        items.mapNotNull { it[kSecAttrAccount as String] as? String }.toSet()
                    }
                    errSecItemNotFound -> emptySet()
                    else -> throw mapOSStatus(status.toInt(), "keys")
                }
            }
        }
    }

    override suspend fun putStringIfAbsent(key: String, value: String): Boolean = runSecure {
        if (getData(key) != null) {
            false
        } else {
            val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                ?: throw storageException("Failed to encode string to NSData")
            putData(key, data)
            true
        }
    }

    // -- Internal helpers --

    private fun putData(key: String, data: NSData) {
        val query = baseQuery(key).apply {
            setValue(data, forKey = kSecValueData as String)
        }
        query.useCF { cfQuery ->
            var status = SecItemAdd(cfQuery, null)
            if (status == errSecDuplicateItem) {
                baseQuery(key).useCF { cfBase ->
                    val updateAttrs = NSMutableDictionary().apply {
                        setValue(data, forKey = kSecValueData as String)
                    }
                    updateAttrs.useCF { cfUpdate ->
                        status = SecItemUpdate(cfBase, cfUpdate)
                    }
                }
            }
            if (status != errSecSuccess) {
                throw mapOSStatus(status.toInt(), "put")
            }
        }
    }

    private fun getData(key: String): NSData? {
        val query = baseQuery(key).apply {
            setValue(true, forKey = kSecReturnData as String)
            setValue(kSecMatchLimitOne, forKey = kSecMatchLimit as String)
        }
        return query.useCF { cfQuery ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(cfQuery, result.ptr)
                when (status) {
                    errSecSuccess -> CFBridgingRelease(result.value) as? NSData
                    errSecItemNotFound -> null
                    else -> throw mapOSStatus(status.toInt(), "get")
                }
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
                platform.posix.memcpy(pinned.addressOf(0), this@toByteArray.bytes, length.toULong())
            }
        }
    }

    // Toll-free bridge: NSMutableDictionary → CFDictionaryRef.
    // CFBridgingRetain adds +1 retain count; the caller MUST CFRelease.
    private inline fun <T> NSDictionary.useCF(block: (CFDictionaryRef) -> T): T {
        val cfRef: CFDictionaryRef = CFBridgingRetain(this)?.reinterpret()
            ?: error("CFBridgingRetain returned null")
        return try {
            block(cfRef)
        } finally {
            CFRelease(cfRef)
        }
    }

    private suspend fun <T> runSecure(block: () -> T): T =
        withContext(Dispatchers.IO) { block() }

    // -- Error mapping --

    private fun storageException(description: String): SecureStorageException =
        SecureStorageException(
            SecurityError.SecureStorageFailure(
                diagnostic = Diagnostic(description = description)
            )
        )

    private fun mapOSStatus(status: Int, operation: String): SecureStorageException =
        SecureStorageException(SecurityError.SecureStorageFailure(
            diagnostic = Diagnostic(
                description = "Keychain $operation failed with OSStatus $status",
                metadata = mapOf(
                    "store" to "ios_keychain",
                    "service" to config.serviceName,
                    "osStatus" to status.toString()
                )
            )
        ))
}
