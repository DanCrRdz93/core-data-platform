package com.dancr.platform.network.ktor

import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.security.trust.TrustPolicy
import io.ktor.client.HttpClient

/**
 * Creates a platform-configured Ktor [HttpClient] with timeout, TLS pinning, and engine selection.
 *
 * - **Android**: OkHttp engine with `CertificatePinner`
 * - **iOS**: Darwin engine with `handleChallenge` for `SecTrust` evaluation
 *
 * To opt out of pinning, pass [TrustPolicy.SystemDefault].
 *
 * @param config      Network configuration (timeouts, base URL).
 * @param trustPolicy TLS pinning policy. Use [TrustPolicy.SystemDefault] for no-pinning.
 * @return A platform-configured [HttpClient] instance.
 */
internal expect fun createPlatformHttpClient(
    config: NetworkConfig,
    trustPolicy: TrustPolicy
): HttpClient
