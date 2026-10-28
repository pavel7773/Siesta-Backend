package com.example.catering.model.response

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationPendingResponse(
    val phone: String,
    val codeSent: Boolean
)