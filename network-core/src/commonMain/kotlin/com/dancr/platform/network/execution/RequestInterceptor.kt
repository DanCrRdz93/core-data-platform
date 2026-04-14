package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest

fun interface RequestInterceptor {

    suspend fun intercept(request: HttpRequest, context: RequestContext?): HttpRequest
}
