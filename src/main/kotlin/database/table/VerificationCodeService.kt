package com.example.catering.database.table


import com.example.catering.security.PasswordHasher
import com.example.catering.security.VerificationCodeCrypto
import com.example.catering.service.EmailSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

// Добавь импорт VerificationCodeCrypto через Alt+Enter,
// если он находится в другом пакете.

sealed interface CodeIssueResult {
    data class Created(val code: String) : CodeIssueResult
    data object TooSoon : CodeIssueResult
}

class VerificationCodeService {

    private val codes = AccountVerificationCodesTable

    suspend fun issueCode(
        userId: Int,
        purpose: String,
        channel: String,
        destination: String
    ): CodeIssueResult = withContext(Dispatchers.IO) {
        require(
            purpose == "EMAIL_VERIFICATION" ||
                    purpose == "PASSWORD_RESET"
        )
        require(channel == "EMAIL" || channel == "SMS")
        require(
            purpose != "EMAIL_VERIFICATION" ||
                    channel == "EMAIL"
        )

        transaction {
            // Блокируем строку пользователя,
            // чтобы параллельные запросы шли по очереди.
            val lockedUser = UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .forUpdate()
                .firstOrNull()

            checkNotNull(lockedUser) {
                "Пользователь не найден"
            }

            val now = OffsetDateTime.now(ZoneOffset.UTC)

            val latest = codes.selectAll()
                .where {
                    (codes.userId eq userId) and
                            (codes.purpose eq purpose) and
                            (codes.channel eq channel)
                }
                .orderBy(codes.id to SortOrder.DESC)
                .limit(1)
                .firstOrNull()

            if (
                latest != null &&
                latest[codes.createdAt]
                    .isAfter(now.minusSeconds(60))
            ) {
                return@transaction CodeIssueResult.TooSoon
            }

            val code = VerificationCodeCrypto.generateCode()

            val hash = VerificationCodeCrypto.hashCode(
                userId = userId,
                purpose = purpose,
                channel = channel,
                destination = destination,
                code = code
            )

            // Старые неиспользованные коды аннулируем.
            codes.update({
                (codes.userId eq userId) and
                        (codes.purpose eq purpose) and
                        (codes.channel eq channel) and
                        codes.consumedAt.isNull()
            }) {
                it[consumedAt] = now
            }

            codes.insert {
                it[codes.userId] = userId
                it[codes.purpose] = purpose
                it[codes.channel] = channel
                it[codes.destination] = destination
                it[codes.codeHash] = hash
                it[codes.attemptsCount] = 0
                it[codes.createdAt] = now
                it[codes.expiresAt] = now.plusMinutes(10)
            }

            CodeIssueResult.Created(code)
        }
    }


    suspend fun verifyAndUseCode(
        userId: Int,
        purpose: String,
        channel: String,
        destination: String,
        enteredCode: String,
        onVerified: () -> Unit
    ): Boolean = withContext(Dispatchers.IO) {

        require(
            purpose == "EMAIL_VERIFICATION" ||
                    purpose == "PASSWORD_RESET"
        )
        require(channel == "EMAIL" || channel == "SMS")
        require(
            purpose != "EMAIL_VERIFICATION" ||
                    channel == "EMAIL"
        )

        transaction {
            val lockedUser = UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .forUpdate()
                .firstOrNull()
                ?: return@transaction false
            val row = codes.selectAll()
                .where {
                    (codes.userId eq userId) and
                            (codes.purpose eq purpose) and
                            (codes.channel eq channel) and
                            (codes.destination eq destination) and
                            codes.consumedAt.isNull()
                }
                .orderBy(codes.id to SortOrder.DESC)
                .limit(1)
                .forUpdate()
                .firstOrNull()
                ?: return@transaction false

            val id = row[codes.id]
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val attempts = row[codes.attemptsCount]

            // Код истёк или попытки закончились.
            if (
                !row[codes.expiresAt].isAfter(now) ||
                attempts >= 5
            ) {
                codes.update({ codes.id eq id }) {
                    it[consumedAt] = now
                }
                return@transaction false
            }

            val valid = VerificationCodeCrypto.verifyCode(
                userId = userId,
                purpose = purpose,
                channel = channel,
                destination = destination,
                code = enteredCode,
                storedHash = row[codes.codeHash]
            )

            if (!valid) {
                codes.update({ codes.id eq id }) {
                    it[attemptsCount] = attempts + 1

                    if (attempts + 1 >= 5) {
                        it[consumedAt] = now
                    }
                }
                return@transaction false
            }

            // Изменение аккаунта выполняется
            // в той же транзакции.
            // Только операции с БД, без сетевых вызовов.
            onVerified()

            // Одноразовое использование.
            val updated = codes.update({
                (codes.id eq id) and codes.consumedAt.isNull()
            }) {
                it[consumedAt] = now
            }

            check(updated == 1) {
                "Код уже использован"
            }

            true
        }
    }

