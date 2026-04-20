package com.dancr.platform.security.credential

/**
 * Supplies the active credential for request authentication.
 *
 * Implementations should read from [SessionController][com.dancr.platform.security.session.SessionController]
 * or [SecretStore][com.dancr.platform.security.store.SecretStore] — never cache stale tokens.
 *
 * **Example — custom implementation:**
 * ```kotlin
 * class MyCredentialProvider(
 *     private val session: SessionController
 * ) : CredentialProvider {
 *
 *     override suspend fun current(): Credential? =
 *         (session.state.value as? SessionState.Active)?.credential
 *
 *     override suspend fun refresh(): Credential? {
 *         val outcome = session.refreshSession()
 *         return (outcome as? RefreshOutcome.Refreshed)?.credential
 *     }
 *
 *     override suspend fun invalidate() {
 *         session.invalidate()
 *     }
 * }
 * ```
 *
 * @see DefaultCredentialProvider for the built-in implementation backed by [SessionController][com.dancr.platform.security.session.SessionController].
 */
interface CredentialProvider {

    /**
     * Returns the active credential, or `null` if no session is active.
     *
     * Called by the auth interceptor before every request.
     * Must be fast — reads from in-memory state, never performs I/O.
     */
    suspend fun current(): Credential?

    /**
     * Proactively refreshes the credential (e.g. before token expiry).
     *
     * @return The new credential on success, `null` if refresh is not possible.
     *         Default: delegates to [current] (no proactive refresh).
     */
    suspend fun refresh(): Credential? = current()

    /**
     * Clears the current credential and forces re-authentication.
     *
     * Called by the auth interceptor on HTTP 401 or by security policies.
     * Default: no-op. Override to delegate to
     * [SessionController.invalidate][com.dancr.platform.security.session.SessionController.invalidate].
     */
    suspend fun invalidate() {}
}
