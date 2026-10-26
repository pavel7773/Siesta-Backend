package com.example.catering.catering_test.repository

import com.example.catering.catering_test.database.TestDatabaseFactory
import com.example.catering.database.table.OrderItemsTable
import com.example.catering.database.table.OrdersTable
import com.example.catering.database.table.UsersTable
import com.example.catering.repository.UserRepository
import com.example.catering.service.PhoneAlreadyRegisteredException
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class UserRepositoryIntegrationTest {

    @Test
    fun findByPhoneReturnsExistingUser() {

        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000001"
                it[passwordHash] = "test_hash"
                it[address] = "Test address"
            } get UsersTable.id
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.findByPhone("79990000001")
        }

        assertNotNull(result)
        assertEquals(userId, result.id)
        assertEquals("Test User", result.name)
        assertEquals("79990000001", result.phone)
        assertEquals("test_hash", result.passwordHash)
        assertEquals("Test address", result.address)
    }

    @Test
    fun findByPhoneReturnsNullWhenUserDoesNotExist() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.findByPhone("79999999999")
        }

        assertNull(result)
    }

    @Test
    fun findByIdReturnsExistingUser() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userId = transaction {
            UsersTable.insert {
                it[name] = "Test User"
                it[phone] = "79990000003"
                it[passwordHash] = "test_hash"
                it[address] = "Test address"
            } get UsersTable.id
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.findById(userId)
        }

        assertNotNull(result)
        assertEquals(userId, result.id)
        assertEquals("Test User", result.name)
        assertEquals("79990000003", result.phone)
        assertEquals("test_hash", result.passwordHash)
        assertEquals("Test address", result.address)
    }

    @Test
    fun findByIdReturnsNullWhenUserDoesNotExist() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.findById(999999)
        }

        assertNull(result)
    }

    @Test
    fun createUserCreatesUser() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.createUser(name = "Test User",
                phone = "79990000001",
                passwordHash = "test_hash",
                address = "Test address")
        }
        assertEquals("Test User", result.name)
        assertEquals("79990000001", result.phone)
        assertEquals("test_hash", result.passwordHash)
        assertEquals("Test address", result.address)

        transaction {
            val row = UsersTable
                .selectAll()
                .where { UsersTable.id eq result.id }
                .single()

            assertEquals("Test User", row[UsersTable.name])
            assertEquals("79990000001", row[UsersTable.phone])
            assertEquals("test_hash", row[UsersTable.passwordHash])
            assertEquals("Test address", row[UsersTable.address])
        }
    }

    @Test
    fun createUserThrowsWhenPhoneAlreadyRegistered() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()

            UsersTable.insert {
                it[name] = "Existing User"
                it[phone] = "79990000005"
                it[passwordHash] = "hash"
                it[address] = ""
            }
        }

        val repository = UserRepository()

        assertFailsWith<PhoneAlreadyRegisteredException> {
            runBlocking {
                repository.createUser(
                    name = "Second User",
                    phone = "79990000005",
                    passwordHash = "another_hash",
                    address = "Address"
                )
            }
        }
    }

    @Test
    fun updateProfileUpdatesNameAndAddress() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userId = transaction {
            UsersTable.insert {
                it[name] = "Old Name"
                it[phone] = "79990000006"
                it[passwordHash] = "test_hash"
                it[address] = "Old address"
            } get UsersTable.id
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.updateProfile(
                userId = userId,
                name = "New Name",
                address = "New address"
            )
        }

        assertNotNull(result)
        assertEquals("New Name", result.name)
        assertEquals("New address", result.address)


        assertEquals("79990000006", result.phone)
        assertEquals("test_hash", result.passwordHash)

        transaction {
            val row = UsersTable
                .selectAll()
                .where { UsersTable.id eq userId }
                .single()

            assertEquals("New Name", row[UsersTable.name])
            assertEquals("New address", row[UsersTable.address])
        }
    }
    @Test
    fun updateProfileReturnsNullWhenUserDoesNotExist() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.updateProfile(
                userId = 999999,
                name = "New Name",
                address = "New address"
            )
        }

        assertNull(result)
    }
    @Test
    fun updateProfileDoesNotChangeAddressWhenAddressIsNull() {
        TestDatabaseFactory.migrate()
        TestDatabaseFactory.init()

        transaction {
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            UsersTable.deleteAll()
        }

        val userId = transaction {
            UsersTable.insert {
                it[name] = "Old Name"
                it[phone] = "79990000007"
                it[passwordHash] = "test_hash"
                it[address] = "Keep this address"
            } get UsersTable.id
        }

        val repository = UserRepository()

        val result = runBlocking {
            repository.updateProfile(
                userId = userId,
                name = "Updated Name",
                address = null
            )
        }

        assertNotNull(result)
        assertEquals("Updated Name", result.name)
        assertEquals("Keep this address", result.address)
    }
}