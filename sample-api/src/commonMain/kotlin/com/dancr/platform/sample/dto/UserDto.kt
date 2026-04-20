package com.dancr.platform.sample.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Network-layer DTO representing a user from the JSONPlaceholder API.
 *
 * Mapped to [User][com.dancr.platform.sample.model.User] via [UserMapper][com.dancr.platform.sample.mapper.UserMapper].
 *
 * @property id         Unique user identifier.
 * @property name       Full name.
 * @property username   Login handle.
 * @property email      Email address.
 * @property phone      Phone number (optional).
 * @property website    Website URL (optional).
 * @property companyDto Nested company information (optional).
 */
@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val username: String,
    val email: String,
    val phone: String? = null,
    val website: String? = null,
    @SerialName("company") val companyDto: CompanyDto? = null
)

/**
 * Nested company information inside [UserDto].
 *
 * @property name        Company name.
 * @property catchPhrase Company catch phrase (optional).
 */
@Serializable
data class CompanyDto(
    val name: String,
    @SerialName("catchPhrase") val catchPhrase: String? = null
)
