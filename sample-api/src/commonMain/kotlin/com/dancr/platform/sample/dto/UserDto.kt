package com.dancr.platform.sample.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class CompanyDto(
    val name: String,
    @SerialName("catchPhrase") val catchPhrase: String? = null
)
