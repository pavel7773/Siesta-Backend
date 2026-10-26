
package com.example.catering.security

import com.example.catering.repository.UserRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuthentication(
    jwtService: JwtService
) {
    val userRepository = UserRepository()

    install(Authentication) {

        jwt("auth-jwt") {

            // Проверяем подпись и срок действия JWT.
            verifier(jwtService.verifier())

            validate { credential ->

                // Получаем ID пользователя из JWT.
                val userId = credential.payload
                    .subject
                    ?.toIntOrNull()

                // Получаем версию сессии из JWT.
                val tokenSessionVersion = credential.payload
                    .getClaim("session_version")
                    .asLong()

                if (
                    userId == null ||
                    userId <= 0 ||
                    tokenSessionVersion == null ||
                    tokenSessionVersion < 0L
                ) {
                    // Некорректный токен.
                    null
                } else {

                    // Получаем актуальную версию из PostgreSQL.
                    val currentSessionVersion =
                        userRepository.getSessionVersion(userId)

                    if (
                        currentSessionVersion != null &&
                        tokenSessionVersion == currentSessionVersion
                    ) {
                        // Сессия действительна.
                        JWTPrincipal(credential.payload)
                    } else {
                        // Пользователь удалён или сессия отозвана.
                        null
                    }
                }
            }
        }
    }
}
