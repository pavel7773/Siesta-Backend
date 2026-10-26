package com.example.catering.database.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object OrdersTable : IntIdTable("orders") {

    val userId = integer("user_id")
    val idempotencyKey = varchar("idempotency_key", 128).nullable()

    val name = varchar("name", 100)
    val phone = varchar("phone", 20)
    val comment = varchar("comment", 500)
    val deliveryType = varchar("delivery_type", 20)
    val address = varchar("address", 300)
    val totalAmount = long("total_amount")
    val createdAt = long("created_at")
    val statusOrder = varchar("status_order", 20)

    init {
        foreignKey(
            userId to UsersTable.id,
            onDelete = ReferenceOption.RESTRICT
        )
    }

}