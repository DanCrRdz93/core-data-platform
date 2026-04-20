package com.dancr.platform.security.session

import com.dancr.platform.security.credential.Credential

/**
 * Observable authentication session state.
 *
 * Exposed as a `StateFlow<SessionState>` by [SessionController.state].
 * Consumers observe this to drive UI (e.g. show login screen vs. main content).
 *
 * **Example — observing session state in a ViewModel:**
 * ```kotlin
 * sessionController.state.collect { state ->
 *     when (state) {
 *         is SessionState.Idle    -> showLoginScreen()
 *         is SessionState.Active  -> showMainContent(state.credential)
 *         is SessionState.Expired -> showSessionExpiredDialog()
 *     }
 * }
 * ```
 *
 * @see SessionController for the full lifecycle API.
 * @see SessionEvent for one-shot lifecycle events.
 */
sealed interface SessionState {

    /** No session exists. The user is not authenticated. */
    data object Idle : SessionState

    /**
     * The user is authenticated with the given [credential].
     *
     * @property credential The active authentication credential.
     */
    data class Active(val credential: Credential) : SessionState

    /** The session token has expired and needs refresh or re-authentication. */
    data object Expired : SessionState
}
