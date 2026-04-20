package com.dancr.platform.network.ws.observability

/**
 * Logging abstraction for WebSocket events.
 *
 * Implement with your backend (Logcat, Timber, OSLog, etc.).
 * Analogous to [NetworkLogger][com.dancr.platform.network.observability.NetworkLogger] for HTTP.
 * No-op by default so the SDK is silent unless explicitly configured.
 *
 * **Example — Android Logcat implementation:**
 * ```kotlin
 * val logcatWsLogger = WebSocketLogger { level, tag, message ->
 *     when (level) {
 *         WebSocketLogger.Level.DEBUG -> Log.d(tag, message)
 *         WebSocketLogger.Level.INFO  -> Log.i(tag, message)
 *         WebSocketLogger.Level.WARN  -> Log.w(tag, message)
 *         WebSocketLogger.Level.ERROR -> Log.e(tag, message)
 *     }
 * }
 * ```
 *
 * @see WebSocketLoggingObserver for the built-in observer that delegates to this.
 */
fun interface WebSocketLogger {

    /**
     * Logs a WebSocket event.
     *
     * @param level   Severity level.
     * @param tag     Log tag (e.g. `"WebSocket"`).
     * @param message Formatted log message.
     */
    fun log(level: Level, tag: String, message: String)

    /** Log severity levels. */
    enum class Level { DEBUG, INFO, WARN, ERROR }

    companion object {
        /** Silent no-op logger. */
        val NOOP: WebSocketLogger = WebSocketLogger { _, _, _ -> }
    }
}
