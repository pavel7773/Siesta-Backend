package com.example.catering.model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderItemRequest (
    val dishId: Int,
    val quantity: Int
)
