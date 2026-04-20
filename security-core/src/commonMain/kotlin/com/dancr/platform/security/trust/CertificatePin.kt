package com.dancr.platform.security.trust

/**
 * A single certificate pin consisting of a hash algorithm and the expected hash.
 *
 * Used by [TrustPolicy] to define which server certificates are trusted.
 * The hash is typically a Base64-encoded SHA-256 digest of the certificate's
 * Subject Public Key Info (SPKI).
 *
 * **Example — defining a SHA-256 pin:**
 * ```kotlin
 * val pin = CertificatePin(
 *     algorithm = "sha256",
 *     hash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
 * )
 * ```
 *
 * @property algorithm Hash algorithm (e.g. `"sha256"`).
 * @property hash      Base64-encoded hash of the certificate’s SPKI.
 *
 * @see TrustPolicy.pinnedCertificates
 * @see DefaultTrustPolicy
 */
data class CertificatePin(
    val algorithm: String,
    val hash: String
)
