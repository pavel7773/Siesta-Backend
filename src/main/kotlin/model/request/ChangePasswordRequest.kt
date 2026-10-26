
package com.example.catering.model.request

import kotlinx.serialization.Serializable

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
