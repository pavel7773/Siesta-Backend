package com.example.catering.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AuthRateLimitService {

    private enum class Scope(
        val dbName: String,
        val maxPerHour: Int
    ) {
        EMAIL("PASSWORD_RESET_EMAIL", 3),
        IP("PASSWORD_RESET_IP", 20)
    }

    private val secretKey by lazy {
        val secret = System.getenv(
            "ACCOUNT_CODE_HMAC_SECRET"
        ) ?: error("Не задан ACCOUNT_CODE_HMAC_SECRET")

        val bytes = secret.toByteArray(Charsets.UTF_8)

        require(bytes.size >= 32) {
            "Слишком короткий HMAC-секрет"
        }

        SecretKeySpec(bytes, "HmacSHA256")
    }

    suspend fun allowPasswordResetEmail(
        email: String
    ): Boolean {
        val normalized = email.trim()
            .lowercase(Locale.ROOT)

        if (normalized.isBlank() || normalized.length > 254) {
            return false
        }

        return consume(Scope.EMAIL, normalized)
    }

    suspend fun allowPasswordResetIp(
        ip: String
    ): Boolean {
        val normalized = ip.trim()

        if (normalized.isBlank() || normalized.length > 255) {
            return false
        }

        return consume(Scope.IP, normalized)
    }

    private fun hashKey(
        scope: Scope,
        value: String
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)

        val payload =
            "siesta-rate-limit-v1:${scope.dbName}:$value"

        return mac.doFinal(
            payload.toByteArray(Charsets.UTF_8)
        ).joinToString("") {
            "%02x".format(it)
        }
    }

    private suspend fun consume(
        scope: Scope,
        value: String
    ): Boolean = withContext(Dispatchers.IO) {

        val hash = hashKey(scope, value)
        val maximum = scope.maxPerHour
        val blockedCount = maximum + 1

        transaction {
            val sql = """
                WITH counted AS (
                    INSERT INTO auth_rate_limits (
                        scope,
                        key_hash,
                        window_start,
                        request_count
                    )
                    VALUES (
                        ?,
                        ?,
                        to_timestamp(
                            floor(extract(epoch FROM now()) / 3600)
                            * 3600
                        ),
                        1
                    )
                    ON CONFLICT (
                        scope,
                        key_hash,
                        window_start
                    )
                    DO UPDATE SET
                        request_count = LEAST(
                            auth_rate_limits.request_count + 1,
                            $blockedCount
                        )
                    RETURNING request_count
                )
                SELECT request_count <= $maximum
                FROM counted
            """.trimIndent()

            exec(
                stmt = sql,
                args = listOf(
                    VarCharColumnType(32) to scope.dbName,
                    VarCharColumnType(64) to hash
                ),
                explicitStatementType = StatementType.SELECT
            ) { result ->
                check(result.next()) {
                    "Не удалось проверить лимит запросов"
                }

                result.getBoolean(1)
            } ?: error("Нет результата проверки лимита")
        }
    }
}
