package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse

/**
 * Post-transport interceptor: mutates or inspects a [RawResponse] after the engine
 * returns it but **before** validation / deserialization.
 *
 * Use cases: response caching, header extraction, response transformation.
 *
 * > **Note:** Response logging is handled by
 * > [LoggingObserver][com.dancr.platform.network.observability.LoggingObserver] at the
 * > observer level, not via a ResponseInterceptor. This avoids duplicating logging concerns.
 *
 * **Example — extracting rate-limit headers:**
 * ```kotlin
 * val rateLimitInterceptor = ResponseInterceptor { response, request, context ->
 *     val remaining = response.headers["X-RateLimit-Remaining"]?.firstOrNull()
 *     if (remaining != null) {
 *         rateLimitTracker.update(remaining.toInt())
 *     }
 *     response // pass through unchanged
 * }
 * ```
 *
 * @see RequestInterceptor for pre-transport interception.
 * @see DefaultSafeRequestExecutor for where interceptors are applied.
 */
fun interface ResponseInterceptor {

    /**
     * Intercepts and optionally transforms the [response].
     *
     * @param response The raw HTTP response from the engine.
     * @param request  The original HTTP request (for correlation).
     * @param context  Optional per-request metadata.
     * @return The (possibly modified) [RawResponse].
     */
    suspend fun intercept(
        response: RawResponse,
        request: HttpRequest,
        context: RequestContext?
    ): RawResponse
}
