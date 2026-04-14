package com.dancr.platform.security.session

import com.dancr.platform.security.error.SecurityError

sealed class SessionEvent {

    data object Started : SessionEvent()

    data object Refreshed : SessionEvent()

    data object Expired : SessionEvent()

    // Graceful logout — user-initiated via endSession().
    data object Ended : SessionEvent()

    // Force-logout — triggered externally (e.g. 401, security policy) via invalidate().
    data object Invalidated : SessionEvent()

    data class RefreshFailed(val error: SecurityError) : SessionEvent()
}
