package com.dancr.platform.security.session

import com.dancr.platform.security.error.SecurityError

/**
 * One-shot lifecycle events emitted by [SessionController.events].
 *
 * Unlike [SessionState] (which is a persistent snapshot), events fire exactly once
 * per lifecycle transition — ideal for triggering side effects (analytics, navigation,
 * toast messages).
 *
 * **Example — reacting to session events:**
 * ```kotlin
 * sessionController.events.collect { event ->
 *     when (event) {
 *         SessionEvent.Started      -> analytics.track("session_started")
 *         SessionEvent.Refreshed    -> analytics.track("token_refreshed")
 *         SessionEvent.Expired      -> analytics.track("session_expired")
 *         SessionEvent.Ended        -> navigateToLogin()
 *         SessionEvent.Invalidated  -> showForcedLogoutBanner()
 *         is SessionEvent.RefreshFailed -> logError(event.error)
 *     }
 * }
 * ```
 *
 * @see SessionController.events
 * @see SessionState
 */
sealed class SessionEvent {

    /** A new session was established via [SessionController.startSession]. */
    data object Started : SessionEvent()

    /** The session credential was successfully refreshed. */
    data object Refreshed : SessionEvent()

    /** The session token expired (state transitioned to [SessionState.Expired]). */
    data object Expired : SessionEvent()

    /** Graceful logout — user-initiated via [SessionController.endSession]. */
    data object Ended : SessionEvent()

    /** Force-logout — triggered externally (e.g. 401, security policy) via [SessionController.invalidate]. */
    data object Invalidated : SessionEvent()

    /**
     * Token refresh was attempted but failed.
     *
     * @property error The [SecurityError] describing the failure.
     */
    data class RefreshFailed(val error: SecurityError) : SessionEvent()
}
