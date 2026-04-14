package com.dancr.platform.network.result

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
