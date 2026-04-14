package com.dancr.platform.security.store

import android.content.Context
import com.dancr.platform.security.error.Diagnostic
import com.dancr.platform.security.error.SecurityError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Integration dependency (add when implementing):
//   androidMain.dependencies {
//       implementation("androidx.security:security-crypto:1.1.0-alpha06")
//   }

class AndroidSecretStore(
    private val context: Context,
    private val config: AndroidStoreConfig = AndroidStoreConfig()
) : SecretStore {

    // TODO: Initialize EncryptedSharedPreferences lazily:
    //
    // private val prefs: SharedPreferences by lazy {
    //     val masterKey = MasterKey.Builder(context, config.masterKeyAlias)
    //         .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    //         .setRequestStrongBoxBacked(config.useStrongBox)
    //         .build()
    //     EncryptedSharedPreferences.create(
    //         context,
    //         config.preferencesName,
    //         masterKey,
    //         EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    //         EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    //     )
    // }

    override suspend fun putString(key: String, value: String): Unit = runOnDisk {
        // TODO: prefs.edit().putString(prefixedKey(key), value).apply()
        TODO("Store string via EncryptedSharedPreferences")
    }

    override suspend fun getString(key: String): String? = runOnDisk {
        // TODO: prefs.getString(prefixedKey(key), null)
        TODO("Retrieve string via EncryptedSharedPreferences")
    }

    override suspend fun putBytes(key: String, value: ByteArray): Unit = runOnDisk {
        // TODO: Encode to Base64 and store via prefs,
        //       or use KeyStore.getInstance("AndroidKeyStore") directly for raw key material
        TODO("Store binary secret")
    }

    override suspend fun getBytes(key: String): ByteArray? = runOnDisk {
        // TODO: Retrieve Base64-encoded string and decode,
        //       or read from AndroidKeyStore directly
        TODO("Retrieve binary secret")
    }

    override suspend fun remove(key: String): Unit = runOnDisk {
        // TODO: prefs.edit().remove(prefixedKey(key)).apply()
        TODO("Remove entry")
    }

    override suspend fun clear(): Unit = runOnDisk {
        // TODO: prefs.edit().clear().apply()
        TODO("Clear all entries")
    }

    override suspend fun contains(key: String): Boolean = runOnDisk {
        // TODO: prefs.contains(prefixedKey(key))
        TODO("Check key existence")
    }

    // -- Internal helpers --

    private fun prefixedKey(key: String): String = "${config.keyPrefix}$key"

    private suspend fun <T> runOnDisk(block: () -> T): T =
        withContext(Dispatchers.IO) { block() }

    // -- Error mapping --

    @Suppress("unused")
    private fun mapException(e: Exception): SecurityError.SecureStorageFailure =
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
}
