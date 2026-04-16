package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.error.WebSocketError

// Extension point for transport-aware WebSocket error classification.
// Implement per transport module (e.g. KtorWebSocketErrorClassifier) for
// type-safe exception matching against library-specific exception types.
interface WebSocketErrorClassifier {

    fun classify(cause: Throwable): WebSocketError
}
