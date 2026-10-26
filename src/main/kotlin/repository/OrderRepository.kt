package com.example.catering.repository

import com.example.catering.database.table.OrderItemsTable
import com.example.catering.database.table.OrdersTable
import com.example.catering.model.request.CreateOrderRequest
import com.example.catering.model.response.OrderItemResponse
import com.example.catering.model.response.OrderResponse
import com.example.catering.model.status.OrderStatus
import com.example.catering.utils.normalizePhone
import com.example.catering.validation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnoreAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class OrderRepository(
    private val orderFinder: OrderByIdempotencyKeyFinder
) {

    suspend fun createOrder(
        userId: Int,
        request: CreateOrderRequest,
        idempotencyKey: String
    ): OrderResponse {

        val existingOrder =
            withContext(Dispatchers.IO) {
                transaction {
                    orderFinder.findOrderByIdempotencyKey(
                        userId = userId,
                        idempotencyKey = idempotencyKey
                    )
                }
            }

        validateOrderRequest(request)

        validateOrderItemsRequest(request)

        validateOrderCommentRequest(request)
        val normalizedComment = request.comment.trim()

        validateOrderPhoneRequest(request)
        val normalizedPhone = normalizePhone(request.phone)

        validateOrderNameRequest(request)
        val normalizedName = request.name.trim()

        val normalizedDeliveryType =
            validateOrderDeliveryTypeRequest(request)

        validateOrderAddressRequest(request, normalizedDeliveryType)
        val normalizedAddress = request.address.trim()

        val dishes = validateOrderDishesRequest(
            request = request,
            menuDishFinder = MenuDishRepository()
        )

        val totalAmount = validateCalculateOrderTotal(
            dishes = dishes,
            request = request
        )


        if (existingOrder != null) {
            return existingOrder
        }

        val createdAt = System.currentTimeMillis()
        val statusOrder = OrderStatus.ACCEPTED

        return withContext(Dispatchers.IO) {
            transaction {

                val newOrderId =
                    OrdersTable.insertIgnoreAndGetId {

                        it[OrdersTable.userId] = userId

                        it[OrdersTable.idempotencyKey] =
                            idempotencyKey

                        it[OrdersTable.name] =
                            normalizedName

                        it[OrdersTable.phone] =
                            normalizedPhone

                        it[OrdersTable.comment] =
                            normalizedComment

                        it[OrdersTable.address] =
                            normalizedAddress

                        it[OrdersTable.deliveryType] =
                            normalizedDeliveryType

                        it[OrdersTable.totalAmount] =
                            totalAmount

                        it[OrdersTable.createdAt] =
                            createdAt

                        it[OrdersTable.statusOrder] =
                            statusOrder.name
                    }

                if (newOrderId == null) {
                    return@transaction findExistingOrderOrThrow(
                        userId, idempotencyKey, orderFinder)

                }

                dishes
                    .zip(request.orderItems)
                    .forEach { (dish, item) ->

                        OrderItemsTable.insert {

                            it[OrderItemsTable.orderId] =
                                newOrderId.value

                            it[OrderItemsTable.name] =
                                dish.name

                            it[OrderItemsTable.price] =
                                dish.price

                            it[OrderItemsTable.dishId] =
                                item.dishId

                            it[OrderItemsTable.quantity] =
                                item.quantity
                        }
                    }

                val responseItems =
                    dishes.zip(request.orderItems)
                        .map { (dish, item) ->
                            OrderItemResponse(
                                dishId = item.dishId,
                                name = dish.name,
                                price = dish.price,
                                quantity = item.quantity
                            )
                        }

                OrderResponse(
                    id = newOrderId.value,
                    name = normalizedName,
                    phone = normalizedPhone,
                    comment = normalizedComment,
                    deliveryType = normalizedDeliveryType,
                    address = normalizedAddress,
                    products = responseItems,
                    totalAmount = totalAmount,
                    createdAt = createdAt,
                    statusOrder = statusOrder
                )
            }
        }
    }

    suspend fun getOrdersByUserId(
        userId: Int
    ): List<OrderResponse> =
        withContext(Dispatchers.IO) {
            transaction {

                val orderRows = OrdersTable
                    .selectAll()
                    .where {
                        OrdersTable.userId eq userId
                    }
                    .orderBy(
                        OrdersTable.createdAt,
                        SortOrder.DESC
                    )
                    .toList()

                if (orderRows.isEmpty()) {
                    return@transaction emptyList()
                }

                val orderIds = orderRows.map {
                    it[OrdersTable.id].value
                }

                val itemsByOrderId = OrderItemsTable
                    .selectAll()
                    .where {
                        OrderItemsTable.orderId inList orderIds
                    }
                    .map { itemRow ->

                        itemRow[OrderItemsTable.orderId] to
                                OrderItemResponse(
                                    dishId =
                                        itemRow[
                                            OrderItemsTable.dishId
                                        ],
                                    name =
                                        itemRow[
                                            OrderItemsTable.name
                                        ],
                                    price =
                                        itemRow[
                                            OrderItemsTable.price
                                        ],
                                    quantity =
                                        itemRow[
                                            OrderItemsTable.quantity
                                        ]
                                )
                    }
                    .groupBy(
                        keySelector = { it.first },
                        valueTransform = { it.second }
                    )

                orderRows.map { orderRow ->

                    val orderId =
                        orderRow[OrdersTable.id].value

                    OrderResponse(
                        id = orderId,
                        name =
                            orderRow[OrdersTable.name],
                        phone =
                            orderRow[OrdersTable.phone],
                        comment =
                            orderRow[OrdersTable.comment],
                        deliveryType =
                            orderRow[
                                OrdersTable.deliveryType
                            ],
                        address =
                            orderRow[OrdersTable.address],
                        products =
                            itemsByOrderId[orderId]
                                .orEmpty(),
                        totalAmount =
                            orderRow[
                                OrdersTable.totalAmount
                            ],
                        createdAt =
                            orderRow[
                                OrdersTable.createdAt
                            ],
                        statusOrder =
                            OrderStatus.valueOf(
                                orderRow[
                                    OrdersTable.statusOrder
                                ]
                            )
                    )
                }
            }
        }

    suspend fun cancelOrder(
        userId: Int,
        orderId: Int
    ): OrderStatus =
        withContext(Dispatchers.IO) {
            transaction {

                val orderRow = OrdersTable
                    .selectAll()
                    .where {
                        (OrdersTable.id eq orderId) and
                                (OrdersTable.userId eq userId)
                    }
                    .singleOrNull()
                    ?: throw IllegalArgumentException(
                        "Заказ не найден"
                    )

                val currentStatus =
                    OrderStatus.valueOf(
                        orderRow[OrdersTable.statusOrder]
                    )

                when (currentStatus) {

                    OrderStatus.ACCEPTED -> {

                        val updatedRows =
                            OrdersTable.update({
                                (OrdersTable.id eq orderId) and
                                        (OrdersTable.userId eq userId) and
                                        (
                                                OrdersTable.statusOrder eq
                                                        OrderStatus.ACCEPTED.name
                                                )
                            }) {
                                it[OrdersTable.statusOrder] =
                                    OrderStatus.CANCELED.name
                            }

                        if (updatedRows == 0) {
                            throw IllegalStateException(
                                "Статус заказа изменился. Повторите запрос"
                            )
                        }

                        OrderStatus.CANCELED
                    }

                    OrderStatus.CANCELED ->
                        throw IllegalStateException(
                            "Заказ уже отменён"
                        )

                    OrderStatus.COOKING,
                    OrderStatus.READY,
                    OrderStatus.COMPLETED ->
                        throw IllegalStateException(
                            "Заказ уже нельзя отменить"
                        )
                }
            }
        }

}