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
 * To opt out of pinning, pass [TrustPolicy.SystemDefault].
 *
 * @param config      WebSocket configuration (URL, timeouts, ping interval).
 * @param trustPolicy TLS pinning policy. Use [TrustPolicy.SystemDefault] for no-pinning.
 * @return A platform-configured [HttpClient] with WebSocket capabilities.
 */
internal expect fun createPlatformWebSocketClient(
    config: WebSocketConfig,
    trustPolicy: TrustPolicy
): HttpClient
