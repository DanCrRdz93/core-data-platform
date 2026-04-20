package com.dancr.platform.security.sanitizer

import com.dancr.platform.security.config.SecurityConfig

/**
 * Built-in [LogSanitizer] that redacts values whose keys match
 * [SecurityConfig.sensitiveHeaders] or [SecurityConfig.sensitiveKeys].
 *
 * Key matching is case-insensitive. Non-sensitive keys pass through unchanged.
 *
 * **Example:**
 * ```kotlin
 * val sanitizer = DefaultLogSanitizer() // uses SecurityConfig defaults
 *
 * sanitizer.sanitize("Authorization", "Bearer tok") // "██"
 * sanitizer.sanitize("Content-Type", "application/json") // "application/json"
 *
 * // Custom config:
 * val custom = DefaultLogSanitizer(
 *     config = SecurityConfig(sensitiveHeaders = setOf("x-custom-secret"))
 * )
 * ```
 *
 * @param config Security configuration that defines which keys are sensitive.
 * @see LogSanitizer
 * @see SecurityConfig
 */
class DefaultLogSanitizer(
    private val config: SecurityConfig = SecurityConfig()
) : LogSanitizer {

    override fun sanitize(key: String, value: String): String {
        val normalized = key.lowercase()
        return if (normalized in config.sensitiveHeaders || normalized in config.sensitiveKeys) {
            config.redactedPlaceholder
        } else {
            value
        }
    }
}
