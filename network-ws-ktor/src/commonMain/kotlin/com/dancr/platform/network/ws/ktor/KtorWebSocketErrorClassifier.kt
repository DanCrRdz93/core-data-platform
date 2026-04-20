package com.dancr.platform.network.ws.ktor

import com.dancr.platform.network.ws.error.WebSocketError
import com.dancr.platform.network.ws.execution.DefaultWebSocketErrorClassifier
import com.dancr.platform.network.ws.result.Diagnostic
import io.ktor.client.plugins.HttpRequestTimeoutException

/**
 * Ktor-specific [WebSocketErrorClassifier][com.dancr.platform.network.ws.execution.WebSocketErrorClassifier]
 * that type-safely matches Ktor exception types before falling back to
 * [DefaultWebSocketErrorClassifier] heuristics.
 *
 * Classifies [HttpRequestTimeoutException] as [WebSocketError.Timeout].
 *
 * **Example:**
 * ```kotlin
 * val executor = DefaultSafeWebSocketExecutor(
 *     engine = KtorWebSocketEngine.create(config),
 *     config = config,
 *     classifier = KtorWebSocketErrorClassifier()
 * )
 * ```
 *
 * @see DefaultWebSocketErrorClassifier
 */
class KtorWebSocketErrorClassifier : DefaultWebSocketErrorClassifier() {

    override fun classifyThrowable(cause: Throwable): WebSocketError {
        if (cause is HttpRequestTimeoutException) {
            return WebSocketError.Timeout(
                diagnostic = Diagnostic(
                    description = cause.message ?: "WebSocket connection timed out",
                    cause = cause
                )
            )
        }

        return super.classifyThrowable(cause)
    }
}
