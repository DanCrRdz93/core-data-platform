package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.result.NetworkResult

interface SafeRequestExecutor {

    suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext? = null,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T>
}
