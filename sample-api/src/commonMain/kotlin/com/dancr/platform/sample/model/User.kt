package com.dancr.platform.sample.model

data class User(
    val id: Long,
    val displayName: String,
    val handle: String,
    val email: String,
    val company: String?
)
