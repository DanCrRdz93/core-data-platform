package com.dancr.platform.security.store

/**
 * Configuration for [IosSecretStore].
 *
 * **Example:**
 * ```kotlin
 * val config = KeychainConfig(
 *     serviceName = "com.myapp.security",
 *     accessGroup = "group.com.myapp.shared",
 *     accessibility = KeychainAccessibility.WHEN_UNLOCKED
 * )
 * ```
 *
 * @property serviceName   Keychain service name used to scope stored items.
 * @property accessGroup   Optional Keychain access group for sharing between apps/extensions.
 * @property accessibility When the stored items are accessible (maps to `kSecAttrAccessible`).
 */
data class KeychainConfig(
    val serviceName: String = "com.dancr.platform.security",
    val accessGroup: String? = null,
    val accessibility: KeychainAccessibility = KeychainAccessibility.AFTER_FIRST_UNLOCK
)

/**
 * Maps to iOS `kSecAttrAccessible` values controlling when Keychain items are readable.
 *
 * @see KeychainConfig.accessibility
 */
enum class KeychainAccessibility {
    /** Item is accessible only while the device is unlocked. */
    WHEN_UNLOCKED,
    /** Item is accessible after the first unlock until the next restart. */
    AFTER_FIRST_UNLOCK,
    /** Item is accessible only when a passcode is set; not migrated to new devices. */
    WHEN_PASSCODE_SET_THIS_DEVICE_ONLY,
    /** Item is accessible only while unlocked; not migrated to new devices. */
    WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    /** Item is accessible after first unlock; not migrated to new devices. */
    AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY
}
