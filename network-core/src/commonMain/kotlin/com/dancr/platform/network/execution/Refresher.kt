package com.dancr.platform.network.execution

/**
 * Hook used by [RefreshingSafeRequestExecutor] to recover from
 * [com.dancr.platform.network.result.NetworkError.Authentication] failures.
 *
 * The implementation typically delegates to
 * `security-core`'s `CredentialProvider.refresh()` (or your own auth client),
 * but the interface stays generic so `network-core` does not depend on
 * `security-core`.
 *
 * Contract: return `true` if the refresh succeeded and the original request
 * is now safe to retry with the new credential. Return `false` if the
 * refresh failed (refresh token missing / expired / network error) — in
 * that case the executor leaves the original `Authentication` failure
 * untouched and propagates it to the caller.
 *
 * **Example — bridging from `CredentialProvider`:**
 * ```kotlin
 * val refresher = Refresher { credentialProvider.refresh() != null }
 * val executor  = RefreshingSafeRequestExecutor(base, refresher)
 * ```
 */
fun interface Refresher {
    suspend fun refresh(): Boolean
}