    suspend fun verifyEmail(
        userId: Int,
        email: String,
        enteredCode: String
    ): Boolean {

        val normalizedEmail = email.trim().lowercase()

        return verifyAndUseCode(
            userId = userId,
            purpose = "EMAIL_VERIFICATION",
            channel = "EMAIL",
            destination = normalizedEmail,
            enteredCode = enteredCode,

            onVerified = {

                val updated = UsersTable.update({
                    (UsersTable.id eq userId) and
                            (UsersTable.email eq normalizedEmail) and
                            UsersTable.emailVerifiedAt.isNull()
                }) {
                    it[UsersTable.emailVerifiedAt] =
                        System.currentTimeMillis()
                }

                check(updated == 1) {
                    "Не удалось подтвердить email"
                }
            }
        )
    }


    suspend fun saveUserEmail(
        userId: Int,
        newEmail: String
    ): Boolean = withContext(Dispatchers.IO) {

        val email = newEmail.trim()
            .lowercase(java.util.Locale.ROOT)

        if (
            email.length !in 5..254 ||
            !Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
                .matches(email)
        ) {
            return@withContext false
        }

        transaction {

            // Сначала блокируем пользователя.
            val current = UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .forUpdate()
                .firstOrNull()
                ?: return@transaction false

            // Адрес не изменился.
            if (current[UsersTable.email] == email) {
                return@transaction true
            }

            // Проверяем занятость подтверждённого email.
            val taken = UsersTable.selectAll()
                .where {
                    (UsersTable.id neq userId) and
                            (UsersTable.email eq email) and
                            UsersTable.emailVerifiedAt.isNotNull()
                }
                .limit(1)
                .any()

            if (taken) {
                return@transaction false
            }

            val now = OffsetDateTime.now(ZoneOffset.UTC)

            // После блокировки пользователя
            // аннулируем прежние email-коды.
            codes.update({
                (codes.userId eq userId) and
                        (codes.channel eq "EMAIL") and
                        codes.consumedAt.isNull()
            }) {
                it[codes.consumedAt] = now
            }

            // Сохраняем новый email без подтверждения.
            UsersTable.update({
                UsersTable.id eq userId
            }) {
                it[UsersTable.email] = email
                it[UsersTable.emailVerifiedAt] = null
            } == 1
        }
    }


    suspend fun sendEmailVerificationCode(
        userId: Int
    ): Boolean = withContext(Dispatchers.IO) {

        // Получаем email пользователя из базы.
        val email = transaction {
            UsersTable.selectAll()
                .where {
                    (UsersTable.id eq userId) and
                            UsersTable.emailVerifiedAt.isNull()
                }
                .firstOrNull()
                ?.get(UsersTable.email)
        } ?: return@withContext false

        val normalizedEmail = email.trim()
            .lowercase(java.util.Locale.ROOT)

        // Создаём одноразовый код и сохраняем HMAC.
        val result = issueCode(
            userId = userId,
            purpose = "EMAIL_VERIFICATION",
            channel = "EMAIL",
            destination = normalizedEmail
        )

        when (result) {
            is CodeIssueResult.Created -> {

                // Отправляем настоящий код через Яндекс.
                deliverEmailCode(
                    userId = userId,
                    purpose = "EMAIL_VERIFICATION",
                    email = normalizedEmail,
                    issued = result
                )

                true
            }

            CodeIssueResult.TooSoon -> false
        }
    }

