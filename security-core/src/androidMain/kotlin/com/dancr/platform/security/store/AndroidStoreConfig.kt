package com.dancr.platform.security.store

/**
 * Configuration for [AndroidSecretStore].
 *
 * **Example:**
 * ```kotlin
 * val config = AndroidStoreConfig(
 *     dataStoreName = "my_secure_prefs",
 *     keyAlias = "my_app_crypto_key",
 *     keyPrefix = "myapp_"
 * )
 * ```
 *
 * @property dataStoreName Jetpack DataStore file name.
 * @property keyAlias      Android Keystore alias for the AES encryption key.
 * @property keyPrefix     Prefix prepended to all keys stored in DataStore.
 */
data class AndroidStoreConfig(
    val dataStoreName: String = "dancr_secure_store",
    val keyAlias: String = "_dancr_crypto_key_",
    val keyPrefix: String = "dancr_"
)
