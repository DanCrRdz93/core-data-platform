package com.dancr.platform.network.observability

/**
 * Logging abstraction for network events.
 *
 * The SDK never decides the logging backend — the consumer provides it.
 * No-op by default so the SDK is silent unless explicitly configured.
 *
 * **Example — Android Logcat implementation:**
 * ```kotlin
 * val androidLogger = NetworkLogger { level, tag, message ->
 *     when (level) {
 *         NetworkLogger.Level.DEBUG -> Log.d(tag, message)
 *         NetworkLogger.Level.INFO  -> Log.i(tag, message)
 *         NetworkLogger.Level.WARN  -> Log.w(tag, message)
 *         NetworkLogger.Level.ERROR -> Log.e(tag, message)
 *     }
 * }
 * ```
 *
 * @see LoggingObserver for the built-in observer that delegates to this logger.
 */
fun interface NetworkLogger {

    /**
     * Logs a network event.
     *
     * @param level   Severity level.
     * @param tag     Logging tag (e.g. `"CoreDataPlatform"`).
     * @param message Formatted log message.
     */
    fun log(level: Level, tag: String, message: String)

    /** Log severity levels. */
    enum class Level { DEBUG, INFO, WARN, ERROR }

    companion object {
        /** No-op logger that silently discards all messages. */
        val NOOP: NetworkLogger = NetworkLogger { _, _, _ -> }

        /**
         * Multiplatform console logger that writes to standard output via [println].
         *
         * Suitable for development, demos, and CLI tooling. Production code should
         * route through a structured backend (Logcat, OSLog, Crashlytics, etc.).
         *
         * **Example:**
         * ```kotlin
         * val observer = LoggingObserver(logger = NetworkLogger.Console)
         * ```
         */
        val Console: NetworkLogger = NetworkLogger { level, tag, message ->
            println("[$level] $tag — $message")
        }
    }
}
