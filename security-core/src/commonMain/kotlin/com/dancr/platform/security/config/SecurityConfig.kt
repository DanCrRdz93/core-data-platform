package com.dancr.platform.security.config

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
