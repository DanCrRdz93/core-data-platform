package com.dancr.platform.network.ws.datasource

import com.dancr.platform.network.ws.client.WebSocketFrame
import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.connection.WebSocketConnection
import com.dancr.platform.network.ws.execution.SafeWebSocketExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Convenience base class for WebSocket data sources.
 *
 * Analogous to [RemoteDataSource][com.dancr.platform.network.datasource.RemoteDataSource] for HTTP.
 * Subclass this to create domain-specific streaming data sources.
 *
 * **Example:**
 * ```kotlin
 * class ChatDataSource(
 *     executor: SafeWebSocketExecutor
 * ) : StreamingDataSource(executor) {
 *
 *     fun messages(roomId: String): Flow<ChatMessage> = observe(
 *         request = WebSocketRequest(path = "/chat/$roomId"),
 *         deserialize = { frame ->
 *             if (frame is WebSocketFrame.Text) json.decodeFromString(frame.text) else null
 *         }
 *     )
 * }
 * ```
 *
 * @param executor The managed [SafeWebSocketExecutor] for creating connections.
 * @see SafeWebSocketExecutor
 */
abstract class StreamingDataSource(
    private val executor: SafeWebSocketExecutor
) {

    /** Creates a raw managed [WebSocketConnection] for the given [request]. */
    protected fun connect(request: WebSocketRequest): WebSocketConnection =
        executor.connect(request)

    /**
     * Creates a connection, deserializes incoming frames, and **automatically closes
     * the connection** when the downstream collector is cancelled or completes.
     *
     * @param T           The deserialized frame type.
     * @param request     The WebSocket connection request.
     * @param deserialize Maps a [WebSocketFrame] to `T`, or `null` to skip the frame.
     * @return A [Flow] of deserialized frames.
     */
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
