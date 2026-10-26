package com.example.catering.catering_test.fake

import com.example.catering.model.response.OrderResponse
import com.example.catering.repository.OrderByIdempotencyKeyFinder

class FakeOrderByIdempotencyKeyFinder(private val order: OrderResponse?) : OrderByIdempotencyKeyFinder {

    override fun findOrderByIdempotencyKey(
        userId: Int, idempotencyKey: String): OrderResponse? {
      return order
    }
}