
package com.example.catering.database.table

import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table("users") {

    val id = integer("id").autoIncrement()

    val name = varchar("name", 100)

    val phone = varchar("phone", 20).uniqueIndex()

    val passwordHash = varchar("password_hash", 255)

    val address = varchar("address", 255).default("")

    // Версия сессии пользователя.
    // Увеличивается при смене пароля.
    val sessionVersion = long("session_version").default(0L)

    val email = varchar("email", 254).nullable()

    val emailVerifiedAt = long("email_verified_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
