package com.example.catering.catering_test.service


import com.example.catering.service.EmailSender
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {

    val recipient = System.getenv("SMTP_FROM")
        ?: error("SMTP_FROM не задан")

    println("Проверяем отправку письма СИЕСТА...")

    EmailSender().sendCode(
        to = recipient,
        code = "123456",
        purpose = "EMAIL_VERIFICATION"
    )

    println("SMTP принял письмо!")
    println("Проверь папку Входящие или Спам.")
}
