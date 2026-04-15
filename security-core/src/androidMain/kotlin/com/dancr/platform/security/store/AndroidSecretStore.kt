package com.dancr.platform.security.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.dancr.platform.security.error.Diagnostic
import com.dancr.platform.security.error.SecureStorageException
import com.dancr.platform.security.error.SecurityError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AndroidSecretStore(
    private val context: Context,
    private val config: AndroidStoreConfig = AndroidStoreConfig()
) : SecretStore {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = config.dataStoreName
    )

    private val crypto: CryptoManager by lazy {
        CryptoManager(keyAlias = config.keyAlias)
    }

    override suspend fun putString(key: String, value: String) {
        try {
            val encrypted = crypto.encryptString(value)
            context.dataStore.edit { prefs ->
                prefs[byteKey(key)] = encrypted
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun getString(key: String): String? {
        try {
            val encrypted = context.dataStore.data
                .map { prefs -> prefs[byteKey(key)] }
                .first()
                ?: return null
            return crypto.decryptString(encrypted)
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun putBytes(key: String, value: ByteArray) {
        try {
            val encrypted = crypto.encrypt(value)
            context.dataStore.edit { prefs ->
                prefs[byteKey(key)] = encrypted
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun getBytes(key: String): ByteArray? {
        try {
            val encrypted = context.dataStore.data
                .map { prefs -> prefs[byteKey(key)] }
                .first()
                ?: return null
            return crypto.decrypt(encrypted)
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun remove(key: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(byteKey(key))
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun clear() {
        try {
            context.dataStore.edit { prefs ->
                prefs.clear()
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun contains(key: String): Boolean {
        try {
            return context.dataStore.data
                .map { prefs -> byteKey(key) in prefs }
                .first()
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun keys(): Set<String> {
        try {
            val prefix = config.keyPrefix
            return context.dataStore.data
                .map { prefs ->
                    prefs.asMap().keys
                        .map { it.name }
                        .filter { it.startsWith(prefix) }
                        .map { it.removePrefix(prefix) }
                        .toSet()
                }
                .first()
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun putStringIfAbsent(key: String, value: String): Boolean {
        try {
            var stored = false
            context.dataStore.edit { prefs ->
                if (byteKey(key) !in prefs) {
                    prefs[byteKey(key)] = crypto.encryptString(value)
                    stored = true
                }
            }
            return stored
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    // -- Internal helpers --

    private fun byteKey(key: String): Preferences.Key<ByteArray> =
        byteArrayPreferencesKey("${config.keyPrefix}$key")

    // -- Error mapping --

    private fun mapException(e: Exception): SecureStorageException =
        SecureStorageException(
            SecurityError.SecureStorageFailure(
                diagnostic = Diagnostic(
                    description = e.message ?: "Android secure storage operation failed",
                    cause = e,
                    metadata = mapOf(
                        "store" to "android_keystore_datastore",
                        "dataStoreName" to config.dataStoreName
                    )
                )
            )
        )
}
