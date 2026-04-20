package com.dancr.platform.security.session

import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.error.SecurityError

/**
 * Result of a [SessionController.refreshSession] attempt.
 *
 * **Example — handling refresh outcomes:**
 * ```kotlin
 * when (val outcome = sessionController.refreshSession()) {
 *     is RefreshOutcome.Refreshed -> useNewToken(outcome.credential)
 *     is RefreshOutcome.NotNeeded -> log("Skipped: ${outcome.reason}")
 *     is RefreshOutcome.Failed    -> handleError(outcome.error)
 * }
 * ```
 *
 * @see SessionController.refreshSession
 */
sealed class RefreshOutcome {

    /**
     * A new credential was obtained and the session is now [SessionState.Active].
     *
     * @property credential The freshly obtained credential.
     */
    data class Refreshed(val credential: Credential) : RefreshOutcome()

    /**
     * No refresh was attempted because preconditions were not met
     * (e.g. no refresh token stored, no provider configured, or session is already idle).
     * The current session state is unchanged.
     *
     * @property reason Human-readable explanation of why the refresh was skipped.
     */
    data class NotNeeded(val reason: String) : RefreshOutcome()

    /**
     * The refresh was attempted but failed. The session transitions to [SessionState.Expired].
     *
     * @property error The [SecurityError] describing the failure.
     */
    data class Failed(val error: SecurityError) : RefreshOutcome()
}
