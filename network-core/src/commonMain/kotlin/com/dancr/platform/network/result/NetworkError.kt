package com.dancr.platform.network.result

sealed class NetworkError {

    abstract val message: String
    abstract val diagnostic: Diagnostic?

    // Override in subclasses to control which errors trigger automatic retry.
    // Custom ErrorClassifier implementations can return errors with different retryability.
    open val isRetryable: Boolean get() = false

    // -- Transport layer --

    data class Connectivity(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Unable to reach the server"
        override val isRetryable: Boolean get() = true
    }

    data class Timeout(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "The request timed out"
        override val isRetryable: Boolean get() = true
    }

    data class Cancelled(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "The request was cancelled"
    }

    // -- HTTP semantic layer --

    data class Authentication(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Authentication required"
    }

    data class Authorization(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Access denied"
    }

    data class ClientError(
        val statusCode: Int,
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Invalid request"
    }

    data class ServerError(
        val statusCode: Int,
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Server error"
        override val isRetryable: Boolean get() = true
    }

    // -- Data processing layer --

    data class Serialization(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Failed to process response data"
    }

    data class ResponseValidation(
        val reason: String,
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Response validation failed"
    }

    // -- Catch-all --

    data class Unknown(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "An unexpected error occurred"
    }
}
