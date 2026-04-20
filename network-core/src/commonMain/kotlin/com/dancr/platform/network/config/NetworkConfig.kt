package com.dancr.platform.network.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the HTTP networking layer.
 *
 * Enforces HTTPS by default (OWASP MASVS-NETWORK-1). Set [allowInsecureConnections]
 * to `true` **only** for local development (localhost / emulator).
 *
 * **Example — production configuration:**
 * ```kotlin
 * val config = NetworkConfig(
 *     baseUrl = "https://api.example.com/v1",
 *     defaultHeaders = mapOf("Accept" to "application/json"),
 *     connectTimeout = 15.seconds,
 *     readTimeout = 30.seconds,
 *     retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 3)
 * )
 * ```
 *
 * **Example — local development:**
 * ```kotlin
 * val devConfig = NetworkConfig(
 *     baseUrl = "http://10.0.2.2:8080",
 *     allowInsecureConnections = true
 * )
 * ```
 *
 * @property baseUrl                  Base URL prepended to every request path.
 * @property defaultHeaders           Headers merged into every request.
 * @property connectTimeout           TCP connection timeout.
 * @property readTimeout              Read / response timeout.
 * @property writeTimeout             Write / request-body timeout.
 * @property retryPolicy              Default retry strategy (can be overridden per-request via [RequestContext][com.dancr.platform.network.execution.RequestContext]).
 * @property allowInsecureConnections If `true`, allows non-HTTPS base URLs. **Never use in production.**
 */
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
