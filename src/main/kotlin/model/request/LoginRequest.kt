package com.example.catering.model.request

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String
)