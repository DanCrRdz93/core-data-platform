package com.dancr.platform.network.ws.ktor

import com.dancr.platform.network.ws.config.WebSocketConfig
import com.dancr.platform.security.trust.TrustPolicy
import io.ktor.client.HttpClient

/**
 * Creates a platform-configured Ktor [HttpClient] with WebSocket support, timeout, and TLS pinning.
 *
 * - **Android**: OkHttp engine with `CertificatePinner` + WebSockets plugin
 * - **iOS**: Darwin engine with `handleChallenge` + WebSockets plugin
 *
 * When [trustPolicy] is `null`, no certificate pinning is applied (system default trust).
 *
 * @param config      WebSocket configuration (URL, timeouts, ping interval).
 * @param trustPolicy Optional TLS pinning policy.
 * @return A platform-configured [HttpClient] with WebSocket capabilities.
 */
internal expect fun createPlatformWebSocketClient(
    config: WebSocketConfig,
    trustPolicy: TrustPolicy?
): HttpClient
