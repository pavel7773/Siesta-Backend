package com.example.catering.database.table


import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object AccountVerificationCodesTable :
    Table("account_verification_codes") {

    val id = long("id").autoIncrement()

    val userId = integer("user_id")

    val purpose = varchar("purpose", 32)

    val channel = varchar("channel", 16)

    val destination = varchar("destination", 254)

    val codeHash = char("code_hash", 64)

    val attemptsCount = integer("attempts_count")
        .default(0)

    val createdAt = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    val expiresAt = timestampWithTimeZone("expires_at")

    val consumedAt = timestampWithTimeZone("consumed_at")
        .nullable()

    override val primaryKey = PrimaryKey(id)
}
