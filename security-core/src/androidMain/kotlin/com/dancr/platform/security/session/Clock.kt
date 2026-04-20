package com.dancr.platform.security.session

/** Android implementation: delegates to [System.currentTimeMillis]. */
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
