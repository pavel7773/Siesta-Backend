package com.example.catering.database.table


import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object OrderItemsTable : IntIdTable("order_items") {

    val orderId = integer("order_id")
    val dishId = integer("dish_id")
    val name = varchar("name", 100)
    val quantity = integer("quantity")
    val price = integer("price")

    init {
        foreignKey(
            orderId to OrdersTable.id,
            onDelete = ReferenceOption.CASCADE
        )
    }
}