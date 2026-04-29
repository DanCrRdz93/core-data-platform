package com.dancr.platform.security.trust

/**
 * Defines the certificate pinning and host-trust rules for network connections.
 *
 * Passed to engine factories (`KtorHttpEngine.create`, `KtorWebSocketEngine.create`)
 * to enforce TLS certificate pinning on Android (OkHttp `CertificatePinner`) and
 * iOS (Darwin `handleChallenge` + `SecTrust`).
 *
 * **Example — pinning a production API:**
 * ```kotlin
 * val trustPolicy = DefaultTrustPolicy(
 *     pins = mapOf(
 *         "api.example.com" to setOf(
 *             CertificatePin(algorithm = "sha256", hash = "AAAA...="),
 *             CertificatePin(algorithm = "sha256", hash = "BBBB...=") // backup pin
 *         )
 *     )
 * )
 *
 * val engine = KtorHttpEngine.create(config, trustPolicy)
 * ```
 *
 * @see DefaultTrustPolicy for the built-in implementation.
 * @see TrustEvaluation for the evaluation result.
 * @see CertificatePin for pin format details.
 */
interface TrustPolicy {

    /**
     * Evaluates whether [hostname] should be trusted.
     *
     * @param hostname The server hostname to evaluate.
     * @return [TrustEvaluation.Trusted] or [TrustEvaluation.Denied] with a reason.
     */
    fun evaluateHost(hostname: String): TrustEvaluation

    /**
     * Returns the set of certificate pins per hostname.
     *
     * @return Map of hostname → set of [CertificatePin]. Empty map means no pinning.
     */
    fun pinnedCertificates(): Map<String, Set<CertificatePin>>

    companion object {
        /**
         * Sentinel policy that opts into system-default trust with **no pinning**.
         *
         * Use this to make the absence of certificate pinning an explicit, auditable
         * decision in `KtorHttpEngine.createSystemDefault(...)` or `create(..., trustPolicy)`,
         * rather than passing `null`.
         *
         * Production deployments handling sensitive data should prefer
         * [DefaultTrustPolicy] with concrete [CertificatePin] entries.
         *
         * **Example:**
         * ```kotlin
         * val engine = KtorHttpEngine.createSystemDefault(config)
         * // equivalent to:
         * val engine = KtorHttpEngine.create(config, TrustPolicy.SystemDefault)
         * ```
         */
        val SystemDefault: TrustPolicy = DefaultTrustPolicy()
    }
}
