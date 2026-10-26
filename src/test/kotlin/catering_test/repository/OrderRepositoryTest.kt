package com.example.catering.catering_test.repository

import com.example.catering.catering_test.fake.FakeMenuDishFinder
import com.example.catering.catering_test.fake.FakeOrderByIdempotencyKeyFinder
import com.example.catering.model.menu.MenuDish
import com.example.catering.model.request.CreateOrderItemRequest
import com.example.catering.model.request.CreateOrderRequest
import com.example.catering.model.response.OrderItemResponse
import com.example.catering.model.response.OrderResponse
import com.example.catering.model.status.OrderStatus
import com.example.catering.validation.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderValidationTest {

    @Test
    fun emptyCartCausesAnException() {

        val request = CreateOrderRequest(
            orderItems = emptyList(),
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderRequest(request)
        }
    }

    @Test
    fun tooManyItemsCausesAnException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(101) { item }

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )

        assertFailsWith<IllegalArgumentException> {
            validateOrderRequest(request)
        }
    }

    @Test
    fun oneItemCartIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderRequest(request)
    }

    @Test
    fun hundredItemsCartIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(100) { item }

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderRequest(request)
    }

    @Test
    fun quantityZeroCausesAnException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 0
        )
        val list = List(1) { item }

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderItemsRequest(request = request)
        }
    }

    @Test
    fun quantity71CausesAnException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 71
        )
        val list = List(1) { item }
        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderItemsRequest(request = request)
        }
    }

    @Test
    fun quantityOneIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderItemsRequest(request = request)
    }

    @Test
    fun quantitySeventyIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 70
        )
        val list = List(1) { item }
        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "просто так",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderItemsRequest(request = request)
    }

    @Test
    fun commentOver500CharactersCausesAnException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }

        val comment = "a".repeat(501)

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = comment,
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderCommentRequest(request)
        }
    }

    @Test
    fun emptyCommentIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }

        val comment = ""

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = comment,
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderCommentRequest(request)
    }

    @Test
    fun comment500CharactersIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }

        val comment = "a".repeat(500)

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = comment,
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderCommentRequest(request)
    }

    @Test
    fun phoneLength10CausesException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "8918857437",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderPhoneRequest(request)
        }
    }

    @Test
    fun phoneLength11IsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = "89990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderPhoneRequest(request)
    }

    @Test
    fun phoneNotStartingWith7CausesException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }

        val request = CreateOrderRequest(
            orderItems = list,
            name = "павел",
            phone = " + 6 - 918 - 857 - 43 - 78 ",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderPhoneRequest(request)
        }
    }

    @Test
    fun name2CharactersLongCausesException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val name = "a".repeat(2)

        val request = CreateOrderRequest(
            orderItems = list,
            name = name,
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderNameRequest(request)
        }
    }

    @Test
    fun name101CharactersLongCausesException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val name = "a".repeat(101)

        val request = CreateOrderRequest(
            orderItems = list,
            name = name,
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderNameRequest(request)
        }
    }

    @Test
    fun name3CharactersLongIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val name = "a".repeat(3)

        val request = CreateOrderRequest(
            orderItems = list,
            name = name,
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderNameRequest(request)
    }

    @Test
    fun name100CharactersLongIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val name = "a".repeat(100)

        val request = CreateOrderRequest(
            orderItems = list,
            name = name,
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "немецкий переулок 1"
        )
        validateOrderNameRequest(request)
    }

    @Test
    fun deliveryTypeCausesException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val deliveryType = "  Курьер  "

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = deliveryType,
            address = "немецкий переулок 1"
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderDeliveryTypeRequest(request)
        }
    }

    @Test
    fun deliveryTypeIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val deliveryType = "  Самовывоз  "

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = deliveryType,
            address = "немецкий переулок 1"
        )
        validateOrderDeliveryTypeRequest(request)
    }

    @Test
    fun deliveryTypeIsAccepted2() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val deliveryType = "  Доставка  "

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = deliveryType,
            address = "немецкий переулок 1"
        )
        validateOrderDeliveryTypeRequest(request)
    }

    @Test
    fun uppercasePickupIsNormalized() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val deliveryType = "САМОВЫВОЗ"

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = deliveryType,
            address = "немецкий переулок 1"
        )
        val result = validateOrderDeliveryTypeRequest(request)

        assertEquals("Самовывоз", result)
    }

    @Test
    fun uppercaseDeliveryWithSpacesIsNormalized() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val deliveryType = "  ДОСТАВКА "

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = deliveryType,
            address = "немецкий переулок 1"
        )
        val result = validateOrderDeliveryTypeRequest(request)

        assertEquals("Доставка", result)
    }

    @Test
    fun deliveryTypeIsAccepted4() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val deliveryType = "ДОСТАВКА"

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = deliveryType,
            address = "немецкий переулок 1"
        )
        validateOrderDeliveryTypeRequest(request)
    }

    @Test
    fun deliveryAddress301CharactersCausesException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val address = "a".repeat(301)

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Доставка",
            address = address
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderAddressRequest(request, normalizedDeliveryType = "Доставка")
        }
    }

    @Test
    fun emptyDeliveryAddressCausesException() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val address = ""

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Доставка",
            address = address
        )
        assertFailsWith<IllegalArgumentException> {
            validateOrderAddressRequest(request, normalizedDeliveryType = "Доставка")
        }
    }

    @Test
    fun deliveryAddress300CharactersIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val address = "a".repeat(300)

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Доставка",
            address = address
        )
        validateOrderAddressRequest(request, normalizedDeliveryType = "Доставка")
    }

    @Test
    fun pickupAddress300CharactersIsAccepted() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val address = "a".repeat(300)

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = address
        )
        validateOrderAddressRequest(request, normalizedDeliveryType = "Самовывоз")
    }

    @Test
    fun emptyPickupAddressIsAccepted4() {

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 1
        )
        val list = List(1) { item }
        val address = ""

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = address
        )
        validateOrderAddressRequest(request, normalizedDeliveryType = "Самовывоз")
    }

    @Test
    fun missingDishCausesException() {
        runBlocking {
            val fakeMenuDishFinder = FakeMenuDishFinder(null)
            val item = CreateOrderItemRequest(
                dishId = 999,
                quantity = 1
            )
            val list = List(1) { item }
            val request = CreateOrderRequest(
                orderItems = list,
                name = "Павел",
                phone = "79990001122",
                comment = "Good",
                deliveryType = "Самовывоз",
                address = "Горький переулок номер 1"
            )
            assertFailsWith<IllegalArgumentException> {
                validateOrderDishesRequest(
                    request = request,
                    menuDishFinder = fakeMenuDishFinder
                )
            }
        }
    }

    @Test
    fun existingDishIsReturned() {
        runBlocking {
            val testDish = MenuDish(
                id = 1,
                name = "Павел",
                price = 450,
                info = "Пицца с 4 сыра",
                imageUrl = "",
                weight = 235,
                calories = 122,
                proteins = 25.5,
                fats = 16.5,
                carbs = 15.5,
                category = ""
            )
            val fakeMenuDishFinder = FakeMenuDishFinder(testDish)

            val item = CreateOrderItemRequest(
                dishId = 1,
                quantity = 1
            )
            val list = List(1) { item }
            val request = CreateOrderRequest(
                orderItems = list,
                name = "Павел",
                phone = "79990001122",
                comment = "Good",
                deliveryType = "Самовывоз",
                address = "Горький переулок номер 1"
            )
            val result = validateOrderDishesRequest(request = request, menuDishFinder = fakeMenuDishFinder)
            assertEquals(1, result.size)
        }
    }

    @Test
    fun existingDishIsReturned1() {
        runBlocking {
            val testDish = MenuDish(
                id = 1,
                name = "Павел",
                price = 450,
                info = "Пицца с 4 сыра",
                imageUrl = "",
                weight = 235,
                calories = 122,
                proteins = 25.5,
                fats = 16.5,
                carbs = 15.5,
                category = ""
            )
            val fakeMenuDishFinder = FakeMenuDishFinder(testDish)

            val item = CreateOrderItemRequest(
                dishId = 1,
                quantity = 1
            )
            val list = List(1) { item }
            val request = CreateOrderRequest(
                orderItems = list,
                name = "Павел",
                phone = "79990001122",
                comment = "Good",
                deliveryType = "Самовывоз",
                address = "Горький переулок номер 1"
            )
            val result = validateOrderDishesRequest(request = request, menuDishFinder = fakeMenuDishFinder)
            assertEquals(testDish, result.first())
        }
    }

    @Test
    fun oneItemCalculateCorrectTotal() {
        val testDishes = listOf(
            MenuDish(
                id = 1,
                name = "Павел",
                price = 450,
                info = "Пицца с 4 сыра",
                imageUrl = "",
                weight = 235,
                calories = 122,
                proteins = 25.5,
                fats = 16.5,
                carbs = 15.5,
                category = ""
            )
        )

        val item = CreateOrderItemRequest(
            dishId = 1,
            quantity = 2
        )
        val list = listOf(item)

        val request = CreateOrderRequest(
            orderItems = list,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "Горький переулок номер 1"
        )

        val result = validateCalculateOrderTotal(dishes = testDishes, request = request)

        assertEquals(900L, result)
    }

    @Test
    fun twoItemsCalculateCorrectTotal() {
        val testDishes = listOf(
            MenuDish(
                id = 1,
                name = "Павел",
                price = 450,
                info = "Пицца с 4 сыра",
                imageUrl = "",
                weight = 235,
                calories = 122,
                proteins = 25.5,
                fats = 16.5,
                carbs = 15.5,
                category = ""
            ),
            MenuDish(
                id = 2,
                name = "Павелий",
                price = 750,
                info = "Пицца мясная",
                imageUrl = "",
                weight = 295,
                calories = 123,
                proteins = 25.5,
                fats = 19.5,
                carbs = 15.5,
                category = ""
            )
        )

        val items = listOf(
            CreateOrderItemRequest(
                dishId = 1,
                quantity = 2
            ),
            CreateOrderItemRequest(
                dishId = 2,
                quantity = 3
            )
        )

        val request = CreateOrderRequest(
            orderItems = items,
            name = "Павел",
            phone = "79990001122",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "Горький переулок номер 1"
        )

        val result = validateCalculateOrderTotal(dishes = testDishes, request = request)

        assertEquals(3150L, result)
    }

    @Test
    fun sale20Applies20PercentDiscountToOrderTotal() {

        val testDishes = listOf(
            MenuDish(
                id = 1,
                name = "Паста",
                price = 1000,
                info = "",
                imageUrl = "",
                weight = 300,
                calories = 500,
                proteins = 20.0,
                fats = 15.0,
                carbs = 60.0,
                category = "Горячее"
            )
        )

        val request = CreateOrderRequest(
            orderItems = listOf(
                CreateOrderItemRequest(
                    dishId = 1,
                    quantity = 2
                )
            ),
            name = "Павел",
            phone = "79990001122",
            comment = "",
            deliveryType = "Самовывоз",
            address = "",
            promoCode = "SALE20"
        )

        val result =
            validateCalculateOrderTotal(
                dishes = testDishes,
                request = request
            )

        assertEquals(
            1600L,
            result
        )
    }

    @Test
    fun existingOrderIsReturned() {

        val orderItems = listOf(
            OrderItemResponse(
                dishId = 1,
                name = "",
                price = 250,
                quantity = 1
            )
        )

        val status = OrderStatus.ACCEPTED

        val order = OrderResponse(
            id = 1,
            name = "",
            phone = "",
            comment = "Good",
            deliveryType = "Самовывоз",
            address = "Горький переулок номер 1",
            products = orderItems,
            totalAmount = 2,
            createdAt = 10,
            statusOrder = status

        )
        val fakeFinder = FakeOrderByIdempotencyKeyFinder(order)

        val result = findExistingOrderOrThrow(
            userId = 1,
            idempotencyKey = "",
            orderFinder = fakeFinder
        )
        assertEquals(order, result)
    }

    @Test
    fun missingOrderCausesException() {
        val fakeFinder = FakeOrderByIdempotencyKeyFinder(null)


        assertFailsWith<IllegalStateException> {
            findExistingOrderOrThrow(
                userId = 1,
                idempotencyKey = "",
                orderFinder = fakeFinder
            )
        }
    }
    @Test
    fun unknownPromoCodeCausesException() {

        val testDishes = listOf(
            MenuDish(
                id = 1,
                name = "Паста",
                price = 1000,
                info = "",
                imageUrl = "",
                weight = 300,
                calories = 500,
                proteins = 20.0,
                fats = 15.0,
                carbs = 60.0,
                category = "Горячее"
            )
        )

        val request = CreateOrderRequest(
            orderItems = listOf(
                CreateOrderItemRequest(
                    dishId = 1,
                    quantity = 2
                )
            ),
            name = "Павел",
            phone = "79990001122",
            comment = "",
            deliveryType = "Самовывоз",
            address = "",
            promoCode = "UNKNOWN"
        )

        val exception =
            assertFailsWith<IllegalArgumentException> {

                validateCalculateOrderTotal(
                    dishes = testDishes,
                    request = request
                )
            }

        assertEquals(
            "Неверный промокод",
            exception.message
        )
    }
    @Test
    fun sale20BelowMinimumOrderAmountCausesException() {

        val testDishes = listOf(
            MenuDish(
                id = 1,
                name = "Паста",
                price = 500,
                info = "",
                imageUrl = "",
                weight = 300,
                calories = 500,
                proteins = 20.0,
                fats = 15.0,
                carbs = 60.0,
                category = "Горячее"
            )
        )

        val request = CreateOrderRequest(
            orderItems = listOf(
                CreateOrderItemRequest(
                    dishId = 1,
                    quantity = 2
                )
            ),
            name = "Павел",
            phone = "79990001122",
            comment = "",
            deliveryType = "Самовывоз",
            address = "",
            promoCode = "SALE20"
        )

        val exception =
            assertFailsWith<IllegalArgumentException> {

                validateCalculateOrderTotal(
                    dishes = testDishes,
                    request = request
                )
            }

        assertEquals(
            "Промокод доступен от 1500 ₽",
            exception.message
        )
    }
}