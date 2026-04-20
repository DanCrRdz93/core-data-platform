package com.dancr.platform.security.store

/**
 * Platform-secure storage abstraction for persisting sensitive data.
 *
 * Implementations:
 * - **Android**: `AndroidSecretStore` (DataStore + AES-GCM via Android Keystore)
 * - **iOS**: `IosSecretStore` (Keychain Services)
 *
 * All operations are `suspend` because platform-secure I/O may be asynchronous.
 * Failures are reported via [SecureStorageException].
 *
 * **Example — storing and retrieving a token:**
 * ```kotlin
 * val store: SecretStore = AndroidSecretStore(context)
 *
 * // Write
 * store.putString("access_token", "eyJhbGci...")
 *
 * // Read
 * val token: String? = store.getString("access_token")
 *
 * // Remove
 * store.remove("access_token")
 *
 * // Check existence
 * if (store.contains("device_id")) { /* … */ }
 *
 * // One-time initialization
 * store.putStringIfAbsent("device_id", UUID.randomUUID().toString())
 * ```
 *
 * @see SecureStorageException for the throwable wrapper used by implementations.
 */
interface SecretStore {

    /** Persists a string [value] under [key], overwriting any existing entry. */
    suspend fun putString(key: String, value: String)

    /** Returns the string stored under [key], or `null` if not found. */
    suspend fun getString(key: String): String?

    /** Persists a byte array [value] under [key], overwriting any existing entry. */
    suspend fun putBytes(key: String, value: ByteArray)

    /** Returns the byte array stored under [key], or `null` if not found. */
    suspend fun getBytes(key: String): ByteArray?

    /** Removes the entry for [key]. No-op if the key does not exist. */
    suspend fun remove(key: String)

    /** Removes all entries from the store. */
    suspend fun clear()

    /** Returns `true` if an entry exists for [key]. */
    suspend fun contains(key: String): Boolean

    /**
     * Returns all keys currently stored.
     *
     * Intended for migration scripts and diagnostics tooling — not for regular product logic.
     * Performance characteristics vary by platform (Keychain enumeration can be expensive on iOS).
     */
    suspend fun keys(): Set<String>

    /**
     * Stores [value] only if [key] does not already exist.
     *
     * Useful for one-time initialization (e.g. generating a device ID on first launch).
     *
     * @return `true` if the value was stored, `false` if the key already existed.
     */
    suspend fun putStringIfAbsent(key: String, value: String): Boolean
}
