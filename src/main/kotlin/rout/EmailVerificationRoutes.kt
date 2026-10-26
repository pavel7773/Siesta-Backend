package com.example.catering.rout


import com.example.catering.database.table.UsersTable
import com.example.catering.database.table.VerificationCodeService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.Locale

@Serializable
private data class VerifyEmailCodeRequest(
    val code: String
)
@Serializable
private data class SaveEmailRequest(
    val email: String
)
@Serializable
private data class EmailStatusResponse(val email: String?, val verified: Boolean)
fun Route.emailVerificationRoutes() {

    val verificationService = VerificationCodeService()

    authenticate("auth-jwt") {

        get("/auth/me/email") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val status = withContext(Dispatchers.IO) {
                transaction {
                    UsersTable.selectAll().where { UsersTable.id eq userId }.firstOrNull()?.let {
                        EmailStatusResponse(it[UsersTable.email], it[UsersTable.emailVerifiedAt] != null)
                    }
                }
            }
            if (status == null) call.respond(HttpStatusCode.Unauthorized)
            else call.respond(status)
        }

        post("/auth/me/email/send-code") {

            // Берём ID только из проверенного JWT.
            val userId = call.principal<JWTPrincipal>()
                ?.payload
                ?.subject
                ?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            try {
                val sent = verificationService
                    .sendEmailVerificationCode(userId)

                if (sent) {
                    // Письмо принято SMTP-сервером.
                    call.respond(HttpStatusCode.Accepted)
                } else {
                    // Нет неподтверждённого email
                    // или повторный запрос слишком ранний.
                    call.respond(HttpStatusCode.Conflict)
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                // Не раскрываем внутренние ошибки клиенту.
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }

        post("/auth/me/email/verify") {

            val userId = call.principal<JWTPrincipal>()
                ?.payload
                ?.subject
                ?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = try {
                call.receive<VerifyEmailCodeRequest>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            // Код должен состоять из 6 цифр.
            if (!request.code.matches(Regex("\\d{6}"))) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            try {
                // Получаем email текущего пользователя.
                val email = withContext(Dispatchers.IO) {
                    transaction {
                        UsersTable.selectAll()
                            .where {
                                (UsersTable.id eq userId) and
                                        UsersTable.emailVerifiedAt.isNull()
                            }
                            .firstOrNull()
                            ?.get(UsersTable.email)
                    }
                }

                if (email.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Conflict)
                    return@post
                }

                // Проверяем код через существующий сервис.
                val verified = verificationService.verifyEmail(
                    userId = userId,
                    email = email,
                    enteredCode = request.code
                )

                if (verified) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }

        put("/auth/me/email") {

            val userId = call.principal<JWTPrincipal>()
                ?.payload
                ?.subject
                ?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@put
            }

            val request = try {
                call.receive<SaveEmailRequest>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }

            val email = request.email
                .trim()
                .lowercase(Locale.ROOT)

            if (
                email.length !in 5..254 ||
                !Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
                    .matches(email)
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }

            try {
                val saved = verificationService.saveUserEmail(
                    userId = userId,
                    newEmail = email
                )

                if (saved) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.Conflict)
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }

    }
}
