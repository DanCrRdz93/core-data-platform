package com.dancr.platform.network.observability

// Logging abstraction for network events.
// The SDK never decides the logging backend — the consumer provides it.
// No-op by default so the SDK is silent unless explicitly configured.
fun interface NetworkLogger {

    fun log(level: Level, tag: String, message: String)

    enum class Level { DEBUG, INFO, WARN, ERROR }

    companion object {
        val NOOP: NetworkLogger = NetworkLogger { _, _, _ -> }
    }
}
