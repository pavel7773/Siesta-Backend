package com.example.catering.catering_test.utils

import com.example.catering.utils.normalizePhone
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneNormalizerTest {

    @Test
    fun phoneStartingWith8ConvertsTo7() {
        val phone = "89188574377" // даем любой вид телефона
        val result = normalizePhone(phone) // даем проверки для телефона
        assertEquals("79188574377", result) // показываем что мы ожидаем
    }

    @Test
    fun phoneStartingWith7RemainsUnchanged() {
        val phone = "79188574377"
        val result = normalizePhone(phone)
        assertEquals("79188574377", result)
    }
    @Test
    fun formattedPhoneRemovesExtraCharacters() {
        val phone = "+ 7 918 - 857 - 43 - 77"
        val result = normalizePhone(phone)
        assertEquals("79188574377", result)
    }

    @Test
    fun formattedPhoneStartingWith8ConvertsTo7() {
        val phone = "8 (918) 857-43-77"
        val result = normalizePhone(phone)
        assertEquals("79188574377", result)
    }
}