package com.dancr.platform.network.ws.client

/**
 * Represents a WebSocket data frame.
 *
 * Only data frames ([Text], [Binary]) and [Close] frames are exposed to consumers.
 * Control frames (Ping/Pong) are handled at the engine level and never leaked.
 *
 * **Example — receiving frames:**
 * ```kotlin
 * session.incoming.collect { frame ->
 *     when (frame) {
 *         is WebSocketFrame.Text   -> processJson(frame.text)
 *         is WebSocketFrame.Binary -> processBinary(frame.data)
 *         is WebSocketFrame.Close  -> log("Closed: ${frame.code} ${frame.reason}")
 *     }
 * }
 * ```
 *
 * **Example — sending frames:**
 * ```kotlin
 * session.send(WebSocketFrame.Text("{\"type\":\"ping\"}"))
 * session.send(WebSocketFrame.Binary(byteArrayOf(0x01, 0x02)))
 * ```
 *
 * @see WebSocketSession for frame sending/receiving.
 */
sealed class WebSocketFrame {

    data class Text(val text: String) : WebSocketFrame() {
        override fun toString(): String = "Text(${text.length} chars)"
    }

    data class Binary(val data: ByteArray) : WebSocketFrame() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()

        override fun toString(): String = "Binary(${data.size} bytes)"
    }

    data class Close(
        val code: Int = 1000,
        val reason: String? = null
    ) : WebSocketFrame() {
        override fun toString(): String = "Close(code=$code, reason=$reason)"
    }
}
