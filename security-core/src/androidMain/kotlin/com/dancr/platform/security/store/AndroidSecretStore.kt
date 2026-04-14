package com.dancr.platform.security.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dancr.platform.security.error.Diagnostic
import com.dancr.platform.security.error.SecureStorageException
import com.dancr.platform.security.error.SecurityError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64 as AndroidBase64

class AndroidSecretStore(
    private val context: Context,
    private val config: AndroidStoreConfig = AndroidStoreConfig()
) : SecretStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context, config.masterKeyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(config.useStrongBox)
            .build()
        EncryptedSharedPreferences.create(
            context,
            config.preferencesName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun putString(key: String, value: String): Unit = runOnDisk {
        try {
            prefs.edit().putString(prefixedKey(key), value).apply()
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun getString(key: String): String? = runOnDisk {
        try {
            prefs.getString(prefixedKey(key), null)
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun putBytes(key: String, value: ByteArray): Unit = runOnDisk {
        try {
            val encoded = AndroidBase64.encodeToString(value, AndroidBase64.NO_WRAP)
            prefs.edit().putString(prefixedKey(key), encoded).apply()
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun getBytes(key: String): ByteArray? = runOnDisk {
        try {
            prefs.getString(prefixedKey(key), null)
                ?.let { AndroidBase64.decode(it, AndroidBase64.NO_WRAP) }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun remove(key: String): Unit = runOnDisk {
        try {
            prefs.edit().remove(prefixedKey(key)).apply()
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun clear(): Unit = runOnDisk {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun contains(key: String): Boolean = runOnDisk {
        try {
            prefs.contains(prefixedKey(key))
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun keys(): Set<String> = runOnDisk {
        try {
            val prefix = config.keyPrefix
            prefs.all.keys
                .filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix) }
                .toSet()
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    override suspend fun putStringIfAbsent(key: String, value: String): Boolean = runOnDisk {
        try {
            val prefixed = prefixedKey(key)
            if (prefs.contains(prefixed)) {
                false
            } else {
                prefs.edit().putString(prefixed, value).apply()
                true
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }

    // -- Internal helpers --

    private fun prefixedKey(key: String): String = "${config.keyPrefix}$key"

    private suspend fun <T> runOnDisk(block: () -> T): T =
        withContext(Dispatchers.IO) { block() }

    // -- Error mapping --

    private fun mapException(e: Exception): SecureStorageException =
        SecureStorageException(
            SecurityError.SecureStorageFailure(
                diagnostic = Diagnostic(
                    description = e.message ?: "Android secure storage operation failed",
                    cause = e,
                    metadata = mapOf(
                        "store" to "android_keystore",
                        "prefsName" to config.preferencesName
                    )
                )
            )
        )
}
