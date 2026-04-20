package com.dancr.platform.security.trust

/**
 * Default [TrustPolicy] that trusts all hosts and optionally enforces certificate pinning.
 *
 * [evaluateHost] always returns [TrustEvaluation.Trusted]. The actual pin verification
 * is performed by the platform engine (OkHttp `CertificatePinner` on Android,
 * Darwin `handleChallenge` on iOS) using the pins from [pinnedCertificates].
 *
 * **Example — no pinning (system default trust):**
 * ```kotlin
 * val policy = DefaultTrustPolicy() // empty pins → no pinning
 * ```
 *
 * **Example — with certificate pinning:**
 * ```kotlin
 * val policy = DefaultTrustPolicy(
 *     pins = mapOf(
 *         "api.example.com" to setOf(
 *             CertificatePin("sha256", "AAAA...="),
 *             CertificatePin("sha256", "BBBB...=") // backup
 *         )
 *     )
 * )
 * ```
 *
 * @param pins Map of hostname → set of [CertificatePin]. Empty by default (no pinning).
 * @see TrustPolicy
 */
open class DefaultTrustPolicy(
    private val pins: Map<String, Set<CertificatePin>> = emptyMap()
) : TrustPolicy {

    /** Always returns [TrustEvaluation.Trusted]. Override for custom host validation. */
    override fun evaluateHost(hostname: String): TrustEvaluation = TrustEvaluation.Trusted

    /** Returns the configured certificate pins. */
    override fun pinnedCertificates(): Map<String, Set<CertificatePin>> = pins
}
