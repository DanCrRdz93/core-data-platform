package com.dancr.platform.security.sanitizer

import com.dancr.platform.security.config.SecurityConfig

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
