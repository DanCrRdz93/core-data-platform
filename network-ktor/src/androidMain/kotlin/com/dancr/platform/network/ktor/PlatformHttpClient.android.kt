package com.dancr.platform.network.ktor

import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.security.trust.TrustPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okhttp3.CertificatePinner

internal actual fun createPlatformHttpClient(
    config: NetworkConfig,
    trustPolicy: TrustPolicy?
): HttpClient = HttpClient(OkHttp) {

    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeout.inWholeMilliseconds
        requestTimeoutMillis = config.readTimeout.inWholeMilliseconds
        socketTimeoutMillis = config.writeTimeout.inWholeMilliseconds
    }

    expectSuccess = false

    if (trustPolicy != null) {
        val pins = trustPolicy.pinnedCertificates()
        if (pins.isNotEmpty()) {
            engine {
                config {
                    certificatePinner(buildCertificatePinner(pins))
                }
            }
        }
    }
}

// Maps TrustPolicy pins to OkHttp's CertificatePinner.
// Pin format follows OkHttp convention: "algorithm/base64hash"
// Example: CertificatePin(algorithm = "sha256", hash = "AAAA...=")
//          → OkHttp pin string "sha256/AAAA...="
private fun buildCertificatePinner(
    pins: Map<String, Set<com.dancr.platform.security.trust.CertificatePin>>
): CertificatePinner = CertificatePinner.Builder().apply {
    pins.forEach { (hostname, pinSet) ->
        pinSet.forEach { pin ->
            add(hostname, "${pin.algorithm}/${pin.hash}")
        }
    }
}.build()
