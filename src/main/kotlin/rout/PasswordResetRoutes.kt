package com.example.catering.rout


import com.example.catering.database.table.VerificationCodeService
import com.example.catering.security.PasswordHasher
import com.example.catering.service.AuthRateLimitService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
private data class PasswordResetEmailRequest(
    val email: String
)
@Serializable
private data class PasswordResetConfirmRequest(
    val email: String,
    val code: String,
    val newPassword: String
)
fun Route.passwordResetRoutes() {

    val verificationService = VerificationCodeService()
    val rateLimits = AuthRateLimitService()
    val passwordHasher = PasswordHasher()

    post("/auth/password/forgot") {

        val request = try {
            call.receive<PasswordResetEmailRequest>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        val email = request.email.trim()
            .lowercase(Locale.ROOT)

        val genericMessage =
            "Если адрес подтверждён, письмо будет отправлено."

        try {
            // Используем адрес соединения, а не
            // произвольный X-Forwarded-For от клиента.
            val ip = call.request.local.remoteHost

            // Максимум 20 запросов за час с IP.
            if (!rateLimits.allowPasswordResetIp(ip)) {
                call.respond(HttpStatusCode.TooManyRequests)
                return@post
            }

            val validEmail =
                email.length in 5..254 &&
                        Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
                            .matches(email)

            if (validEmail) {

                // Максимум 3 запроса в час на email.
                if (rateLimits.allowPasswordResetEmail(email)) {

                    try {
                        verificationService
                            .sendPasswordResetEmail(email)

                    } catch (e: CancellationException) {
                        throw e

                    } catch (e: Exception) {
                        // Не раскрываем существование email.
                        // Не записываем код или адрес в лог.
                        call.application.log.warn(
                            "Password reset email delivery failed: " +
                                    e.javaClass.simpleName
                        )
                    }
                }
            }

            // Одинаковый ответ для существующих,
            // неизвестных и ограниченных email.
            call.respond(
                HttpStatusCode.Accepted,
                genericMessage
            )

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {
            call.application.log.error(
                "Password reset rate limit error: " +
                        e.javaClass.simpleName
            )

            // При ошибке защиты запрос не выполняем.
            call.respond(HttpStatusCode.ServiceUnavailable)
        }
    }

    post("/auth/password/reset") {

        val request = try {
            call.receive<PasswordResetConfirmRequest>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        val email = request.email.trim()
            .lowercase(Locale.ROOT)

        val validEmail =
            email.length in 5..254 &&
                    Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
                        .matches(email)

        val validCode =
            request.code.matches(Regex("""\d{6}"""))

        val validPassword =
            request.newPassword.length in 6..128

        if (!validEmail || !validCode || !validPassword) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        try {
            // Ограничиваем попытки с одного IP.
            val ip = call.request.local.remoteHost

            if (!rateLimits.allowPasswordResetIp(ip)) {
                call.respond(HttpStatusCode.TooManyRequests)
                return@post
            }

            val success = verificationService.resetPasswordWithEmail(
                rawEmail = email,
                enteredCode = request.code,
                newPassword = request.newPassword,
                passwordHasher = passwordHasher
            )

            if (success) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                // Одинаковый ответ для неизвестного email,
                // неправильного и истёкшего кода.
                call.respond(HttpStatusCode.BadRequest)
            }

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {
            call.application.log.error(
                "Password reset failed: " +
                        e.javaClass.simpleName
            )

            call.respond(HttpStatusCode.ServiceUnavailable)
        }
    }

}
