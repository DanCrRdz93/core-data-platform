package com.dancr.platform.network.ws.ktor

import com.dancr.platform.network.ws.config.WebSocketConfig
import com.dancr.platform.security.trust.TrustPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.CertificatePinner

/**
 * Android implementation using the OkHttp engine with WebSocket support.
 *
 * Configures ping interval and connect timeout from [WebSocketConfig], and applies
 * certificate pinning via OkHttp's [CertificatePinner] when a [TrustPolicy] is provided.
 */
internal actual fun createPlatformWebSocketClient(
    config: WebSocketConfig,
    trustPolicy: TrustPolicy
): HttpClient = HttpClient(OkHttp) {

    install(WebSockets) {
        pingIntervalMillis = config.pingInterval.inWholeMilliseconds
    }

    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeout.inWholeMilliseconds
    }

    expectSuccess = false

    val pins = trustPolicy.pinnedCertificates()
    if (pins.isNotEmpty()) {
        engine {
            config {
                certificatePinner(buildCertificatePinner(pins))
            }
        }
    }
}

private fun buildCertificatePinner(
    pins: Map<String, Set<com.dancr.platform.security.trust.CertificatePin>>
): CertificatePinner = CertificatePinner.Builder().apply {
    pins.forEach { (hostname, pinSet) ->
        pinSet.forEach { pin ->
            add(hostname, "${pin.algorithm}/${pin.hash}")
        }
    }
}.build()
