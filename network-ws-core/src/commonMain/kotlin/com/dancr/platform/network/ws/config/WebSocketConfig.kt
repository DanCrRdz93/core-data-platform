package com.dancr.platform.network.ws.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the WebSocket networking layer.
 *
 * Enforces WSS by default (OWASP MASVS-NETWORK-1). Set [allowInsecureConnections]
 * to `true` **only** for local development.
 *
 * **Example — production configuration:**
 * ```kotlin
 * val config = WebSocketConfig(
 *     url = "wss://ws.example.com/v1",
 *     defaultHeaders = mapOf("Accept" to "application/json"),
 *     connectTimeout = 15.seconds,
 *     pingInterval = 30.seconds,
 *     reconnectPolicy = ReconnectPolicy.ExponentialBackoff(maxRetries = 5)
 * )
 * ```
 *
 * @property url                      WebSocket server URL (must start with `wss://` unless insecure).
 * @property defaultHeaders           Headers merged into every connection request.
 * @property connectTimeout           TCP connection timeout.
 * @property pingInterval             Interval between keep-alive pings.
 * @property reconnectPolicy          Automatic reconnection strategy.
 * @property allowInsecureConnections  If `true`, allows non-WSS URLs. **Never use in production.**
 */
data class WebSocketConfig(
    val url: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val connectTimeout: Duration = 30.seconds,
    val pingInterval: Duration = 30.seconds,
    val reconnectPolicy: ReconnectPolicy = ReconnectPolicy.ExponentialBackoff(),
    // OWASP MASVS-NETWORK-1: Enforce WSS by default.
    // Set to true ONLY for local development (localhost, emulator).
    val allowInsecureConnections: Boolean = false
) {
    init {
        require(url.isNotBlank()) { "url must not be blank" }
        if (!allowInsecureConnections) {
            require(url.startsWith("wss://")) {
                "url must use WSS for secure communication. " +
                    "Set allowInsecureConnections = true only for local development."
            }
        }
    }
}
