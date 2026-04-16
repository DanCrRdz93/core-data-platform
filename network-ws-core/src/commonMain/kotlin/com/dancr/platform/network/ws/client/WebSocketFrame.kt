package com.dancr.platform.network.ws.client

// Represents a WebSocket frame. Only data frames are exposed to consumers.
// Control frames (Ping/Pong) are handled at the engine level — never leaked.
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
