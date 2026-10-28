package com.example.catering.repository

import com.example.catering.database.table.UsersTable
import com.example.catering.model.user.User
import com.example.catering.service.PhoneAlreadyRegisteredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class UserRepository {

    suspend fun findByPhone(phone: String): User? =
        withContext(Dispatchers.IO) {
            transaction {
                val row = UsersTable
                    .selectAll()
                    .where { UsersTable.phone eq phone }
                    .firstOrNull()
                    ?: return@transaction null

                User(
                    id = row[UsersTable.id],
                    name = row[UsersTable.name],
                    phone = row[UsersTable.phone],
                    passwordHash = row[UsersTable.passwordHash],
                    address = row[UsersTable.address]
                )
            }
        }

    suspend fun findById(id: Int): User? =
        withContext(Dispatchers.IO) {
            transaction {
                val row = UsersTable
                    .selectAll()
                    .where { UsersTable.id eq id }
                    .firstOrNull()
                    ?: return@transaction null

                User(
                    id = row[UsersTable.id],
                    name = row[UsersTable.name],
                    phone = row[UsersTable.phone],
                    passwordHash = row[UsersTable.passwordHash],
                    address = row[UsersTable.address]
                )
            }
        }

    suspend fun createUser(
        name: String,
        phone: String,
        passwordHash: String,
        address: String
    ): User {
        try {
            return withContext(Dispatchers.IO) {
                transaction {
                    val insertResult = UsersTable.insert {
                        it[UsersTable.name] = name
                        it[UsersTable.phone] = phone
                        it[UsersTable.passwordHash] = passwordHash
                        it[UsersTable.address] = address
                    }

                    User(
                        id = insertResult[UsersTable.id],
                        name = name,
                        phone = phone,
                        passwordHash = passwordHash,
                        address = address
                    )
                }
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState == "23505") {
                throw PhoneAlreadyRegisteredException()
            }

            throw e
        }
    }

    suspend fun updateProfile(
        userId: Int,
        name: String?,
        address: String?
    ): User? =
        withContext(Dispatchers.IO) {
            transaction {

                val updatedRows = UsersTable.update(
                    where = { UsersTable.id eq userId }
                ) {
                    if (name != null) {
                        it[UsersTable.name] = name
                    }

                    if (address != null) {
                        it[UsersTable.address] = address
                    }
                }

                if (updatedRows == 0) {
                    return@transaction null
                }

                val row = UsersTable
                    .selectAll()
                    .where { UsersTable.id eq userId }
                    .firstOrNull()
                    ?: return@transaction null

                User(
                    id = row[UsersTable.id],
                    name = row[UsersTable.name],
                    phone = row[UsersTable.phone],
                    passwordHash = row[UsersTable.passwordHash],
                    address = row[UsersTable.address]
                )
            }
        }


    suspend fun updatePasswordHash(
        userId: Int,
        expectedPasswordHash: String,
        passwordHash: String
    ): Boolean = withContext(Dispatchers.IO) {

        transaction {

            val updatedRows = UsersTable.update(
                where = {
                    (UsersTable.id eq userId) and
                            (UsersTable.passwordHash eq expectedPasswordHash)
                }
            ) {
                // Новый хеш пароля.
                it[UsersTable.passwordHash] = passwordHash

                // Одновременно увеличиваем версию сессии.
                it[UsersTable.sessionVersion] =
                    UsersTable.sessionVersion + 1L
            }

            updatedRows == 1
        }
    }

    suspend fun getSessionVersion(
        userId: Int
    ): Long? = withContext(Dispatchers.IO) {

        transaction {

            UsersTable
                .selectAll()
                .where {
                    UsersTable.id eq userId
                }
                .firstOrNull()
                ?.get(UsersTable.sessionVersion)
        }
    }
    suspend fun isPhoneVerified(
        userId: Int
    ): Boolean = withContext(Dispatchers.IO) {

        transaction {
            UsersTable
                .selectAll()
                .where {
                    UsersTable.id eq userId
                }
                .firstOrNull()
                ?.get(UsersTable.phoneVerifiedAt) != null
        }
    }

}