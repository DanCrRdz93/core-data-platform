package com.dancr.platform.security.credential

import com.dancr.platform.security.session.RefreshOutcome
import com.dancr.platform.security.session.SessionController
import com.dancr.platform.security.session.SessionState

/**
 * Built-in [CredentialProvider] backed by a [SessionController].
 *
 * Reads the active credential from [SessionController.state] and delegates
 * refresh / invalidation to the controller.
 *
 * **Example — wiring with a [DefaultSessionController][com.dancr.platform.security.session.DefaultSessionController]:**
 * ```kotlin
 * val sessionController = DefaultSessionController(store = mySecretStore)
 * val credentialProvider = DefaultCredentialProvider(sessionController)
 *
 * // Use in an auth interceptor:
 * val authInterceptor = RequestInterceptor { request, context ->
 *     val credential = credentialProvider.current()
 *     if (credential != null) {
 *         val headers = CredentialHeaderMapper.toHeaders(credential)
 *         request.copy(headers = request.headers + headers)
 *     } else request
 * }
 * ```
 *
 * @param sessionController The session controller that owns the credential lifecycle.
 * @see CredentialProvider
 */
class DefaultCredentialProvider(
    private val sessionController: SessionController
) : CredentialProvider {

    /** Returns the credential from the active session, or `null` if idle/expired. */
    override suspend fun current(): Credential? =
        when (val state = sessionController.state.value) {
            is SessionState.Active -> state.credential
            is SessionState.Idle -> null
            is SessionState.Expired -> null
        }

    /** Delegates to [SessionController.refreshSession] and returns the new credential on success. */
    override suspend fun refresh(): Credential? =
        when (val outcome = sessionController.refreshSession()) {
            is RefreshOutcome.Refreshed -> outcome.credential
            is RefreshOutcome.NotNeeded -> current()
            is RefreshOutcome.Failed -> null
        }

    /** Delegates to [SessionController.invalidate] to force-clear the session. */
    override suspend fun invalidate() {
        sessionController.invalidate()
    }
}
