package com.dancr.platform.sample.mapper

import com.dancr.platform.sample.dto.UserDto
import com.dancr.platform.sample.model.User

object UserMapper {

    fun toDomain(dto: UserDto): User = User(
        id = dto.id,
        displayName = dto.name,
        handle = dto.username,
        email = dto.email,
        company = dto.companyDto?.name
    )

    fun toDomain(dtos: List<UserDto>): List<User> = dtos.map(::toDomain)
}
