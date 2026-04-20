package com.dancr.platform.sample.mapper

import com.dancr.platform.sample.dto.UserDto
import com.dancr.platform.sample.model.User

/**
 * Maps [UserDto] network DTOs to [User] domain models.
 *
 * **Example:**
 * ```kotlin
 * val user: User = UserMapper.toDomain(userDto)
 * val users: List<User> = UserMapper.toDomain(userDtos)
 * ```
 */
object UserMapper {

    /** Maps a single [UserDto] to a [User] domain model. */
    fun toDomain(dto: UserDto): User = User(
        id = dto.id,
        displayName = dto.name,
        handle = dto.username,
        email = dto.email,
        company = dto.companyDto?.name
    )

    /** Maps a list of [UserDto]s to [User] domain models. */
    fun toDomain(dtos: List<UserDto>): List<User> = dtos.map(::toDomain)
}
