package com.example.catering.catering_test.service

import com.example.catering.catering_test.database.TestDatabaseFactory
import com.example.catering.database.table.OrderItemsTable
import com.example.catering.database.table.OrdersTable
import com.example.catering.database.table.UsersTable
import com.example.catering.repository.UserRepository
import com.example.catering.security.PasswordHasher
import com.example.catering.service.AuthService
import com.example.catering.service.InvalidCredentialsException
import com.example.catering.service.PhoneAlreadyRegisteredException
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class AuthServiceIntegrationTest {

    @Test
    fun registerCreatesUserWithHashedPassword() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userRepository = UserRepository()
        val passwordHasher = PasswordHasher()

        val authService = AuthService(
            userRepository = userRepository,
            passwordHasher = passwordHasher
        )

        val result = runBlocking {
            authService.register(
                name = "Test User",
                phone = "79990000010",
                password = "MyPassword123!"
            )
        }

        assertEquals("Test User", result.name)
        assertEquals("79990000010", result.phone)
        assertEquals("", result.address)

        assertNotEquals(
            "MyPassword123!",
            result.passwordHash
        )

        assertTrue(
            passwordHasher.verify(
                password = "MyPassword123!",
                passwordHash = result.passwordHash
            )
        )
    }
    @Test
    fun registerThrowsWhenPhoneAlreadyRegistered() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userRepository = UserRepository()
        val passwordHasher = PasswordHasher()

        runBlocking {
            userRepository.createUser(
                name = "Existing User",
                phone = "79990000011",
                passwordHash = passwordHasher.hash("OldPassword123!"),
                address = ""
            )
        }

        val authService = AuthService(
            userRepository = userRepository,
            passwordHasher = passwordHasher
        )

        assertFailsWith<PhoneAlreadyRegisteredException> {
            runBlocking {
                authService.register(
                    name = "Second User",
                    phone = "79990000011",
                    password = "NewPassword123!"
                )
            }
        }
    }
    @Test
    fun loginReturnsUserWhenCredentialsAreCorrect() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userRepository = UserRepository()
        val passwordHasher = PasswordHasher()

        val password = "MyPassword123!"

        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000012"
                it[passwordHash] = passwordHasher.hash(password)
                it[address] = "Test address"
            } get UsersTable.id
        }

        val authService = AuthService(
            userRepository = userRepository,
            passwordHasher = passwordHasher
        )

        val result = runBlocking {
            authService.login(
                phone = "79990000012",
                password = password
            )
        }

        assertEquals(userId, result.id)
        assertEquals("Test User", result.name)
        assertEquals("79990000012", result.phone)
        assertEquals("Test address", result.address)
    }

    @Test
    fun loginThrowsWhenPhoneDoesNotExist() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val authService = AuthService(
            userRepository = UserRepository(),
            passwordHasher = PasswordHasher()
        )

        assertFailsWith<InvalidCredentialsException> {
            runBlocking {
                authService.login(
                    phone = "79999999999",
                    password = "MyPassword123!"
                )
            }
        }
    }

    @Test
    fun loginThrowsWhenPasswordIsWrong() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userRepository = UserRepository()
        val passwordHasher = PasswordHasher()

        transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000013"
                it[passwordHash] = passwordHasher.hash("CorrectPassword123!")
                it[address] = ""
            }
        }

        val authService = AuthService(
            userRepository = userRepository,
            passwordHasher = passwordHasher
        )

        assertFailsWith<InvalidCredentialsException> {
            runBlocking {
                authService.login(
                    phone = "79990000013",
                    password = "WrongPassword123!"
                )
            }
        }
    }
}