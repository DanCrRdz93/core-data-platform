package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse

// Extension point for post-transport processing.
// Use cases: response logging, metrics collection, response caching, header extraction.
// TODO: Implement LoggingResponseInterceptor for centralized response logging with LogSanitizer.
// TODO: Implement CachingResponseInterceptor for conditional caching based on Cache-Control headers.
fun interface ResponseInterceptor {

    suspend fun intercept(
        response: RawResponse,
        request: HttpRequest,
        context: RequestContext?
    ): RawResponse
}
