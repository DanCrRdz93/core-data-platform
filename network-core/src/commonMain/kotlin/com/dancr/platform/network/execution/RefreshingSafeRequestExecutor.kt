package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.result.NetworkError
import com.dancr.platform.network.result.NetworkResult

/**
 * Decorator over a [SafeRequestExecutor] that transparently recovers from
 * one [NetworkError.Authentication] (HTTP 401) per request by invoking a
 * [Refresher] and retrying the call.
 *
 * Typical wiring (Koin):
 * ```kotlin
 * single<SafeRequestExecutor>(named("auth")) {
 *     val base = DefaultSafeRequestExecutor(
 *         engine = get(),
 *         config = get(named("auth")),
 *         interceptors = listOf(authInterceptor),
 *     )
 *     val refresher = Refresher { credentialProvider.refresh() != null }
 *     RefreshingSafeRequestExecutor(base, refresher)
 * }
 * ```
 *
 * Behavior:
 * - The first attempt always runs through the [delegate].
 * - If it returns [NetworkResult.Failure] with [NetworkError.Authentication]
 *   AND the [refresher] returns `true`, the request is re-executed once.
 * - The retry result is returned as-is — even if it's another 401 — so
 *   no infinite loops are possible.
 * - Successful first attempts and non-Authentication failures pass through
 *   untouched.
 *
 * Important: the [refresher] must NOT use this same executor — otherwise a
 * failing refresh would recursively trigger another refresh attempt. Wire
 * the refresh request through a separate, no-auth executor instance.
 *
 * @param delegate  The underlying executor (typically a base
 *                  [DefaultSafeRequestExecutor], optionally wrapped in
 *                  other decorators like a circuit breaker).
 * @param refresher Hook that performs the credential refresh.
 */
class RefreshingSafeRequestExecutor(
    private val delegate: SafeRequestExecutor,
    private val refresher: Refresher,
) : SafeRequestExecutor {

    override suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext?,
        deserialize: (RawResponse) -> T,
    ): NetworkResult<T> {
        val first = delegate.execute(request, context, deserialize)
        if (first is NetworkResult.Failure && first.error is NetworkError.Authentication) {
            if (refresher.refresh()) {
                return delegate.execute(request, context, deserialize)
            }
        }
        return first
    }
}
