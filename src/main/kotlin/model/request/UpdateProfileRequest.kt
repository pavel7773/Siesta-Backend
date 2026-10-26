package com.example.catering.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val address: String? = null
)