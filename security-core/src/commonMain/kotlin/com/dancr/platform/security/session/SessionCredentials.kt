package com.dancr.platform.security.session

import com.dancr.platform.security.credential.Credential

data class SessionCredentials(
    val credential: Credential,
    val refreshToken: String? = null,
    val expiresAtMs: Long? = null
)
