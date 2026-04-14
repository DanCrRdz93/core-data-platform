package com.dancr.platform.network.observability

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.result.NetworkError

// Observes network lifecycle events and delegates formatted output to a NetworkLogger.
// Keeps formatting minimal — the consumer controls the logging backend and verbosity.
//
// OWASP MASVS-PRIVACY: headerSanitizer defaults to REDACT ALL values.
// The safe path is the default — no sensitive data leaks unless the consumer
// explicitly provides a sanitizer. Wire security-core's DefaultLogSanitizer:
//     headerSanitizer = { key, value -> logSanitizer.sanitize(key, value) }
class LoggingObserver(
    private val logger: NetworkLogger = NetworkLogger.NOOP,
    private val tag: String = "CoreDataPlatform",
    private val headerSanitizer: (key: String, value: String) -> String = REDACT_ALL
) : NetworkEventObserver {

    override fun onRequestStarted(request: HttpRequest, context: RequestContext?) {
        logger.log(
            NetworkLogger.Level.DEBUG, tag,
            "--> ${request.method.name} ${request.path}${formatHeaders(request.headers)}"
        )
    }

    override fun onResponseReceived(
        request: HttpRequest,
        response: RawResponse,
        durationMs: Long,
        context: RequestContext?
    ) {
        val level = if (response.isSuccessful) NetworkLogger.Level.INFO else NetworkLogger.Level.WARN
        logger.log(
            level, tag,
            "<-- ${response.statusCode} ${request.method.name} ${request.path} (${durationMs}ms)"
        )
    }

    override fun onRetryScheduled(
        request: HttpRequest,
        attempt: Int,
        maxAttempts: Int,
        error: NetworkError,
        delayMs: Long
    ) {
        logger.log(
            NetworkLogger.Level.WARN, tag,
            "⟳ Retry $attempt/$maxAttempts for ${request.method.name} ${request.path} " +
                "after ${delayMs}ms — ${error.message}"
        )
    }

    override fun onRequestFailed(
        request: HttpRequest,
        error: NetworkError,
        durationMs: Long,
        context: RequestContext?
    ) {
        logger.log(
            NetworkLogger.Level.ERROR, tag,
            "FAILED ${request.method.name} ${request.path} (${durationMs}ms) — ${error.message}"
        )
    }

    // -- Internal --

    private fun formatHeaders(headers: Map<String, String>): String {
        if (headers.isEmpty()) return ""
        val sanitized = headers.entries.joinToString { (k, v) ->
            "$k: ${headerSanitizer(k, v)}"
        }
        return " [$sanitized]"
    }

    companion object {
        // OWASP MASVS-PRIVACY default: redact ALL header values.
        // Consumers who want selective visibility should provide their own sanitizer
        // (e.g., security-core's DefaultLogSanitizer which only redacts sensitive keys).
        val REDACT_ALL: (String, String) -> String = { _, _ -> "██" }
    }
}
