package com.example.catering

import com.example.catering.database.DatabaseFactory
import com.example.catering.model.request.ChangePasswordRequest
import com.example.catering.model.request.CreateOrderRequest
import com.example.catering.model.request.LoginRequest
import com.example.catering.model.request.RegisterRequest
import com.example.catering.model.request.UpdateProfileRequest
import com.example.catering.model.response.AuthResponse
import com.example.catering.model.response.UserResponse
import com.example.catering.repository.DatabaseOrderByIdempotencyKeyFinder
import com.example.catering.repository.MenuDishRepository
import com.example.catering.repository.OrderRepository
import com.example.catering.repository.UserRepository
import com.example.catering.rout.emailVerificationRoutes
import com.example.catering.rout.passwordResetRoutes
import com.example.catering.security.JwtService
import com.example.catering.security.PasswordHasher
import com.example.catering.service.AuthService
import com.example.catering.service.InvalidCredentialsException
import com.example.catering.service.InvalidNewPasswordException
import com.example.catering.service.PasswordChangeConflictException
import com.example.catering.service.PhoneAlreadyRegisteredException
import com.example.catering.service.SamePasswordException
import com.example.catering.utils.normalizePhone
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(jwtService: JwtService) {
    val userRepository = UserRepository()

    val menuDishRepository = MenuDishRepository()
    val orderFinder = DatabaseOrderByIdempotencyKeyFinder()
    val orderRepository = OrderRepository(orderFinder = orderFinder)

    val authService = AuthService(
        userRepository = userRepository,
        passwordHasher = PasswordHasher()
    )

    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/health") {
            val databaseHealthy = DatabaseFactory.isHealthy()
            if (databaseHealthy) {
                call.respondText(
                    text = "ok",
                    status = HttpStatusCode.OK
                )
            } else {
                call.respondText(
                    text = "database unavailable",
                    status = HttpStatusCode.ServiceUnavailable
                )
            }
        }
        get("/menu") {
            val dishes = menuDishRepository.getAll()

            call.respond(
                status = HttpStatusCode.OK,
                message = dishes
            )
        }
        rateLimit(RateLimitName("register")) {

            post("/auth/register") {

                val request = call.receive<RegisterRequest>()
                val normalizedPhone = normalizePhone(request.phone)

                val normalizedName = request.name.trim()

                if (normalizedName.length !in 3..100) {
                    call.respondText(
                        "Имя должно содержать от 3 до 100 символов",
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }

                if (
                    normalizedPhone.length != 11 ||
                    !normalizedPhone.startsWith("7")
                ) {
                    call.respondText(
                        "Введите корректный номер телефона",
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }

                if (request.password.length !in 6..128) {
                    call.respondText(
                        "Пароль должен содержать от 6 до 128 символов",
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }

                val user = try {
                    authService.register(
                        name = normalizedName,
                        phone = normalizedPhone,
                        password = request.password
                    )
                } catch (e: PhoneAlreadyRegisteredException) {
                    call.respondText(
                        "Телефон уже зарегистрирован",
                        status = HttpStatusCode.Conflict
                    )
                    return@post
                }


                val sessionVersion = userRepository.getSessionVersion(user.id)

                if (sessionVersion == null) {
                    call.respondText(
                        text = "Не удалось получить данные сессии",
                        status = HttpStatusCode.InternalServerError
                    )
                    return@post
                }

                val token = jwtService.generateToken(
                    userId = user.id,
                    sessionVersion = sessionVersion
                )


                call.respond(
                    status = HttpStatusCode.Created,
                    message = AuthResponse(
                        userResponse = UserResponse(
                            id = user.id,
                            name = user.name,
                            phone = user.phone,
                            address = user.address
                        ),
                        token = token
                    )
                )
            }
        }
        rateLimit(RateLimitName("login")) {
            post("/auth/login") {
                val request = call.receive<LoginRequest>()
                val normalizedPhone = normalizePhone(request.phone)
                val user = try {
                    authService.login(
                        phone = normalizedPhone,
                        password = request.password
                    )
                } catch (e: InvalidCredentialsException) {
                    call.respondText(
                        "Неверный телефон или пароль",
                        status = HttpStatusCode.Unauthorized
                    )
                    return@post
                }


                val sessionVersion = userRepository.getSessionVersion(user.id)

                if (sessionVersion == null) {
                    call.respondText(
                        text = "Не удалось получить данные сессии",
                        status = HttpStatusCode.Unauthorized
                    )
                    return@post
                }

                val token = jwtService.generateToken(
                    userId = user.id,
                    sessionVersion = sessionVersion
                )


                call.respond(
                    status = HttpStatusCode.OK,
                    message = AuthResponse(
                        userResponse = UserResponse(
                            id = user.id,
                            name = user.name,
                            phone = user.phone,
                            address = user.address
                        ),
                        token = token
                    )
                )
            }
        }

        authenticate("auth-jwt") {
            post("/orders") {
                val userId = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.subject
                    ?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val idempotencyKey =
                    call.request.headers["Idempotency-Key"]?.trim()

                if (
                    idempotencyKey.isNullOrBlank() ||
                    idempotencyKey.length > 128
                ) {
                    call.respondText(
                        text = "Некорректный Idempotency-Key",
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }

                val request = call.receive<CreateOrderRequest>()

                val order = try {
                    orderRepository.createOrder(
                        userId = userId,
                        request = request,
                        idempotencyKey = idempotencyKey
                    )
                } catch (e: IllegalArgumentException) {
                    call.respondText(
                        text = e.message ?: "Некорректный запрос",
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }
                call.respond(HttpStatusCode.Created, message = order)
            }



                get("/orders") {
                    val userId = call.principal<JWTPrincipal>()
                        ?.payload
                        ?.subject
                        ?.toIntOrNull()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    val orders = orderRepository.getOrdersByUserId(userId)

                    call.respond(
                        status = HttpStatusCode.OK,
                        message = orders
                    )
                }


                get("/auth/me") {
                    val userId = call.principal<JWTPrincipal>()
                        ?.payload
                        ?.subject
                        ?.toIntOrNull()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    val user = userRepository.findById(userId)

                    if (user == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    call.respond(
                        UserResponse(
                            id = user.id,
                            name = user.name,
                            phone = user.phone,
                            address = user.address
                        )
                    )
                }

                patch("/auth/me") {
                    val userId = call.principal<JWTPrincipal>()
                        ?.payload
                        ?.subject
                        ?.toIntOrNull()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@patch
                    }

                    val request = call.receive<UpdateProfileRequest>()

                    val name = request.name?.trim()
                    val address = request.address?.trim()

                    if (name != null && name.length !in 3..100) {
                        call.respondText(
                            text = "Имя должно содержать от 3 до 100 символов",
                            status = HttpStatusCode.BadRequest
                        )
                        return@patch
                    }

                    if (address != null && address.length > 255) {
                        call.respondText(
                            text = "Адрес не должен превышать 255 символов",
                            status = HttpStatusCode.BadRequest
                        )
                        return@patch
                    }
                    if (name == null && address == null) {
                        call.respondText(
                            text = "Нет данных для изменения",
                            status = HttpStatusCode.BadRequest
                        )
                        return@patch
                    }

                    val user = userRepository.updateProfile(
                        userId = userId,
                        name = name,
                        address = address
                    )

                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@patch
                    }

                    call.respond(
                        status = HttpStatusCode.OK,
                        message = UserResponse(
                            id = user.id,
                            name = user.name,
                            phone = user.phone,
                            address = user.address
                        )
                    )
                }


            rateLimit(RateLimitName("login")) {

                patch("/auth/me/password") {

                    // Пользователь определяется только по проверенному JWT.
                    val userId = call.principal<JWTPrincipal>()
                        ?.payload
                        ?.subject
                        ?.toIntOrNull()

                    if (userId == null || userId <= 0) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@patch
                    }

                    // Получаем запрос.
                    val request = call.receive<ChangePasswordRequest>()

                    // Ограничиваем размер входных паролей.
                    if (
                        request.currentPassword.length !in 6..128 ||
                        request.newPassword.length !in 6..128
                    ) {
                        call.respondText(
                            text = "Пароль должен содержать от 6 до 128 символов",
                            status = HttpStatusCode.BadRequest
                        )
                        return@patch
                    }

                    try {
                        authService.changePassword(
                            userId = userId,
                            currentPassword = request.currentPassword,
                            newPassword = request.newPassword
                        )

                        // Новый пароль сохранён.
                        // Прежний JWT теперь недействителен.
                        call.respond(HttpStatusCode.NoContent)

                    } catch (e: InvalidCredentialsException) {

                        call.respondText(
                            text = "Неверный текущий пароль",
                            status = HttpStatusCode.BadRequest
                        )

                    } catch (e: InvalidNewPasswordException) {

                        call.respondText(
                            text = "Недопустимый новый пароль",
                            status = HttpStatusCode.BadRequest
                        )

                    } catch (e: SamePasswordException) {

                        call.respondText(
                            text = "Новый пароль должен отличаться от старого",
                            status = HttpStatusCode.BadRequest
                        )

                    } catch (e: PasswordChangeConflictException) {

                        call.respondText(
                            text = "Пароль уже был изменён. Войдите заново",
                            status = HttpStatusCode.Conflict
                        )
                    }
                }
            }

            patch("/orders/{id}/cancel") {

                    val userId = call.principal<JWTPrincipal>()
                        ?.payload
                        ?.subject
                        ?.toIntOrNull()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@patch
                    }

                    val orderId = call.parameters["id"]?.toIntOrNull()

                    if (orderId == null) {
                        call.respondText(
                            text = "Некорректный id заказа",
                            status = HttpStatusCode.BadRequest
                        )
                        return@patch
                    }

                    try {
                        val newStatus = orderRepository.cancelOrder(
                            userId = userId,
                            orderId = orderId
                        )

                        call.respond(
                            HttpStatusCode.OK,
                            mapOf("statusOrder" to newStatus.name)
                        )

                    } catch (e: IllegalArgumentException) {

                        call.respondText(
                            text = e.message ?: "Заказ не найден",
                            status = HttpStatusCode.NotFound
                        )

                    } catch (e: IllegalStateException) {

                        call.respondText(
                            text = e.message ?: "Заказ нельзя отменить",
                            status = HttpStatusCode.Conflict
                        )
                    }
                }
            }
        emailVerificationRoutes()
        passwordResetRoutes()
        }
    }
