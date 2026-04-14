package com.dancr.platform.network.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class NetworkConfig(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val connectTimeout: Duration = 30.seconds,
    val readTimeout: Duration = 30.seconds,
    val writeTimeout: Duration = 30.seconds,
    val retryPolicy: RetryPolicy = RetryPolicy.None,
    // OWASP MASVS-NETWORK-1: Enforce HTTPS by default.
    // Set to true ONLY for local development (localhost, emulator).
    val allowInsecureConnections: Boolean = false
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        if (!allowInsecureConnections) {
            require(baseUrl.startsWith("https://")) {
                "baseUrl must use HTTPS for secure communication. " +
                    "Set allowInsecureConnections = true only for local development."
            }
        }
    }
}
