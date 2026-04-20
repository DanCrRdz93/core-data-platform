package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.error.WebSocketError

/**
 * Maps transport-level failures into typed [WebSocketError] instances.
 *
 * Implement per transport module (e.g. `KtorWebSocketErrorClassifier`) for
 * type-safe exception matching against library-specific exception types.
 *
 * **Example — custom classifier:**
 * ```kotlin
 * class MyWsClassifier : WebSocketErrorClassifier {
 *     override fun classify(cause: Throwable): WebSocketError {
 *         if (cause is MyCustomTimeoutException) {
 *             return WebSocketError.Timeout(Diagnostic("Custom WS timeout"))
 *         }
 *         return DefaultWebSocketErrorClassifier().classify(cause)
 *     }
 * }
 * ```
 *
 * @see DefaultWebSocketErrorClassifier for the built-in heuristic classifier.
 * @see WebSocketError for the error hierarchy.
 */
interface WebSocketErrorClassifier {

    /**
     * Classifies a transport failure into a typed [WebSocketError].
     *
     * @param cause The throwable that caused the connection failure.
     * @return A typed [WebSocketError].
     */
    fun classify(cause: Throwable): WebSocketError
}
