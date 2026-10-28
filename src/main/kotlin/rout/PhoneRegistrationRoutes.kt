package com.example.catering.rout

import com.example.catering.database.table.VerificationCodeService
import com.example.catering.model.response.AuthResponse
import com.example.catering.model.response.UserResponse
import com.example.catering.repository.UserRepository
import com.example.catering.security.JwtService
import com.example.catering.utils.normalizePhone
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
private data class VerifyPhoneRequest(
    val phone: String,
    val code: String
)

@Serializable
private data class ResendPhoneCodeRequest(
    val phone: String
)

fun Route.phoneRegistrationRoutes(
    jwtService: JwtService
) {

    val verificationService =
        VerificationCodeService()

    val userRepository =
        UserRepository()

    rateLimit(
        RateLimitName("register")
    ) {

        post(
            "/auth/register/resend-code"
        ) {

            val request =
                try {
                    call.receive<
                            ResendPhoneCodeRequest
                            >()
                } catch (
                    e: CancellationException
                ) {
                    throw e

                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }

            val phone =
                normalizePhone(request.phone)

            if (
                phone.length != 11 ||
                !phone.startsWith("7")
            ) {
                call.respond(
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val user =
                userRepository
                    .findByPhone(phone)

            if (user == null) {
                call.respond(
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            if (
                userRepository
                    .isPhoneVerified(user.id)
            ) {
                call.respond(
                    HttpStatusCode.Conflict
                )
                return@post
            }

            try {
                val sent =
                    verificationService
                        .sendPhoneRegistrationCode(
                            user.id
                        )

                if (sent) {
                    call.respond(
                        HttpStatusCode.Accepted
                    )
                } else {
                    call.respond(
                        HttpStatusCode.TooManyRequests
                    )
                }

            } catch (
                e: CancellationException
            ) {
                throw e

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable
                )
            }
        }

        post(
            "/auth/register/verify-phone"
        ) {

            val request =
                try {
                    call.receive<
                            VerifyPhoneRequest
                            >()
                } catch (
                    e: CancellationException
                ) {
                    throw e

                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }

            val phone =
                normalizePhone(request.phone)

            if (
                phone.length != 11 ||
                !phone.startsWith("7") ||
                !request.code.matches(
                    Regex("""\d{6}""")
                )
            ) {
                call.respond(
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val verified =
                try {
                    verificationService
                        .verifyPhoneRegistration(
                            rawPhone = phone,
                            enteredCode =
                                request.code
                        )
                } catch (
                    e: CancellationException
                ) {
                    throw e

                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable
                    )
                    return@post
                }

            if (!verified) {
                call.respond(
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val user =
                userRepository.findByPhone(phone)

            if (user == null) {
                call.respond(
                    HttpStatusCode.Unauthorized
                )
                return@post
            }

            val sessionVersion =
                userRepository
                    .getSessionVersion(user.id)

            if (sessionVersion == null) {
                call.respond(
                    HttpStatusCode.InternalServerError
                )
                return@post
            }

            val token =
                jwtService.generateToken(
                    userId = user.id,
                    sessionVersion =
                        sessionVersion
                )

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    userResponse =
                        UserResponse(
                            id = user.id,
                            name = user.name,
                            phone = user.phone,
                            address =
                                user.address
                        ),
                    token = token
                )
            )
        }
    }
}