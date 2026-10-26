package com.example.catering.repository

import com.example.catering.model.response.OrderResponse

interface OrderByIdempotencyKeyFinder {
    fun findOrderByIdempotencyKey(
        userId: Int,
        idempotencyKey: String
    ): OrderResponse?
}