package com.dancr.platform.sample.model

/**
 * Domain model representing a user.
 *
 * Produced by [UserMapper][com.dancr.platform.sample.mapper.UserMapper] from [UserDto][com.dancr.platform.sample.dto.UserDto].
 *
 * @property id          Unique user identifier.
 * @property displayName User's full name.
 * @property handle      Login handle / username.
 * @property email       Email address.
 * @property company     Company name, or `null` if not available.
 */
data class User(
    val id: Long,
    val displayName: String,
    val handle: String,
    val email: String,
    val company: String?
)
