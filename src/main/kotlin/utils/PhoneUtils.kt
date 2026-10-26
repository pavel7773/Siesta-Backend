package com.example.catering.utils

fun normalizePhone(phone: String): String {

        val digits = phone.filter { it.isDigit() }
        val normalizedPhone =
            if (digits.length == 11 && digits.startsWith("8")) {
                "7" + digits.drop(1)
            } else {
                digits
            }
        return normalizedPhone
    }
