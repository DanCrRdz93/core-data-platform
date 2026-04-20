package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest

/**
 * Intercepts and optionally transforms an [HttpRequest] before it reaches the [HttpEngine][com.dancr.platform.network.client.HttpEngine].
 *
 * Typical use cases: injecting authentication headers, adding tracing headers,
 * appending default query parameters.
 *
 * **Example — authentication interceptor:**
 * ```kotlin
 * val authInterceptor = RequestInterceptor { request, context ->
 *     val credential = credentialProvider.current()
 *     if (credential != null && context?.requiresAuth == true) {
 *         val headers = CredentialHeaderMapper.toHeaders(credential)
 *         request.copy(headers = request.headers + headers)
 *     } else request
 * }
 * ```
 *
 * **Example — tracing header interceptor:**
 * ```kotlin
 * val tracingInterceptor = RequestInterceptor { request, context ->
 *     val traceId = context?.parentSpanId ?: generateTraceId()
 *     request.copy(headers = request.headers + ("X-Trace-Id" to traceId))
 * }
 * ```
 *
 * @see ResponseInterceptor for post-transport interception.
 * @see DefaultSafeRequestExecutor for where interceptors are applied.
 */
fun interface RequestInterceptor {

    /**
     * Intercepts and optionally transforms the [request].
     *
     * @param request The outgoing HTTP request (may already include default headers).
     * @param context Optional per-request metadata (operation ID, tags, auth flag).
     * @return The (possibly modified) [HttpRequest] to send.
     */
    suspend fun intercept(request: HttpRequest, context: RequestContext?): HttpRequest
}
