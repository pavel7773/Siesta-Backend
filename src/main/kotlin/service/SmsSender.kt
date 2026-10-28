package com.example.catering.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class SmsSender {

    suspend fun sendCode(
        phone: String,
        code: String
    ): Unit = withContext(Dispatchers.IO) {

        require(phone.matches(Regex("""7\d{10}"""))) {
            "Некорректный номер телефона"
        }

        require(code.matches(Regex("""\d{6}"""))) {
            "Код должен содержать 6 цифр"
        }

        val apiId =
            System.getenv("SMS_RU_API_ID")
                ?.takeIf { it.isNotBlank() }
                ?: error(
                    "Не задана переменная SMS_RU_API_ID"
                )

        val testMode =
            System.getenv("SMS_TEST_MODE")
                ?.trim()
                ?.equals(
                    "true",
                    ignoreCase = true
                )
                ?: false

        val message =
            "Код СИЕСТА: $code. Действует 10 минут."

        val parameters =
            buildList {
                add(
                    "api_id" to apiId
                )
                add(
                    "to" to phone
                )
                add(
                    "msg" to message
                )
                add(
                    "json" to "1"
                )

                if (testMode) {
                    add(
                        "test" to "1"
                    )
                }
            }

        val body =
            parameters.joinToString("&") {
                    (key, value) ->

                val encoded =
                    URLEncoder.encode(
                        value,
                        StandardCharsets.UTF_8
                    )

                "$key=$encoded"
            }

        val request =
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "https://sms.ru/sms/send"
                    )
                )
                .timeout(
                    Duration.ofSeconds(20)
                )
                .header(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
                )
                .POST(
                    HttpRequest.BodyPublishers
                        .ofString(body)
                )
                .build()

        val response =
            httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            )

        if (response.statusCode() !in 200..299) {
            error(
                "SMS.ru вернул HTTP ${response.statusCode()}"
            )
        }

        val json =
            Json.parseToJsonElement(
                response.body()
            ).jsonObject

        check(
            json["status"]
                ?.jsonPrimitive
                ?.content == "OK"
        ) {
            "SMS.ru отклонил запрос"
        }

        val smsResult =
            json["sms"]
                ?.jsonObject
                ?.get(phone)
                ?.jsonObject
                ?: error(
                    "SMS.ru не вернул результат отправки"
                )

        check(
            smsResult["status"]
                ?.jsonPrimitive
                ?.content == "OK"
        ) {
            "SMS.ru не отправил сообщение"
        }

        if (testMode) {

            val maskedPhone =
                phone.take(2) +
                        "*****" +
                        phone.takeLast(4)

            println(
                "SMS_TEST_CODE phone=$maskedPhone code=$code"
            )
        }
    }

    companion object {

        private val httpClient =
            HttpClient.newBuilder()
                .connectTimeout(
                    Duration.ofSeconds(10)
                )
                .build()
    }
}