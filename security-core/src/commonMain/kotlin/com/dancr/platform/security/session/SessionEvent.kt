package com.dancr.platform.security.session

import com.dancr.platform.security.error.SecurityError

sealed class SessionEvent {

    data object Started : SessionEvent()

    data object Refreshed : SessionEvent()

    data object Expired : SessionEvent()

    data object Ended : SessionEvent()

    data class RefreshFailed(val error: SecurityError) : SessionEvent()
}
