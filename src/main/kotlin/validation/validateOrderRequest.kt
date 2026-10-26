package com.example.catering.validation

import com.example.catering.model.menu.MenuDish
import com.example.catering.model.request.CreateOrderRequest
import com.example.catering.model.response.OrderResponse
import com.example.catering.repository.MenuDishFinder
import com.example.catering.repository.OrderByIdempotencyKeyFinder
import com.example.catering.utils.normalizePhone

fun validateOrderRequest(request: CreateOrderRequest) {
    if (request.orderItems.size !in 1..100) {
        throw IllegalArgumentException(
            "Корзина должна содержать от 1 до 100 позиций"
        )
    }
}

fun validateOrderItemsRequest(request: CreateOrderRequest) {
    if (request.orderItems.any { it.quantity !in 1..70 }) {
        throw IllegalArgumentException(
            "Некорректное количество блюда"
        )
    }
}

fun validateOrderCommentRequest(request: CreateOrderRequest) {
    val normalizedComment = request.comment.trim()

    if (normalizedComment.length > 500) {
        throw IllegalArgumentException(
            "Комментарий не должен превышать 500 символов"
        )
    }
}

fun validateOrderPhoneRequest(request: CreateOrderRequest) {
    val normalizedPhone = normalizePhone(request.phone)

    if (
        normalizedPhone.length != 11 ||
        !normalizedPhone.startsWith("7")
    ) {
        throw IllegalArgumentException(
            "Введите корректный номер телефона"
        )
    }
}

fun validateOrderNameRequest(request: CreateOrderRequest) {
    val normalizedName = request.name.trim()

    if (normalizedName.length !in 3..100) {
        throw IllegalArgumentException(
            "Имя должно содержать от 3 до 100 символов"
        )
    }
}

fun normalizeDeliveryType(deliveryType: String): String {
    val normalized = deliveryType.trim().lowercase()
    return when (normalized) {
        "доставка" -> "Доставка"
        "самовывоз" -> "Самовывоз"
        else -> throw IllegalArgumentException(
            "Неизвестный тип доставки"
        )
    }
}

fun validateOrderDeliveryTypeRequest(request: CreateOrderRequest): String {
    return normalizeDeliveryType(request.deliveryType)
}

fun validateOrderAddressRequest(
    request: CreateOrderRequest,
    normalizedDeliveryType: String
) {
    val normalizedAddress = request.address.trim()

    if (normalizedAddress.length > 300) {
        throw IllegalArgumentException(
            "Адрес не должен превышать 300 символов"
        )
    }

    if (
        normalizedDeliveryType == "Доставка" &&
        normalizedAddress.isBlank()
    ) {
        throw IllegalArgumentException(
            "Укажите адрес доставки"
        )
    }
}

suspend fun validateOrderDishesRequest(
    request: CreateOrderRequest,
    menuDishFinder: MenuDishFinder
): List<MenuDish> {
    val dishes = request.orderItems.map { item ->
        menuDishFinder.findById(item.dishId)
            ?: throw IllegalArgumentException(
                "Блюдо не найдено"
            )
    }
    return dishes
}

fun validateCalculateOrderTotal(
    dishes: List<MenuDish>,
    request: CreateOrderRequest
): Long {

    val subtotal =
        dishes.zip(request.orderItems)
            .sumOf { (dish, item) ->
                dish.price.toLong() * item.quantity
            }

    val promoCode =
        request.promoCode
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }

    if (promoCode == null) {
        return subtotal
    }

    val discount = when (promoCode) {

        "SALE20" -> {
            if (subtotal < 1500L) {
                throw IllegalArgumentException(
                    "Промокод доступен от 1500 ₽"
                )
            }

            subtotal * 20 / 100
        }

        "WELCOME200" -> {
            if (subtotal < 1000L) {
                throw IllegalArgumentException(
                    "Промокод доступен от 1000 ₽"
                )
            }

            minOf(
                subtotal,
                200L
            )
        }

        "BIGORDER" -> {
            if (subtotal < 3000L) {
                throw IllegalArgumentException(
                    "Промокод доступен от 3000 ₽"
                )
            }

            subtotal * 15 / 100
        }

        "OLDPROMO" -> {
            throw IllegalArgumentException(
                "Промокод больше не активен"
            )
        }

        else -> {
            throw IllegalArgumentException(
                "Неверный промокод"
            )
        }
    }

    return subtotal - discount
}

fun findExistingOrderOrThrow(
    userId: Int,
    idempotencyKey: String,
    orderFinder: OrderByIdempotencyKeyFinder
): OrderResponse {

    return orderFinder.findOrderByIdempotencyKey(
        userId = userId,
        idempotencyKey = idempotencyKey
    ) ?: throw IllegalStateException(
        "Не удалось получить созданный заказ"
    )
}
