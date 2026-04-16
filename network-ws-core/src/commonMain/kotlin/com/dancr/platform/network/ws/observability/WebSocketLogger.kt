package com.dancr.platform.network.ws.observability

// Abstraction for log output. Implement with your backend (Logcat, Timber, OSLog, etc.).
// Analogous to NetworkLogger for HTTP.
// No-op by default so the SDK is silent unless explicitly configured.
fun interface WebSocketLogger {

    fun log(level: Level, tag: String, message: String)

    enum class Level { DEBUG, INFO, WARN, ERROR }

    companion object {
        val NOOP: WebSocketLogger = WebSocketLogger { _, _, _ -> }
    }
}