    suspend fun sendPasswordResetEmail(
        rawEmail: String
    ) = withContext(Dispatchers.IO) {

        val email = rawEmail.trim()
            .lowercase(java.util.Locale.ROOT)

        if (email.length !in 5..254) {
            return@withContext
        }

        // Ищем только подтверждённый email.
        val userId = transaction {
            UsersTable.selectAll()
                .where {
                    (UsersTable.email eq email) and
                            UsersTable.emailVerifiedAt.isNotNull()
                }
                .limit(1)
                .firstOrNull()
                ?.get(UsersTable.id)
        } ?: return@withContext

        val result = issueCode(
            userId = userId,
            purpose = "PASSWORD_RESET",
            channel = "EMAIL",
            destination = email
        )

        if (result is CodeIssueResult.Created) {
            deliverEmailCode(
                userId = userId,
                purpose = "PASSWORD_RESET",
                email = email,
                issued = result
            )
        }
    }

    suspend fun resetPasswordWithEmail(
        rawEmail: String,
        enteredCode: String,
        newPassword: String,
        passwordHasher: PasswordHasher
    ): Boolean {

        val email = rawEmail.trim()
            .lowercase(java.util.Locale.ROOT)

        // Проверяем входные данные.
        if (
            email.length !in 5..254 ||
            !Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
                .matches(email) ||
            newPassword.length !in 6..128
        ) {
            return false
        }

        // Находим только подтверждённый email.
        val userId = withContext(Dispatchers.IO) {
            transaction {
                UsersTable.selectAll()
                    .where {
                        (UsersTable.email eq email) and
                                UsersTable.emailVerifiedAt.isNotNull()
                    }
                    .limit(1)
                    .firstOrNull()
                    ?.get(UsersTable.id)
            }
        } ?: return false

        // Используем существующий алгоритм
        // хеширования паролей СИЕСТА.
        val passwordHash = withContext(Dispatchers.Default) {
            passwordHasher.hash(newPassword)
        }

        // Проверка кода и обновление пароля
        // выполняются в одной транзакции.
        return verifyAndUseCode(
            userId = userId,
            purpose = "PASSWORD_RESET",
            channel = "EMAIL",
            destination = email,
            enteredCode = enteredCode,
            onVerified = {

                val updated = UsersTable.update({
                    (UsersTable.id eq userId) and
                            (UsersTable.email eq email) and
                            UsersTable.emailVerifiedAt.isNotNull()
                }) {
                    it[UsersTable.passwordHash] = passwordHash

                    // Отзываем старые JWT-сессии.
                    it[UsersTable.sessionVersion] =
                        UsersTable.sessionVersion + 1L
                }

                check(updated == 1) {
                    "Не удалось обновить пароль"
                }
            }
        )
    }

    private suspend fun deliverEmailCode(
        userId: Int,
        purpose: String,
        email: String,
        issued: CodeIssueResult.Created
    ) {
        try {
            EmailSender().sendCode(
                to = email,
                code = issued.code,
                purpose = purpose
            )
        } catch (error: Exception) {

            // Если отправка не удалась,
            // аннулируем именно этот код.
            withContext(NonCancellable + Dispatchers.IO) {
                transaction {
                    val hash = VerificationCodeCrypto.hashCode(
                        userId = userId,
                        purpose = purpose,
                        channel = "EMAIL",
                        destination = email,
                        code = issued.code
                    )

                    codes.update({
                        (codes.userId eq userId) and
                                (codes.purpose eq purpose) and
                                (codes.channel eq "EMAIL") and
                                (codes.destination eq email) and
                                (codes.codeHash eq hash) and
                                codes.consumedAt.isNull()
                    }) {
                        it[codes.consumedAt] =
                            OffsetDateTime.now(ZoneOffset.UTC)
                    }
                }
            }

            throw error
        }
    }


}
