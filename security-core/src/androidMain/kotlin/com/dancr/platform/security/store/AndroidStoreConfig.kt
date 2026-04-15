package com.dancr.platform.security.store

data class AndroidStoreConfig(
    val dataStoreName: String = "dancr_secure_store",
    val keyAlias: String = "_dancr_crypto_key_",
    val keyPrefix: String = "dancr_"
)
