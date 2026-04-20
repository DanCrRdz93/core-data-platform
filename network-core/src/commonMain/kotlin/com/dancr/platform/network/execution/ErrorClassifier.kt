package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.result.NetworkError

/**
 * Maps transport-level failures into typed [NetworkError] instances.
 *
 * Implement per transport module (e.g. `KtorErrorClassifier`) for type-safe
 * exception matching against library-specific exception types.
 *
 * **Example — custom classifier:**
 * ```kotlin
 * class MyErrorClassifier : ErrorClassifier {
 *     override fun classify(response: RawResponse?, cause: Throwable?): NetworkError {
 *         if (cause is MyCustomTimeoutException) {
 *             return NetworkError.Timeout(Diagnostic("Custom timeout"))
 *         }
 *         return DefaultErrorClassifier().classify(response, cause)
 *     }
 * }
 * ```
 *
 * @see DefaultErrorClassifier for the built-in heuristic classifier.
 * @see NetworkError for the error hierarchy.
 */
interface ErrorClassifier {

    /**
     * Classifies a transport failure into a typed [NetworkError].
     *
     * @param response The raw HTTP response (if one was received before the failure).
     * @param cause    The throwable that caused the failure (if transport-level).
     * @return A typed [NetworkError] representing the classified failure.
     */
    fun classify(response: RawResponse?, cause: Throwable?): NetworkError
}
