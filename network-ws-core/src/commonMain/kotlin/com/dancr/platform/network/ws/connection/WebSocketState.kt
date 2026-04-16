package com.dancr.platform.network.ws.connection

import com.dancr.platform.network.ws.error.WebSocketError

// Observable connection state. Consumers use this to update UI
// (e.g., show "Reconnecting…" banner) without inspecting frames.
sealed class WebSocketState {

    data class Connecting(val attempt: Int = 0) : WebSocketState()

    data object Connected : WebSocketState()

    data class Disconnected(val error: WebSocketError? = null) : WebSocketState()
}
