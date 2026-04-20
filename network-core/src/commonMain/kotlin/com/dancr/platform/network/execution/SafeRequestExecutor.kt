package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.result.NetworkResult

/**
 * Managed execution layer that wraps [HttpEngine][com.dancr.platform.network.client.HttpEngine]
 * with interceptors, validation, retry logic, error classification, and observability.
 *
 * Every request returns a [NetworkResult] — never throws.
 *
 * **Example — executing a typed request:**
 * ```kotlin
 * val result: NetworkResult<User> = executor.execute(
 *     request = HttpRequest(path = "/users/42", method = HttpMethod.GET),
 *     context = RequestContext(operationId = "getUser"),
 *     deserialize = { response ->
 *         json.decodeFromString(response.body!!.decodeToString())
 *     }
 * )
 *
 * when (result) {
 *     is NetworkResult.Success -> showUser(result.data)
 *     is NetworkResult.Failure -> handleError(result.error)
 * }
 * ```
 *
 * @see DefaultSafeRequestExecutor for the built-in implementation.
 * @see RemoteDataSource for a convenience base class that delegates to this executor.
 */
interface SafeRequestExecutor {

    /**
     * Executes the [request] through the full pipeline and returns a typed [NetworkResult].
     *
     * @param T           The deserialized response type.
     * @param request     The HTTP request to execute.
     * @param context     Optional per-request metadata (tracing, retry override, auth flag).
     * @param deserialize Function to convert a [RawResponse] into [T].
     * @return [NetworkResult.Success] with deserialized data, or [NetworkResult.Failure] with a typed error.
     */
    suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext? = null,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T>
}
