package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.result.Diagnostic
import com.dancr.platform.network.result.NetworkError

/**
 * Built-in [ErrorClassifier] that uses heuristics to classify transport failures.
 *
 * - **Throwables** are classified by exception class name patterns (cross-platform safe).
 * - **Responses** are classified by HTTP status code ranges.
 *
 * Extend this class and override [classifyThrowable] in platform-specific modules
 * for type-safe exception matching (e.g. `KtorErrorClassifier`).
 *
 * **Example — using directly:**
 * ```kotlin
 * val classifier = DefaultErrorClassifier()
 *
 * // From a throwable
 * val error = classifier.classify(null, SocketTimeoutException("timed out"))
 * // → NetworkError.Timeout
 *
 * // From a response
 * val httpError = classifier.classify(RawResponse(statusCode = 401), null)
 * // → NetworkError.Authentication
 * ```
 *
 * @see ErrorClassifier
 * @see KtorErrorClassifier for Ktor-specific classification.
 */
open class DefaultErrorClassifier : ErrorClassifier {

    override fun classify(response: RawResponse?, cause: Throwable?): NetworkError {
        if (cause != null) return classifyThrowable(cause)
        if (response != null) return classifyResponse(response)
        return NetworkError.Unknown()
    }

    /**
     * Classifies a [Throwable] into a [NetworkError] using exception class name heuristics.
     *
     * Override in platform-specific subclasses for type-safe matching
     * (e.g. `HttpRequestTimeoutException` in Ktor).
     *
     * @param cause The throwable to classify.
     * @return A typed [NetworkError].
     */
    protected open fun classifyThrowable(cause: Throwable): NetworkError {
        val diagnostic = Diagnostic(
            description = cause.message ?: "Unknown error",
            cause = cause
        )

        val name = cause::class.simpleName.orEmpty()
        return when {
            name.contains("Timeout", ignoreCase = true) ->
                NetworkError.Timeout(diagnostic)

            name.contains("UnknownHost", ignoreCase = true) ||
                name.contains("ConnectException", ignoreCase = true) ||
                name.contains("NoRouteToHost", ignoreCase = true) ->
                NetworkError.Connectivity(diagnostic)

            name.contains("Serializ", ignoreCase = true) ||
                name.contains("JsonException", ignoreCase = true) ||
                name.contains("ParseException", ignoreCase = true) ->
                NetworkError.Serialization(diagnostic)

            else -> NetworkError.Unknown(diagnostic)
        }
    }

    /**
     * Classifies a non-successful [RawResponse] into a [NetworkError] by status code.
     *
     * @param response The raw HTTP response with a non-2xx status code.
     * @return A typed [NetworkError] (Authentication, Authorization, ClientError, ServerError, or Unknown).
     */
    protected open fun classifyResponse(response: RawResponse): NetworkError {
        val diagnostic = Diagnostic(
            description = "HTTP ${response.statusCode}",
            metadata = mapOf("statusCode" to response.statusCode.toString())
        )

        return when (response.statusCode) {
            401 -> NetworkError.Authentication(diagnostic)
            403 -> NetworkError.Authorization(diagnostic)
            in 400..499 -> NetworkError.ClientError(response.statusCode, diagnostic)
            in 500..599 -> NetworkError.ServerError(response.statusCode, diagnostic)
            else -> NetworkError.Unknown(diagnostic)
        }
    }
}
