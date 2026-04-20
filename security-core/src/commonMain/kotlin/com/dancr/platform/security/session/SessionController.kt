package com.dancr.platform.security.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the authentication session lifecycle.
 *
 * Exposes observable [state] and one-shot [events] so consumers can react to
 * session transitions (e.g. show login screen, track analytics, refresh tokens).
 *
 * **Example — typical login / logout flow:**
 * ```kotlin
 * val controller: SessionController = DefaultSessionController(
 *     store = mySecretStore,
 *     refreshTokenProvider = { refreshToken ->
 *         api.refreshToken(refreshToken)?.let { response ->
 *             SessionCredentials(
 *                 credential = Credential.Bearer(response.accessToken),
 *                 refreshToken = response.refreshToken,
 *                 expiresAtMs = response.expiresAtMs
 *             )
 *         }
 *     }
 * )
 *
 * // Login
 * controller.startSession(
 *     SessionCredentials(credential = Credential.Bearer(token = "eyJ..."))
 * )
 *
 * // Check authentication
 * if (controller.isAuthenticated) { /* … */ }
 *
 * // Refresh
 * val outcome = controller.refreshSession()
 *
 * // Logout (user-initiated)
 * controller.endSession()
 *
 * // Force-logout (e.g. 401 interceptor)
 * controller.invalidate()
 * ```
 *
 * @see DefaultSessionController for the built-in implementation.
 * @see SessionState for the observable state model.
 * @see SessionEvent for one-shot lifecycle events.
 */
interface SessionController {

    /** Observable session state. Always reflects the latest lifecycle transition. */
    val state: StateFlow<SessionState>

    /** Stream of one-shot lifecycle events (started, refreshed, ended, etc.). */
    val events: Flow<SessionEvent>

    /**
     * Convenience property: `true` when [state] is [SessionState.Active].
     * Derived from [state] — never stored separately.
     */
    val isAuthenticated: Boolean
        get() = state.value is SessionState.Active

    /**
     * Persists [credentials], transitions state to [SessionState.Active],
     * and emits [SessionEvent.Started].
     *
     * @param credentials The credential bundle to persist and activate.
     */
    suspend fun startSession(credentials: SessionCredentials)

    /**
     * Attempts to refresh the session credential.
     *
     * @return A sealed [RefreshOutcome]:
     *   - [RefreshOutcome.Refreshed] — new credential obtained, session is Active.
     *   - [RefreshOutcome.NotNeeded] — preconditions not met (no token/provider). State unchanged.
     *   - [RefreshOutcome.Failed] — refresh attempted but failed. Session transitions to Expired.
     */
    suspend fun refreshSession(): RefreshOutcome

    /**
     * Graceful logout. Clears persisted credentials, transitions to [SessionState.Idle],
     * and emits [SessionEvent.Ended].
     */
    suspend fun endSession()

    /**
     * Force-logout from any layer. Clears persisted credentials, transitions to
     * [SessionState.Idle], and emits [SessionEvent.Invalidated].
     *
     * Use for HTTP 401 responses or security-triggered invalidation.
     */
    suspend fun invalidate()
}
