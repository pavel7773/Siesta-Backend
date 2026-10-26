package com.example.catering.repository

import com.example.catering.database.table.OrderItemsTable
import com.example.catering.database.table.OrdersTable
import com.example.catering.model.response.OrderItemResponse
import com.example.catering.model.response.OrderResponse
import com.example.catering.model.status.OrderStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class DatabaseOrderByIdempotencyKeyFinder: OrderByIdempotencyKeyFinder {

   override fun findOrderByIdempotencyKey(
        userId: Int,
        idempotencyKey: String
    ): OrderResponse? {

        val orderRow = OrdersTable
            .selectAll()
            .where {
                (OrdersTable.userId eq userId) and
                        (OrdersTable.idempotencyKey eq
                                idempotencyKey)
            }
            .singleOrNull()
            ?: return null

        val orderId =
            orderRow[OrdersTable.id].value

        val items = OrderItemsTable
            .selectAll()
            .where {
                OrderItemsTable.orderId eq orderId
            }
            .map { itemRow ->

                OrderItemResponse(
                    dishId =
                        itemRow[OrderItemsTable.dishId],
                    name =
                        itemRow[OrderItemsTable.name],
                    price =
                        itemRow[OrderItemsTable.price],
                    quantity =
                        itemRow[OrderItemsTable.quantity]
                )
            }

        return OrderResponse(
            id = orderId,
            name =
                orderRow[OrdersTable.name],
            phone =
                orderRow[OrdersTable.phone],
            comment =
                orderRow[OrdersTable.comment],
            deliveryType =
                orderRow[OrdersTable.deliveryType],
            address =
                orderRow[OrdersTable.address],
            products = items,
            totalAmount =
                orderRow[OrdersTable.totalAmount],
            createdAt =
                orderRow[OrdersTable.createdAt],
            statusOrder =
                OrderStatus.valueOf(
                    orderRow[OrdersTable.statusOrder]
                )
        )
    }
}