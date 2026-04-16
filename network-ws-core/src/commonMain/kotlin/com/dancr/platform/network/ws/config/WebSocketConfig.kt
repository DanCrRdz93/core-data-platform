package com.dancr.platform.network.ws.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
