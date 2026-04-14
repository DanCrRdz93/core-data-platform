package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse

// Extension point for post-transport processing.
// Use cases: response caching, header extraction, response transformation.
//
// Note: Response logging is handled by LoggingObserver at the observer level,
// not via a ResponseInterceptor. This avoids duplicating logging concerns.
//
// Future: CachingResponseInterceptor for conditional caching based on Cache-Control headers.
fun interface ResponseInterceptor {

    suspend fun intercept(
        response: RawResponse,
        request: HttpRequest,
        context: RequestContext?
    ): RawResponse
}
