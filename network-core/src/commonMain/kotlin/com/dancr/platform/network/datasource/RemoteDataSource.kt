package com.dancr.platform.network.datasource

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.execution.SafeRequestExecutor
import com.dancr.platform.network.result.NetworkResult

/**
 * Convenience base class for API data sources.
 *
 * Subclass this to expose type-safe API methods while delegating transport
 * concerns to [SafeRequestExecutor].
 *
 * **Example — user API data source:**
 * ```kotlin
 * class UserRemoteDataSource(
 *     executor: SafeRequestExecutor
 * ) : RemoteDataSource(executor) {
 *
 *     suspend fun getUser(id: Int): NetworkResult<User> = execute(
 *         request = HttpRequest(path = "/users/$id", method = HttpMethod.GET),
 *         context = RequestContext(operationId = "getUser-$id"),
 *         deserialize = { response ->
 *             json.decodeFromString(response.body!!.decodeToString())
 *         }
 *     )
 * }
 * ```
 *
 * @param executor The [SafeRequestExecutor] that handles the full request pipeline.
 * @see SafeRequestExecutor
 */
abstract class RemoteDataSource(
    private val executor: SafeRequestExecutor
) {

    /**
     * Delegates to [SafeRequestExecutor.execute] with the given parameters.
     *
     * @param T           The deserialized response type.
     * @param request     The HTTP request to execute.
     * @param context     Optional per-request metadata.
     * @param deserialize Function to convert a [RawResponse][com.dancr.platform.network.client.RawResponse] into [T].
     * @return A [NetworkResult] containing the deserialized data or an error.
     */
    protected suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext? = null,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T> = executor.execute(request, context, deserialize)
}
