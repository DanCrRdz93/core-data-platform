package com.dancr.platform.security.session

import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.error.SecurityError

// Result of a session refresh attempt.
//
// Semantics:
//   Refreshed  — A new credential was obtained and the session is now active.
//   NotNeeded  — No refresh was attempted because the preconditions were not met
//                (e.g. no refresh token stored, no provider configured, or session
//                is already idle). The current session state is unchanged.
//   Failed     — The refresh was attempted but failed. The session transitions to Expired.
sealed class RefreshOutcome {

    data class Refreshed(val credential: Credential) : RefreshOutcome()

    data class NotNeeded(val reason: String) : RefreshOutcome()

    data class Failed(val error: SecurityError) : RefreshOutcome()
}
