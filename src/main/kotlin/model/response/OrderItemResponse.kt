package com.example.catering.model.response

import kotlinx.serialization.Serializable

@Serializable
data class OrderItemResponse (
    val dishId: Int,
    val name: String,
    val price: Int,
    val quantity: Int
)