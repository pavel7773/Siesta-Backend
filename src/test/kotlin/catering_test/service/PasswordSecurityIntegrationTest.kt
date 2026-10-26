package com.example.catering.catering_test.service
import com.example.catering.catering_test.database.TestDatabaseFactory
import com.example.catering.repository.UserRepository
import com.example.catering.security.JwtService
import com.example.catering.security.PasswordHasher
import com.example.catering.service.AuthService
import com.example.catering.service.InvalidCredentialsException
import com.example.catering.service.SamePasswordException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordSecurityIntegrationTest {

    private val userRepository = UserRepository()
    private val authService = AuthService(
        userRepository = userRepository,
        passwordHasher = PasswordHasher()
    )
    private val jwtService = JwtService()

    private fun prepareDatabase() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()
    }

    private fun randomPhone(): String {
        val suffix = Random.nextLong(
            0L,
            1_000_000_000L
        ).toString().padStart(9, '0')

        return "79$suffix"
    }

    @Test
    fun changePasswordRevokesOldJwtAndAllowsNewLogin() = runBlocking {
        prepareDatabase()

        val phone = randomPhone()
        val oldPassword = "SiestaOldPassword!123"
        val newPassword = "SiestaNewPassword!456"

        val user = authService.register(
            name = "Security Test",
            phone = phone,
            password = oldPassword
        )

        val oldVersion = userRepository
            .getSessionVersion(user.id)
            ?: error("Версия сессии отсутствует")

        val oldToken = jwtService.generateToken(
            userId = user.id,
            sessionVersion = oldVersion
        )

        authService.changePassword(
            userId = user.id,
            currentPassword = oldPassword,
            newPassword = newPassword
        )

        val newVersion = userRepository
            .getSessionVersion(user.id)
            ?: error("Версия сессии отсутствует")

        // Версия увеличилась ровно на единицу.
        assertEquals(oldVersion + 1L, newVersion)

        // Старый JWT больше не соответствует сессии.
        assertFalse(
            jwtService.isSessionVersionCurrent(
                token = jwtService.verifier().verify(oldToken),
                currentVersion = newVersion
            )
        )

        // Старый пароль больше не подходит.
        assertFailsWith<InvalidCredentialsException> {
            authService.login(
                phone = phone,
                password = oldPassword
            )
        }

        // Новый пароль работает.
        val loggedInUser = authService.login(
            phone = phone,
            password = newPassword
        )

        assertEquals(user.id, loggedInUser.id)

        // Новый JWT соответствует текущей версии.
        val newToken = jwtService.generateToken(
            userId = user.id,
            sessionVersion = newVersion
        )

        assertTrue(
            jwtService.isSessionVersionCurrent(
                token = jwtService.verifier().verify(newToken),
                currentVersion = newVersion
            )
        )
    }

    @Test
    fun wrongCurrentPasswordDoesNotChangeSession() = runBlocking {
        prepareDatabase()

        val phone = randomPhone()
        val password = "CorrectPassword!123"

        val user = authService.register(
            name = "Security Test",
            phone = phone,
            password = password
        )

        val versionBefore =
            userRepository.getSessionVersion(user.id)

        assertFailsWith<InvalidCredentialsException> {
            authService.changePassword(
                userId = user.id,
                currentPassword = "WrongPassword!123",
                newPassword = "AnotherPassword!456"
            )
        }

        val versionAfter =
            userRepository.getSessionVersion(user.id)

        // Ошибка не отзывает действующую сессию.
        assertEquals(versionBefore, versionAfter)

        // Правильный пароль продолжает работать.
        val loggedInUser = authService.login(
            phone = phone,
            password = password
        )

        assertEquals(user.id, loggedInUser.id)
    }

    @Test
    fun samePasswordCannotBeUsedAgain() = runBlocking {
        prepareDatabase()

        val phone = randomPhone()
        val password = "SiestaPassword!123"

        val user = authService.register(
            name = "Security Test",
            phone = phone,
            password = password
        )

        val versionBefore =
            userRepository.getSessionVersion(user.id)

        assertFailsWith<SamePasswordException> {
            authService.changePassword(
                userId = user.id,
                currentPassword = password,
                newPassword = password
            )
        }

        assertEquals(
            versionBefore,
            userRepository.getSessionVersion(user.id)
        )
    }
}
