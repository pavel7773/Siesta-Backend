package com.example.catering.model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderRequest (
    val orderItems: List<CreateOrderItemRequest>,
    val name: String,
    val phone: String,
    val comment: String,
    val deliveryType: String,
    val address: String,
    val promoCode: String? = null
)
