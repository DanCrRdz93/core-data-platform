package com.dancr.platform.security.store

data class KeychainConfig(
    val serviceName: String = "com.dancr.platform.security",
    val accessGroup: String? = null,
    val accessibility: KeychainAccessibility = KeychainAccessibility.AFTER_FIRST_UNLOCK
)

enum class KeychainAccessibility {
    WHEN_UNLOCKED,
    AFTER_FIRST_UNLOCK,
    WHEN_PASSCODE_SET_THIS_DEVICE_ONLY,
    WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY
}
