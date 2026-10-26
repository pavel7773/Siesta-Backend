package com.example.catering.model.response

import com.example.catering.model.status.OrderStatus
import kotlinx.serialization.Serializable

@Serializable
data class OrderResponse (
    val id: Int,
    val name: String,
    val phone: String,
    val comment: String,
    val deliveryType: String,
    val address: String,
    val products: List<OrderItemResponse>,
    val totalAmount: Long,
    val createdAt: Long,
    val statusOrder: OrderStatus
)
