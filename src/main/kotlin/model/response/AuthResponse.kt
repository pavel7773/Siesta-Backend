package com.example.catering.model.response

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val userResponse: UserResponse,
    val token: String
)