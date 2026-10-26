package com.example.catering.model.request

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val phone: String,
    val password: String
)
