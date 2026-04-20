package com.dancr.platform.security.session

import com.dancr.platform.security.credential.Credential

/**
 * Bundle of authentication data passed to [SessionController.startSession].
 *
 * Wraps the active [credential] together with optional refresh and expiry metadata.
 * The [toString] override redacts the refresh token (OWASP MASVS-STORAGE-2).
 *
 * **Example — creating session credentials after login:**
 * ```kotlin
 * val credentials = SessionCredentials(
 *     credential = Credential.Bearer(token = "eyJhbGci..."),
 *     refreshToken = "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4",
 *     expiresAtMs = System.currentTimeMillis() + 3_600_000 // 1 hour
 * )
 *
 * sessionController.startSession(credentials)
 * ```
 *
 * @property credential   The active authentication credential.
 * @property refreshToken Optional refresh token for silent re-authentication.
 * @property expiresAtMs  Optional epoch millis when the credential expires.
 */
data class SessionCredentials(
    val credential: Credential,
    val refreshToken: String? = null,
    val expiresAtMs: Long? = null
) {
    override fun toString(): String =
        "SessionCredentials(credential=$credential, refreshToken=${if (refreshToken != null) "██" else "null"}, expiresAtMs=$expiresAtMs)"
}
