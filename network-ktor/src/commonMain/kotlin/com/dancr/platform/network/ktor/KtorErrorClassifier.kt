package com.dancr.platform.network.ktor

import com.dancr.platform.network.execution.DefaultErrorClassifier
import com.dancr.platform.network.result.Diagnostic
import com.dancr.platform.network.result.NetworkError
import io.ktor.client.plugins.HttpRequestTimeoutException

/**
 * Ktor-specific [ErrorClassifier][com.dancr.platform.network.execution.ErrorClassifier] that
 * type-safely matches Ktor exception types before falling back to [DefaultErrorClassifier] heuristics.
 *
 * Classifies [HttpRequestTimeoutException] as [NetworkError.Timeout].
 *
 * **Example:**
 * ```kotlin
 * val executor = DefaultSafeRequestExecutor(
 *     engine = KtorHttpEngine.create(config),
 *     config = config,
 *     classifier = KtorErrorClassifier()
 * )
 * ```
 *
 * @see DefaultErrorClassifier
 */
class KtorErrorClassifier : DefaultErrorClassifier() {

    override fun classifyThrowable(cause: Throwable): NetworkError {
        if (cause is HttpRequestTimeoutException) {
            return NetworkError.Timeout(
                diagnostic = Diagnostic(
                    description = cause.message ?: "Request timed out",
                    cause = cause
                )
            )
        }

        return super.classifyThrowable(cause)
    }
}
