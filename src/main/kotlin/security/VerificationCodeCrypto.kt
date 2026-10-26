package com.example.catering.security


import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object VerificationCodeCrypto {

    private const val ALGORITHM = "HmacSHA256"
    private const val SECRET_ENV = "ACCOUNT_CODE_HMAC_SECRET"

    private val random = SecureRandom()

    private val secretKey by lazy {
        val secret = System.getenv(SECRET_ENV)
            ?: error("$SECRET_ENV не задан")

        val bytes = secret.toByteArray(Charsets.UTF_8)

        require(bytes.size >= 32) {
            "$SECRET_ENV должен содержать минимум 32 байта"
        }

        SecretKeySpec(bytes, ALGORITHM)
    }

    // Создаём случайный шестизначный код.
    fun generateCode(): String {
        return random.nextInt(1_000_000)
            .toString()
            .padStart(6, '0')
    }

    // Храним HMAC, а не сам код.
    fun hashCode(
        userId: Int,
        purpose: String,
        channel: String,
        destination: String,
        code: String
    ): String {
        require(code.matches(Regex("\\d{6}"))) {
            "Неверный формат кода"
        }

        val payload = listOf(
            userId.toString(),
            purpose,
            channel,
            destination,
            code
        ).joinToString("") { value ->
            "${value.length}:$value"
        }

        val mac = Mac.getInstance(ALGORITHM)
        mac.init(secretKey)

        return mac.doFinal(
            payload.toByteArray(Charsets.UTF_8)
        ).joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    // Сравниваем код с сохранённым HMAC.
    fun verifyCode(
        userId: Int,
        purpose: String,
        channel: String,
        destination: String,
        code: String,
        storedHash: String
    ): Boolean {
        if (!code.matches(Regex("\\d{6}"))) {
            return false
        }

        if (!storedHash.matches(Regex("[0-9a-fA-F]{64}"))) {
            return false
        }

        val calculatedHash = hashCode(
            userId,
            purpose,
            channel,
            destination,
            code
        )

        return MessageDigest.isEqual(
            calculatedHash.toByteArray(Charsets.US_ASCII),
            storedHash.lowercase()
                .toByteArray(Charsets.US_ASCII)
        )
    }
}
