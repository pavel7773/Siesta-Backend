package com.example.catering.catering_test.database

import com.example.catering.database.table.MenuDishesTable
import com.example.catering.database.table.OrderItemsTable
import com.example.catering.database.table.OrdersTable
import com.example.catering.database.table.UsersTable
import com.example.catering.model.request.CreateOrderItemRequest
import com.example.catering.model.request.CreateOrderRequest
import com.example.catering.model.status.OrderStatus
import com.example.catering.repository.DatabaseOrderByIdempotencyKeyFinder
import com.example.catering.repository.OrderRepository
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseIntegrationTest {

    @Test
    fun testDatabaseConnection() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        val result = transaction {
            exec("SELECT 1") { resultSet ->
                if (resultSet.next()) {
                    resultSet.getInt(1)
                } else {
                    null
                }
            }
        }
        assertEquals(1, result)
    }

    @Test
    fun getOrdersByUserIdReturnsEmptyList() {
        runBlocking {

            TestDatabaseFactory.migrate()
            TestDatabaseFactory.init()

            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
            }

            val orderRepository = OrderRepository(
                orderFinder = DatabaseOrderByIdempotencyKeyFinder()
            )

            val result = orderRepository.getOrdersByUserId(1)

            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun getOrdersByUserIdReturnsExistingOrder() {

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000001"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }
        val orderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.name] = "Test User"
                it[OrdersTable.phone] = "79990000001"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 500L
                it[OrdersTable.createdAt] = 1000L
                it[OrdersTable.statusOrder] = "ACCEPTED"
            }.value
        }
        transaction {
            OrderItemsTable.insert {
                it[OrderItemsTable.orderId] = orderId
                it[OrderItemsTable.dishId] = 1
                it[OrderItemsTable.name] = "Тестовое блюдо"
                it[OrderItemsTable.quantity] = 2
                it[OrderItemsTable.price] = 250
            }
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )

        val result = runBlocking {
            orderRepository.getOrdersByUserId(userId)
        }
        val order = result.first()

        assertEquals(orderId, order.id)
        assertEquals(1, order.products.size)
        val product = order.products.first()

        assertEquals(1, product.dishId)
        assertEquals("Тестовое блюдо", product.name)
        assertEquals(2, product.quantity)
        assertEquals(250, product.price)

    }

    @Test
    fun getOrdersByUserIdReturnsNewestOrderFirst() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000001"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }

        val firstOrderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.name] = "First Order"
                it[OrdersTable.phone] = "79990000001"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 100L
                it[OrdersTable.createdAt] = 1000L
                it[OrdersTable.statusOrder] = "ACCEPTED"
            }.value
        }

        val secondOrderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.name] = "Second Order"
                it[OrdersTable.phone] = "79990000001"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 200L
                it[OrdersTable.createdAt] = 2000L
                it[OrdersTable.statusOrder] = "ACCEPTED"
            }.value
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )

        val result = runBlocking {
            orderRepository.getOrdersByUserId(userId)
        }
        assertEquals(2, result.size)

        assertEquals(secondOrderId, result[0].id)
        assertEquals(firstOrderId, result[1].id)
    }


    @Test
    fun getOrdersByUserIdReturnsOnlyUsersOrders() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val firstUserId = transaction {
            UsersTable.insert {
                it[name] = "First User"
                it[phone] = "79188574377"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }

        val secondUserId = transaction {
            UsersTable.insert {
                it[name] = "Second User"
                it[phone] = "79990000002"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }

        val firstOrderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = firstUserId
                it[OrdersTable.name] = "First Order"
                it[OrdersTable.phone] = "79188574377"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 100L
                it[OrdersTable.createdAt] = 1000L
                it[OrdersTable.statusOrder] = "ACCEPTED"
            }.value
        }

        val secondOrderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = secondUserId
                it[OrdersTable.name] = "Second Order"
                it[OrdersTable.phone] = "79990000002"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 200L
                it[OrdersTable.createdAt] = 2000L
                it[OrdersTable.statusOrder] = "ACCEPTED"
            }.value
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )

        val result = runBlocking {
            orderRepository.getOrdersByUserId(firstUserId)
        }
        assertEquals(1, result.size)
        assertEquals(firstOrderId, result.first().id)
        assertTrue(result.none { it.id == secondOrderId })
    }

    @Test
    fun getOrdersByUserIdReturnsOrderWithEmptyProducts() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79188574377"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }

        val orderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.name] = "Second Order"
                it[OrdersTable.phone] = "79990000003"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 200L
                it[OrdersTable.createdAt] = 2000L
                it[OrdersTable.statusOrder] = "ACCEPTED"
            }.value
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )

        val result = runBlocking {
            orderRepository.getOrdersByUserId(userId)
        }
        assertEquals(1, result.size)
        assertEquals(orderId, result.first().id)
        assertTrue(result.first().products.isEmpty())
    }

    @Test
    fun cancelAcceptedOrderChangesStatusToCanceled() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79188574377"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }
        val orderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.name] = "Second Order"
                it[OrdersTable.phone] = "79990000003"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 200L
                it[OrdersTable.createdAt] = 2000L
                it[OrdersTable.statusOrder] = OrderStatus.ACCEPTED.name
            }.value
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )

        val result = runBlocking {
            orderRepository.cancelOrder(
                userId = userId,
                orderId = orderId
            )
        }
        assertEquals(
            OrderStatus.CANCELED,
            result
        )
        val statusInDatabase = transaction {
            OrdersTable
                .selectAll()
                .where { OrdersTable.id eq orderId }
                .single()[OrdersTable.statusOrder]
        }
        assertEquals(
            OrderStatus.CANCELED.name,
            statusInDatabase
        )
    }

    @Test
    fun cancelOrderThrowsWhenOrderNotFound() {

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79188574377"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                orderRepository.cancelOrder(
                    userId = userId,
                    orderId = 999999
                )
            }
        }
        assertEquals(
            "Заказ не найден",
            exception.message
        )
    }

    @Test
    fun cancelOrderThrowsWhenOrderAlreadyCanceled() {

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79188574377"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }
        val orderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.name] = "Second Order"
                it[OrdersTable.phone] = "79990000003"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 200L
                it[OrdersTable.createdAt] = 2000L
                it[OrdersTable.statusOrder] = OrderStatus.CANCELED.name
            }.value
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )
        val exception = assertFailsWith<IllegalStateException> {
            runBlocking {
                orderRepository.cancelOrder(
                    userId = userId,
                    orderId = orderId
                )
            }
        }
        assertEquals(
            "Заказ уже отменён",
            exception.message
        )
    }

    @Test
    fun cancelOrderThrowsWhenOrderCanNoLongerBeCanceled() {

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79188574377"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }
        val orderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.name] = "Second Order"
                it[OrdersTable.phone] = "79990000003"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 200L
                it[OrdersTable.createdAt] = 2000L
                it[OrdersTable.statusOrder] = OrderStatus.CANCELED.name
            }.value
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )

        val statuses = listOf(
            OrderStatus.COOKING,
            OrderStatus.READY,
            OrderStatus.COMPLETED
        )

        statuses.forEach { status ->

            transaction {
                OrdersTable.update(
                    { OrdersTable.id eq orderId }
                ) {
                    it[statusOrder] = status.name
                }
            }

            val exception = assertFailsWith<IllegalStateException> {
                runBlocking {
                    orderRepository.cancelOrder(
                        userId = userId,
                        orderId = orderId
                    )
                }
            }

            assertEquals(
                "Заказ уже нельзя отменить",
                exception.message
            )
        }
    }

    @Test
    fun cancelOrderThrowsWhenOrderBelongsToAnotherUser() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }
        val secondUserId = transaction {
            UsersTable.insert {
                it[name] = "Second User"
                it[phone] = "79990000002"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }
        val firstUserId = transaction {
            UsersTable.insert {
                it[name] = "First User"
                it[phone] = "79990000001"
                it[passwordHash] = "test_hash_1"
            } get UsersTable.id
        }
        val orderId = transaction {
            OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = firstUserId
                it[OrdersTable.name] = "Second Order"
                it[OrdersTable.phone] = "79990000003"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 200L
                it[OrdersTable.createdAt] = 2000L
                it[OrdersTable.statusOrder] = OrderStatus.ACCEPTED.name
            }.value
        }
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                orderRepository.cancelOrder(
                    userId = secondUserId,
                    orderId = orderId
                )
            }
        }

        assertEquals(
            "Заказ не найден",
            exception.message
        )
    }

    @Test
    fun createOrderCreatesOrderWithItems() {

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
            MenuDishesTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Second User"
                it[phone] = "79990000002"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }

        val dishId = transaction {
            MenuDishesTable.insertAndGetId {
                it[name] = "Тестовая пицца"
                it[price] = 500
            }.value
        }

        val orderItem = listOf(
            CreateOrderItemRequest(
                dishId = dishId,
                quantity = 2
            )
        )
        val request = CreateOrderRequest(
            orderItems = orderItem,
            name = "User",
            phone = "79990000002",
            comment = "",
            deliveryType = "Самовывоз",
            address = "",
        )
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )
        val result = runBlocking {
            orderRepository.createOrder(
                userId = userId,
                request = request,
                idempotencyKey = "test-key-1"
            )
        }
        assertEquals(OrderStatus.ACCEPTED, result.statusOrder)
        assertEquals(1000L, result.totalAmount)
        assertEquals(1, result.products.size)
        val product = result.products.first()

        assertEquals(dishId, product.dishId)
        assertEquals("Тестовая пицца", product.name)
        assertEquals(500, product.price)
        assertEquals(2, product.quantity)

        transaction {
            val orderRow = OrdersTable
                .selectAll()
                .where { OrdersTable.id eq result.id }
                .single()

            assertEquals(userId, orderRow[OrdersTable.userId])
            assertEquals(1000L, orderRow[OrdersTable.totalAmount])
            assertEquals(
                OrderStatus.ACCEPTED.name,
                orderRow[OrdersTable.statusOrder]
            )

            val itemRow = OrderItemsTable
                .selectAll()
                .where { OrderItemsTable.orderId eq result.id }
                .single()

            assertEquals(dishId, itemRow[OrderItemsTable.dishId])
            assertEquals("Тестовая пицца", itemRow[OrderItemsTable.name])
            assertEquals(500, itemRow[OrderItemsTable.price])
            assertEquals(2, itemRow[OrderItemsTable.quantity])
        }
    }

    @Test
    fun createOrderWithSameIdempotencyKeyReturnsExistingOrder() {

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
            MenuDishesTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Second User"
                it[phone] = "79990000002"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }

        val dishId = transaction {
            MenuDishesTable.insertAndGetId {
                it[name] = "Тестовая пицца"
                it[price] = 500
            }.value
        }

        val orderItem = listOf(
            CreateOrderItemRequest(
                dishId = dishId,
                quantity = 2
            )
        )
        val request = CreateOrderRequest(
            orderItems = orderItem,
            name = "User",
            phone = "79990000002",
            comment = "",
            deliveryType = "Самовывоз",
            address = "",
        )
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )
        val firstResult = runBlocking {
            orderRepository.createOrder(
                userId = userId,
                request = request,
                idempotencyKey = "same-key"
            )
        }
        val secondResult = runBlocking {
            orderRepository.createOrder(
                userId = userId,
                request = request,
                idempotencyKey = "same-key"
            )
        }

        assertEquals(firstResult.id, secondResult.id)
        val ordersCount = transaction {
            OrdersTable
                .selectAll()
                .where { OrdersTable.userId eq userId }
                .count()
        }

        assertEquals(1L, ordersCount)
    }

    @Test
    fun createOrderNormalizesInputData() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
            MenuDishesTable.deleteAll()
        }
        val userId = transaction {
            UsersTable.insert {
                it[name] = "Second User"
                it[phone] = "79990000002"
                it[passwordHash] = "test_hash"
            } get UsersTable.id
        }

        val dishId = transaction {
            MenuDishesTable.insertAndGetId {
                it[name] = "Тестовая пицца"
                it[price] = 500
            }.value
        }

        val orderItem = listOf(
            CreateOrderItemRequest(
                dishId = dishId,
                quantity = 2
            )
        )
        val request = CreateOrderRequest(
            orderItems = listOf(
                CreateOrderItemRequest(
                    dishId = dishId,
                    quantity = 1
                )
            ),
            name = "  Test User  ",
            phone = "+7 (999) 000-00-02",
            comment = "  Test comment  ",
            deliveryType = "  доставка  ",
            address = "  Test street 1  "
        )
        val orderRepository = OrderRepository(
            orderFinder = DatabaseOrderByIdempotencyKeyFinder()
        )
        val result = runBlocking {
            orderRepository.createOrder(
                userId = userId,
                request = request,
                idempotencyKey = "normalize-test-key"
            )
        }
        assertEquals("Test User", result.name)
        assertEquals("79990000002", result.phone)
        assertEquals("Test comment", result.comment)
        assertEquals("Доставка", result.deliveryType)
        assertEquals("Test street 1", result.address)
    }
}


