package com.dancr.platform.network.ktor

import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.security.trust.TrustPolicy
import io.ktor.client.HttpClient

// Creates a platform-configured HttpClient with timeout, TLS pinning, and engine selection.
// - Android: OkHttp engine with CertificatePinner
// - iOS: Darwin engine with handleChallenge for SecTrust evaluation
// When trustPolicy is null, no certificate pinning is applied (system default trust).
internal expect fun createPlatformHttpClient(
    config: NetworkConfig,
    trustPolicy: TrustPolicy?
): HttpClient
