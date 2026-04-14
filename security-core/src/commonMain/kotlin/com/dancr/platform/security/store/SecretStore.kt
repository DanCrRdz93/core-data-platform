package com.dancr.platform.security.store

// Platform-secure storage abstraction.
// Implementations: AndroidSecretStore (EncryptedSharedPreferences + Keystore),
//                  IosSecretStore (Keychain Services).
//
// Design note: A typed overload (suspend fun <T> put(key, value, serializer)) was considered
// but deferred. It would introduce a kotlinx-serialization dependency on security-core,
// which currently has none. If structured data storage becomes needed, evaluate adding it
// via an extension module or extension function rather than expanding this core contract.
interface SecretStore {

    suspend fun putString(key: String, value: String)

    suspend fun getString(key: String): String?

    suspend fun putBytes(key: String, value: ByteArray)

    suspend fun getBytes(key: String): ByteArray?

    suspend fun remove(key: String)

    suspend fun clear()

    suspend fun contains(key: String): Boolean

    // Returns all keys currently stored.
    // Intended for migration scripts and diagnostics tooling — not for regular product logic.
    // Performance characteristics vary by platform (Keychain enumeration can be expensive on iOS).
    suspend fun keys(): Set<String>

    // Stores value only if the key does not already exist. Returns true if the value was stored.
    // Useful for one-time initialization (e.g. generating a device ID on first launch).
    suspend fun putStringIfAbsent(key: String, value: String): Boolean
}
