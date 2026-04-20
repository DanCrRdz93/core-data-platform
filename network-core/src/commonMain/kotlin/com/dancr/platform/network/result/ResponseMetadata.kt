package com.dancr.platform.network.result

/**
 * Metadata about a successful HTTP response.
 *
 * Attached to [NetworkResult.Success] by the executor. Contains timing,
 * retry information, and raw response headers for downstream consumers.
 *
 * **Example — inspecting metadata after a successful request:**
 * ```kotlin
 * val result = executor.execute(request) { deserialize(it) }
 * if (result is NetworkResult.Success) {
 *     println("Status: ${result.metadata.statusCode}")
 *     println("Duration: ${result.metadata.durationMs}ms")
 *     println("Attempts: ${result.metadata.attemptCount}")
 *     println("Request ID: ${result.metadata.requestId}")
 * }
 * ```
 *
 * @property statusCode   HTTP status code (e.g. 200).
 * @property headers      Response headers as a multi-value map.
 * @property durationMs   Request duration in milliseconds.
 * @property requestId    Server-assigned request ID (from `X-Request-Id` header), if present.
 * @property attemptCount Number of attempts made (1 = no retries).
 */
data class ResponseMetadata(
    val statusCode: Int = 0,
    val headers: Map<String, List<String>> = emptyMap(),
    val durationMs: Long = 0L,
    // Populated by the executor from response headers (e.g. X-Request-Id) when present.
    // TracingObserver can also correlate this with its generated span/trace IDs.
    val requestId: String? = null,
    val attemptCount: Int = 1
) {
    companion object {
        val EMPTY = ResponseMetadata()
    }
}
