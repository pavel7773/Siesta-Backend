package com.example.catering.catering_test.security


import com.example.catering.security.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    @Test
    fun correctPasswordReturnsTrue() {
        // Arrange — подготовили всё необходимое
        val passwordHasher = PasswordHasher()
        val passwordH = passwordHasher.hash("MyPassword123!")
        // Act  — выполнили действие, которое проверяем
        val result = passwordHasher.verify("MyPassword123!", passwordH)
        // Assert — проверили результат
        assertTrue(result)
    }

    @Test
    fun wrongPasswordReturnsFalse() {
        val passwordHasher = PasswordHasher()
        val passwordH = passwordHasher.hash("MyPassword123!")
        val result = passwordHasher.verify("MyPassword12334344!", passwordH)
        assertFalse(result)
    }

    @Test
    fun samePasswordProducesDifferentHashes() {
        val passwordHasher = PasswordHasher()
        val passwordH = passwordHasher.hash("MyPassword123!")
        val passwordH1 = passwordHasher.hash("MyPassword123!")
        assertNotEquals(passwordH,passwordH1)
    }
}