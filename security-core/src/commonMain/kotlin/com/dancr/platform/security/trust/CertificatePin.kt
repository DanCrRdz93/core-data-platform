package com.dancr.platform.security.trust

data class CertificatePin(
    val algorithm: String,
    val hash: String
)
