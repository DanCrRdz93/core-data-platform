package com.dancr.platform.security.config

/**
 * Configuration for security-related features such as log sanitization.
 *
 * Defines which header names and field keys are considered sensitive and should
 * be redacted in logs. Used by [DefaultLogSanitizer][com.dancr.platform.security.sanitizer.DefaultLogSanitizer].
 *
 * **Example — using defaults:**
 * ```kotlin
 * val config = SecurityConfig() // uses DEFAULT_SENSITIVE_HEADERS & DEFAULT_SENSITIVE_KEYS
 * ```
 *
 * **Example — custom sensitive keys:**
 * ```kotlin
 * val config = SecurityConfig(
 *     sensitiveHeaders = SecurityConfig.DEFAULT_SENSITIVE_HEADERS + setOf("x-custom-auth"),
 *     sensitiveKeys = SecurityConfig.DEFAULT_SENSITIVE_KEYS + setOf("client_secret"),
 *     redactedPlaceholder = "[REDACTED]"
 * )
 * ```
 *
 * @property sensitiveHeaders  Header names to redact (matched case-insensitively).
 * @property sensitiveKeys     Field/key names to redact (matched case-insensitively).
 * @property redactedPlaceholder Replacement string shown in place of redacted values.
 */
data class SecurityConfig(
    val sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS,
    val sensitiveKeys: Set<String> = DEFAULT_SENSITIVE_KEYS,
    val redactedPlaceholder: String = "██"
) {
    companion object {
        val DEFAULT_SENSITIVE_HEADERS: Set<String> = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "proxy-authorization"
        )

        val DEFAULT_SENSITIVE_KEYS: Set<String> = setOf(
            "password",
            "secret",
            "token",
            "api_key",
            "apikey",
            "access_token",
            "refresh_token"
        )
    }
}
