package com.dancr.platform.security.store

data class AndroidStoreConfig(
    val preferencesName: String = "dancr_secure_prefs",
    val masterKeyAlias: String = "_dancr_master_key_",
    val keyPrefix: String = "dancr_",
    val useStrongBox: Boolean = false
)
