package com.dancr.platform.security.trust

open class DefaultTrustPolicy(
    private val pins: Map<String, Set<CertificatePin>> = emptyMap()
) : TrustPolicy {

    override fun evaluateHost(hostname: String): TrustEvaluation = TrustEvaluation.Trusted

    override fun pinnedCertificates(): Map<String, Set<CertificatePin>> = pins
}
