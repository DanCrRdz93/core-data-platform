package com.dancr.platform.security.error

sealed class SecurityError {

    abstract val message: String
    abstract val diagnostic: Diagnostic?

    // -- Authentication lifecycle --

    data class TokenExpired(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Session has expired"
    }

    data class TokenRefreshFailed(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Unable to refresh session"
    }

    data class InvalidCredentials(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Invalid credentials"
    }

    // -- Secure storage --

    data class SecureStorageFailure(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Secure storage access failed"
    }

    // -- Trust --

    data class CertificatePinningFailure(
        val host: String,
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "Connection security verification failed"
    }

    // -- Catch-all --

    data class Unknown(
        override val diagnostic: Diagnostic? = null
    ) : SecurityError() {
        override val message: String get() = "An unexpected security error occurred"
    }
}
