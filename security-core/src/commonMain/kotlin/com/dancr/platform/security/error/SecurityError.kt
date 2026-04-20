package com.dancr.platform.security.error

/**
 * Sealed hierarchy of security-related errors.
 *
 * Every variant exposes a user-safe [message] (never leaks implementation details)
 * and an optional [diagnostic] for logging/debugging.
 *
 * **Example — pattern-matching on errors:**
 * ```kotlin
 * when (error) {
 *     is SecurityError.TokenExpired        -> navigateToLogin()
 *     is SecurityError.TokenRefreshFailed   -> showRetryDialog()
 *     is SecurityError.InvalidCredentials   -> showBadCredentialsAlert()
 *     is SecurityError.SecureStorageFailure -> reportStorageIssue(error.diagnostic)
 *     is SecurityError.CertificatePinningFailure -> abortConnection(error.host)
 *     is SecurityError.Unknown             -> logAndIgnore(error.diagnostic)
 * }
 * ```
 *
 * @see Diagnostic for the structured diagnostic payload.
 */
sealed class SecurityError {

    /** User-safe error message. Never contains sensitive information. */
    abstract val message: String

    /** Optional diagnostic detail for logging and debugging. */
    abstract val diagnostic: Diagnostic?

    // -- Authentication lifecycle --

    /** The session token has expired and needs to be refreshed or re-authenticated. */
    data class TokenExpired(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Session has expired"
    }

    /** An attempt to refresh the session token failed. */
    data class TokenRefreshFailed(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Unable to refresh session"
    }

    /** The supplied credentials were rejected by the server (e.g. wrong password). */
    data class InvalidCredentials(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Invalid credentials"
    }

    // -- Secure storage --

    /** A read/write operation on the platform secure store failed. */
    data class SecureStorageFailure(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Secure storage access failed"
    }

    // -- Trust --

    /**
     * Certificate pinning verification failed for the given [host].
     *
     * @property host The hostname whose certificate did not match any configured pin.
     */
    data class CertificatePinningFailure(
        val host: String,
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Connection security verification failed"
    }

    // -- Catch-all --

    /** Unclassified security error. Inspect [diagnostic] for details. */
    data class Unknown(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "An unexpected security error occurred"
    }
}
