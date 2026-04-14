package com.dancr.platform.security.store

// Platform-secure storage abstraction.
// Implementations: AndroidSecretStore (EncryptedSharedPreferences + Keystore),
//                  IosSecretStore (Keychain Services).
// TODO: Add suspend fun keys(): Set<String> for migration/diagnostics tooling.
// TODO: Add suspend fun putStringIfAbsent(key, value): Boolean for atomic write-if-missing.
// TODO: Consider adding a typed overload: suspend fun <T> put(key, value, serializer) for structured data.
interface SecretStore {

    suspend fun putString(key: String, value: String)

    suspend fun getString(key: String): String?

    suspend fun putBytes(key: String, value: ByteArray)

    suspend fun getBytes(key: String): ByteArray?

    suspend fun remove(key: String)

    suspend fun clear()

    suspend fun contains(key: String): Boolean
}
