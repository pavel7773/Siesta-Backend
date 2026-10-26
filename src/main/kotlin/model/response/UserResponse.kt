package com.example.catering.model.response

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: Int,
    val name: String,
    val phone: String,
    val address: String
)