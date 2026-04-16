package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.error.WebSocketError
import com.dancr.platform.network.ws.result.Diagnostic

// Default classifier for transport-agnostic exceptions.
// Transport modules (e.g. KtorWebSocketErrorClassifier) extend this
// to handle library-specific exception types first, then fall back here.
open class DefaultWebSocketErrorClassifier : WebSocketErrorClassifier {

    override fun classify(cause: Throwable): WebSocketError {
        return classifyThrowable(cause)
    }

    protected open fun classifyThrowable(cause: Throwable): WebSocketError {
        val message = cause.message.orEmpty()
        val diagnostic = Diagnostic(description = message, cause = cause)

        return when {
            isConnectivityError(message) -> WebSocketError.ConnectionFailed(diagnostic = diagnostic)
            isTimeoutError(message) -> WebSocketError.Timeout(diagnostic = diagnostic)
            else -> WebSocketError.Unknown(diagnostic = diagnostic)
        }
    }

    private fun isConnectivityError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("unable to resolve host") ||
            lower.contains("connection refused") ||
            lower.contains("no route to host") ||
            lower.contains("network is unreachable")
    }

    private fun isTimeoutError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("timed out") || lower.contains("timeout")
    }
}
