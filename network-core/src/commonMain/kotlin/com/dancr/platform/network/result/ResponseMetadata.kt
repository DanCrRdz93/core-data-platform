package com.dancr.platform.network.result

data class ResponseMetadata(
    val statusCode: Int = 0,
    val headers: Map<String, List<String>> = emptyMap(),
    val durationMs: Long = 0L,
    // TODO: Generate via TracingObserver or extract from response headers (e.g. X-Request-Id).
    val requestId: String? = null,
    val attemptCount: Int = 1
) {
    companion object {
        val EMPTY = ResponseMetadata()
    }
}
