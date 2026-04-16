package com.dancr.platform.network.ws.datasource

import com.dancr.platform.network.ws.client.WebSocketFrame
import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.connection.WebSocketConnection
import com.dancr.platform.network.ws.execution.SafeWebSocketExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

// Base class for WebSocket data sources. Analogous to RemoteDataSource for HTTP.
// Subclass this to create domain-specific streaming data sources.
abstract class StreamingDataSource(
    private val executor: SafeWebSocketExecutor
) {

    protected fun connect(request: WebSocketRequest): WebSocketConnection =
        executor.connect(request)

    // Creates a connection, deserializes incoming frames, and **automatically closes
    // the connection** when the downstream collector is cancelled or completes.
    // This prevents resource leaks — the connection scope is always cleaned up.
    protected fun <T> observe(
        request: WebSocketRequest,
        deserialize: (WebSocketFrame) -> T?
    ): Flow<T> = flow {
        val connection = executor.connect(request)
        try {
            connection.incoming.mapNotNull(deserialize).collect { emit(it) }
        } finally {
            connection.close()
        }
    }
}
