package com.dancr.platform.security.trust

interface TrustPolicy {

    fun evaluateHost(hostname: String): TrustEvaluation

    fun pinnedCertificates(): Map<String, Set<CertificatePin>>
}
