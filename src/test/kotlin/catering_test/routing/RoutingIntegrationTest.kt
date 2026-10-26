package com.example.catering.catering_test.routing

import com.example.catering.catering_test.database.TestDatabaseFactory
import com.example.catering.configureRouting
import com.example.catering.configureSerialization
import com.example.catering.database.table.MenuDishesTable
import com.example.catering.database.table.OrderItemsTable
import com.example.catering.database.table.OrdersTable
import com.example.catering.database.table.UsersTable
import com.example.catering.model.status.OrderStatus
import com.example.catering.security.JwtService
import com.example.catering.security.PasswordHasher
import com.example.catering.security.configureAuthentication
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class RoutingIntegrationTest {

    private fun Application.testModule() {
        install(XForwardedHeaders)

        install(RateLimit) {
            register(RateLimitName("login")) {
                rateLimiter(
                    limit = 100,
                    refillPeriod = 1.minutes
                )

                requestKey { call ->
                    call.request.origin.remoteHost
                }
            }

            register(RateLimitName("register")) {
                rateLimiter(
                    limit = 100,
                    refillPeriod = 1.minutes
                )

                requestKey { call ->
                    call.request.origin.remoteHost
                }
            }
        }

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        val jwtService = JwtService()

        configureSerialization()
        configureAuthentication(jwtService)
        configureRouting(jwtService)
    }


    private fun createAuthenticatedTestToken(): String {

        // Уникальный телефон для тестового пользователя.
        val phone = "79" +
                kotlin.random.Random.nextLong(
                    from = 0,
                    until = 1_000_000_000L
                ).toString().padStart(9, '0')

        val passwordHash = PasswordHasher()
            .hash("RoutingTestPassword123!")

        // Создаём настоящего пользователя в тестовой базе.
        val userId = transaction {

            val insertedUser = UsersTable.insert {
                it[UsersTable.name] = "Routing Test"
                it[UsersTable.phone] = phone
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.address] = ""
                it[UsersTable.sessionVersion] = 0L
            }

            insertedUser[UsersTable.id]
        }

        // Версия JWT совпадает с версией в PostgreSQL.
        return JwtService().generateToken(
            userId = userId,
            sessionVersion = 0L
        )
    }


    @Test
    fun registerReturnsCreatedForValidRequest() = testApplication {

        application {
            testModule()

            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }
        }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "Test User",
                "phone": "+7 (999) 000-00-20",
                "password": "MyPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Created,
            response.status
        )
    }

    @Test
    fun registerReturnsBadRequestForInvalidName() = testApplication {
        application {
            testModule()

            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }
        }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "A",
                "phone": "79990000020",
                "password": "MyPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.BadRequest,
            response.status
        )
    }

    @Test
    fun registerReturnsConflictWhenPhoneAlreadyRegistered() = testApplication {
        application {
            testModule()

            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }
        }

        val firstResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "First User",
                "phone": "79990000030",
                "password": "MyPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Created,
            firstResponse.status
        )

        val secondResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "Second User",
                "phone": "79990000030",
                "password": "AnotherPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Conflict,
            secondResponse.status
        )
    }

    @Test
    fun loginReturnsOkForValidCredentials() = testApplication {
        application {
            testModule()

            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }
        }

        // Сначала регистрируем настоящего пользователя
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "Test User",
                "phone": "79990000040",
                "password": "MyPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Created,
            registerResponse.status
        )

        // Затем пытаемся войти
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "phone": "79990000040",
                "password": "MyPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.OK,
            loginResponse.status
        )
    }

    @Test
    fun loginReturnsUnauthorizedForWrongPassword() = testApplication {
        application {
            testModule()

            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }
        }

        client.post("/auth/register") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "Test User",
                "phone": "79990000041",
                "password": "CorrectPassword123!"
            }
            """.trimIndent()
            )
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "phone": "79990000041",
                "password": "WrongPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status
        )
    }

    @Test
    fun loginReturnsUnauthorizedForWrongPhone() = testApplication {
        application {
            testModule()


            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }

            val passwordHasher = PasswordHasher()
            val password = "MyPassword123!"

            transaction {
                UsersTable.insert {
                    it[name] = "Test User"
                    it[phone] = "79990000050"
                    it[passwordHash] = passwordHasher.hash(password)
                    it[address] = ""
                }
            }
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "phone": "79990000051",
                "password": "MyPassword123!"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status
        )
    }

    @Test
    fun loginReturnsUnauthorizedForPassword() = testApplication {

        application {
            testModule()


            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }

            val passwordHasher = PasswordHasher()
            val password = "MyPassword123!"

            transaction {
                UsersTable.insert {
                    it[name] = "Test User"
                    it[phone] = "79990000051"
                    it[passwordHash] = passwordHasher.hash(password)
                    it[address] = ""
                }
            }
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "phone": "79990000051",
                "password": "MyPassword123"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status
        )
    }

    @Test
    fun loginReturnsOkForCorrectCredentials() = testApplication {

        application {
            testModule()


            transaction {
                OrderItemsTable.deleteAll()
                OrdersTable.deleteAll()
                UsersTable.deleteAll()
            }

            val passwordHasher = PasswordHasher()
            val password = "MyPassword123"

            transaction {
                UsersTable.insert {
                    it[name] = "Test User"
                    it[phone] = "79990000051"
                    it[passwordHash] = passwordHasher.hash(password)
                    it[address] = ""
                }
            }
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "phone": "79990000051",
                "password": "MyPassword123"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )
    }

    @Test
    fun getMeReturnsUnauthorizedWithoutToken() = testApplication {

        application {
            testModule()
        }
        val response = client.get("/auth/me")

        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status
        )
    }

    @Test
    fun getMeReturnsOkWithValidToken() = testApplication {

        application {
            testModule()
        }

        startApplication()

        val passwordHasher = PasswordHasher()

        val userId = transaction {

            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()

            val insertedUser = UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000060"
                it[passwordHash] = passwordHasher.hash("MyPassword123!")
                it[address] = "Test address"
            }

            insertedUser[UsersTable.id]
        }

        val jwtService = JwtService()
        val token = jwtService.generateToken(userId)

        val response = client.get("/auth/me") {
            bearerAuth(token)
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )
    }
    @Test
    fun updateMeReturnsOkForValidRequest() = testApplication {

        application {
            testModule()
        }

        startApplication()

        val passwordHasher = PasswordHasher()

        val userId = transaction {

            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()

            val insertedUser = UsersTable.insert {
                it[name] = "Old Name"
                it[phone] = "79990000061"
                it[passwordHash] = passwordHasher.hash("MyPassword123!")
                it[address] = "Old address"
            }

            insertedUser[UsersTable.id]
        }

        val token = JwtService().generateToken(userId)

        val response = client.patch("/auth/me") {

            bearerAuth(token)
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "New Name",
                "address": "New address"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )
    }
    @Test
    fun updateMeReturnsBadRequestForEmptyRequest() = testApplication {

        application {
            testModule()
        }

        startApplication()

        val token = createAuthenticatedTestToken()

        val response = client.patch("/auth/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": null,
                "address": null
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.BadRequest,
            response.status
        )
    }
    @Test
    fun createOrderReturnsBadRequestWithoutIdempotencyKey() = testApplication {

        application {
            testModule()
        }

        startApplication()

        val token = createAuthenticatedTestToken()

        val response = client.post("/orders") {
            bearerAuth(token)
        }

        assertEquals(
            HttpStatusCode.BadRequest,
            response.status
        )
    }

    @Test
    fun createOrderReturnsCreatedForValidRequest() = testApplication {

        application {
            testModule()
        }

        startApplication()

        var userId = 0
        var dishId = 0

        transaction {

            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
            MenuDishesTable.deleteAll()

            val insertedUser = UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000070"
                it[passwordHash] = "test_hash"
                it[address] = "Test address"
            }

            userId = insertedUser[UsersTable.id]

            val insertedDish = MenuDishesTable.insert {
                it[name] = "Test Dish"
                it[price] = 450
            }

            dishId = insertedDish[MenuDishesTable.id].value
        }

        val token = JwtService().generateToken(userId)

        val response = client.post("/orders") {

            bearerAuth(token)

            header(
                "Idempotency-Key",
                "test-order-key-1"
            )

            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "orderItems": [
                    {
                        "dishId": $dishId,
                        "quantity": 2
                    }
                ],
                "name": "Test User",
                "phone": "79990000070",
                "comment": "",
                "deliveryType": "Доставка",
                "address": "Test address"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Created,
            response.status
        )
    }

    @Test
    fun getOrdersReturnsOkWithValidToken() = testApplication {

        application {
            testModule()
        }

        val token = createAuthenticatedTestToken()

        val response = client.get("/orders") {
            bearerAuth(token)
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )
    }

    @Test
    fun cancelOrderReturnsBadRequestForInvalidId() = testApplication {

        application {
            testModule()
        }

        val token = createAuthenticatedTestToken()

        val response = client.patch("/orders/abc/cancel") {
            bearerAuth(token)
        }

        assertEquals(
            HttpStatusCode.BadRequest,
            response.status
        )
    }

    @Test
    fun cancelOrderReturnsNotFoundWhenOrderDoesNotExist() = testApplication {

        application {
            testModule()
        }

        startApplication()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
        }

        val token = createAuthenticatedTestToken()

        val response = client.patch("/orders/999999/cancel") {
            bearerAuth(token)
        }

        assertEquals(
            HttpStatusCode.NotFound,
            response.status
        )
    }

    @Test
    fun cancelOrderReturnsConflictWhenOrderCannotBeCanceled() = testApplication {

        application {
            testModule()
        }

        startApplication()

        var userId = 0
        var orderId = 0

        transaction {

            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()

            val insertedUser = UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000080"
                it[passwordHash] = "test_hash"
                it[address] = ""
            }

            userId = insertedUser[UsersTable.id]

            val insertedOrder = OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.idempotencyKey] = "cancel-conflict-key"
                it[OrdersTable.name] = "Test User"
                it[OrdersTable.phone] = "79990000080"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 450
                it[OrdersTable.createdAt] = System.currentTimeMillis()
                it[OrdersTable.statusOrder] = OrderStatus.COOKING.name
            }

            orderId = insertedOrder.value
        }

        val token = JwtService().generateToken(userId)

        val response = client.patch("/orders/$orderId/cancel") {
            bearerAuth(token)
        }

        assertEquals(
            HttpStatusCode.Conflict,
            response.status
        )
    }

    @Test
    fun cancelOrderReturnsOkAndChangesStatusToCanceled() = testApplication {

        application {
            testModule()
        }

        startApplication()

        var userId = 0
        var orderId = 0

        transaction {

            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()

            val insertedUser = UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000081"
                it[passwordHash] = "test_hash"
                it[address] = ""
            }

            userId = insertedUser[UsersTable.id]

            val insertedOrder = OrdersTable.insertAndGetId {
                it[OrdersTable.userId] = userId
                it[OrdersTable.idempotencyKey] = "cancel-success-key"
                it[OrdersTable.name] = "Test User"
                it[OrdersTable.phone] = "79990000081"
                it[OrdersTable.comment] = ""
                it[OrdersTable.deliveryType] = "Самовывоз"
                it[OrdersTable.address] = ""
                it[OrdersTable.totalAmount] = 450
                it[OrdersTable.createdAt] = System.currentTimeMillis()
                it[OrdersTable.statusOrder] = OrderStatus.ACCEPTED.name
            }

            orderId = insertedOrder.value
        }

        val token = JwtService().generateToken(userId)

        val response = client.patch("/orders/$orderId/cancel") {
            bearerAuth(token)
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )

        val statusInDatabase = transaction {
            OrdersTable
                .selectAll()
                .where {
                    OrdersTable.id eq orderId
                }
                .single()[OrdersTable.statusOrder]
        }

        assertEquals(
            OrderStatus.CANCELED.name,
            statusInDatabase
        )
    }
    @Test
    fun createOrderWithSale20ReturnsDiscountedServerTotal() = testApplication {

        application {
            testModule()
        }

        startApplication()

        var userId = 0
        var dishId = 0

        transaction {

            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
            MenuDishesTable.deleteAll()

            val insertedUser = UsersTable.insert {
                it[name] = "Promo User"
                it[phone] = "79990000071"
                it[passwordHash] = "test_hash"
                it[address] = ""
            }

            userId = insertedUser[UsersTable.id]

            val insertedDish = MenuDishesTable.insert {
                it[name] = "Promo Dish"
                it[price] = 1000
            }

            dishId =
                insertedDish[MenuDishesTable.id].value
        }

        val token =
            JwtService().generateToken(userId)

        val response = client.post("/orders") {

            bearerAuth(token)

            header(
                "Idempotency-Key",
                "promo-sale20-key"
            )

            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "orderItems": [
                    {
                        "dishId": $dishId,
                        "quantity": 2
                    }
                ],
                "name": "Promo User",
                "phone": "79990000071",
                "comment": "",
                "deliveryType": "Самовывоз",
                "address": "",
                "promoCode": "SALE20"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Created,
            response.status
        )

        val responseJson =
            Json.parseToJsonElement(
                response.bodyAsText()
            ).jsonObject

        val totalAmount =
            responseJson["totalAmount"]
                ?.jsonPrimitive
                ?.content
                ?.toLong()

        // 1000 × 2 = 2000 ₽.
        // SALE20 должен быть рассчитан сервером:
        // 2000 - 20% = 1600 ₽.
        assertEquals(
            1600L,
            totalAmount
        )

        val savedTotal = transaction {
            OrdersTable
                .selectAll()
                .where {
                    OrdersTable.idempotencyKey eq
                            "promo-sale20-key"
                }
                .single()[OrdersTable.totalAmount]
        }

        // Проверяем не только HTTP response,
        // но и то, что правильная сумма сохранена в БД.
        assertEquals(
            1600L,
            savedTotal
        )
    }
}
