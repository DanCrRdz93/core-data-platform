package com.dancr.platform.security.sanitizer

/**
 * Redacts sensitive values from key-value pairs before logging.
 *
 * Implementations decide which keys are sensitive (e.g. `authorization`, `cookie`)
 * and replace their values with a placeholder.
 *
 * **Example — using with [DefaultLogSanitizer]:**
 * ```kotlin
 * val sanitizer: LogSanitizer = DefaultLogSanitizer()
 *
 * sanitizer.sanitize("Authorization", "Bearer eyJ...") // "██"
 * sanitizer.sanitize("Content-Type", "application/json") // "application/json"
 * ```
 *
 * @see DefaultLogSanitizer for the built-in implementation.
 */
interface LogSanitizer {

    /**
     * Returns the (possibly redacted) value for the given [key].
     *
     * @param key   Header or field name (matched case-insensitively by default implementations).
     * @param value The original value.
     * @return The original [value] if the key is non-sensitive, or a redacted placeholder otherwise.
     */
    fun sanitize(key: String, value: String): String
}

/**
 * Sanitizes all values in a single-value header map.
 *
 * **Example:**
 * ```kotlin
 * val headers = mapOf("Authorization" to "Bearer token", "Accept" to "application/json")
 * val safe = sanitizer.sanitizeHeaders(headers)
 * // {"Authorization" to "██", "Accept" to "application/json"}
 * ```
 */
fun LogSanitizer.sanitizeHeaders(headers: Map<String, String>): Map<String, String> =
    headers.mapValues { (key, value) -> sanitize(key, value) }

/**
 * Sanitizes all values in a multi-value header map (e.g. HTTP response headers).
 *
 * **Example:**
 * ```kotlin
 * val headers = mapOf("Set-Cookie" to listOf("session=abc", "id=xyz"))
 * val safe = sanitizer.sanitizeMultiValueHeaders(headers)
 * // {"Set-Cookie" to ["██", "██"]}
 * ```
 */
fun LogSanitizer.sanitizeMultiValueHeaders(headers: Map<String, List<String>>): Map<String, List<String>> =
    headers.mapValues { (key, values) -> values.map { sanitize(key, it) } }
