package com.dancr.platform.network.datasource

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.execution.SafeRequestExecutor
import com.dancr.platform.network.result.NetworkResult

abstract class RemoteDataSource(
    private val executor: SafeRequestExecutor
) {

    protected suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext? = null,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T> = executor.execute(request, context, deserialize)
}
