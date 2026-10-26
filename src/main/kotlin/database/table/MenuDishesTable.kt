package com.example.catering.database.table

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object MenuDishesTable: IntIdTable("menu_dishes") {

    val name = varchar("name", 100)
    val price = integer("price")

    val info = varchar("info", 500).default("")
    val imageUrl = varchar("image_url", 1000).default("")

    val weight = integer("weight").default(0)
    val calories = integer("calories").default(0)

    val proteins = double("proteins").default(0.0)
    val fats = double("fats").default(0.0)
    val carbs = double("carbs").default(0.0)

    val category = varchar("category", 100).default("")
    val isActive = bool("is_active").default(true)
}