
package com.example.catering.service

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.Properties

class EmailSender {

    suspend fun sendCode(
        to: String,
        code: String,
        purpose: String
    ): Unit = withContext(Dispatchers.IO) {

        require(code.matches(Regex("""\d{6}"""))) {
            "Код должен содержать 6 цифр"
        }

        val subject = when (purpose) {
            "EMAIL_VERIFICATION" ->
                "СИЕСТА — подтверждение почты"

            "PASSWORD_RESET" ->
                "СИЕСТА — восстановление пароля"

            else -> error("Неизвестное назначение кода")
        }

        val message = """
            Здравствуйте!

            Ваш код СИЕСТА:

            $code

            Код действует 10 минут.

            Никому не сообщайте этот код.
            Если вы не запрашивали его,
            просто проигнорируйте письмо.

            С уважением,
            команда СИЕСТА
        """.trimIndent()

        when (
            System.getenv("EMAIL_PROVIDER")
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?: "smtp"
        ) {
            "smtp" -> sendSmtp(
                to = to,
                subject = subject,
                message = message
            )

            "resend" -> sendResend(
                to = to,
                subject = subject,
                message = message
            )

            else -> error("Неизвестный EMAIL_PROVIDER")
        }
    }

    private fun sendResend(
        to: String,
        subject: String,
        message: String
    ) {
        val apiKey = requiredEnv("RESEND_API_KEY")
        val from = requiredEnv("RESEND_FROM")

        val payload = buildJsonObject {
            put("from", from)

            put("to", buildJsonArray {
                add(JsonPrimitive(to))
            })

            put("subject", subject)
            put("text", message)
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    Json.encodeToString(payload)
                )
            )
            .build()

        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.discarding()
        )

        if (response.statusCode() !in 200..299) {
            error(
                "Resend отклонил отправку. HTTP ${response.statusCode()}"
            )
        }
    }

    private fun sendSmtp(
        to: String,
        subject: String,
        message: String
    ) {
        val host = requiredEnv("SMTP_HOST")
        val username = requiredEnv("SMTP_USERNAME")
        val password = requiredEnv("SMTP_PASSWORD")
        val from = requiredEnv("SMTP_FROM")

        val port = System.getenv("SMTP_PORT")
            ?.toIntOrNull() ?: 465

        require(port == 465) {
            "Для SMTP используется SSL/TLS, порт 465"
        }

        val properties = Properties().apply {
            setProperty("mail.smtp.host", host)
            setProperty("mail.smtp.port", port.toString())
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.ssl.enable", "true")
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.connectiontimeout", "10000")
            setProperty("mail.smtp.timeout", "10000")
            setProperty("mail.smtp.writetimeout", "10000")
        }

        val session = Session.getInstance(properties)

        val sender = InternetAddress(from).apply {
            validate()
        }

        val recipient = InternetAddress(to).apply {
            validate()
        }

        val email = MimeMessage(session).apply {
            setFrom(sender)
            setRecipient(
                Message.RecipientType.TO,
                recipient
            )
            setSubject(subject, "UTF-8")
            setText(message, "UTF-8")
            sentDate = Date()
        }

        Transport.send(email, username, password)
    }

    private fun requiredEnv(name: String): String {
        return System.getenv(name)
            ?.takeIf { it.isNotBlank() }
            ?: error("Не задана переменная $name")
    }

    companion object {
        private val httpClient: HttpClient =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
    }
}
