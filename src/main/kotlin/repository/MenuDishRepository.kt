package com.example.catering.repository

import com.example.catering.database.table.MenuDishesTable
import com.example.catering.model.menu.MenuDish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class MenuDishRepository: MenuDishFinder {

    override suspend fun findById(id: Int): MenuDish? =
        withContext(Dispatchers.IO) {
            transaction {
                val row = MenuDishesTable
                    .selectAll()
                    .where { MenuDishesTable.id eq id }
                    .firstOrNull()
                    ?: return@transaction null

                MenuDish(
                    id = row[MenuDishesTable.id].value,
                    name = row[MenuDishesTable.name],
                    price = row[MenuDishesTable.price],
                    info = row[MenuDishesTable.info],
                    imageUrl = row[MenuDishesTable.imageUrl],
                    weight = row[MenuDishesTable.weight],
                    calories = row[MenuDishesTable.calories],
                    proteins = row[MenuDishesTable.proteins],
                    fats = row[MenuDishesTable.fats],
                    carbs = row[MenuDishesTable.carbs],
                    category = row[MenuDishesTable.category]
                )
            }
        }

    suspend fun getAll(): List<MenuDish> =
        withContext(Dispatchers.IO) {
            transaction {
                MenuDishesTable
                    .selectAll()
                    .where { MenuDishesTable.isActive eq true }
                    .map { row ->
                        MenuDish(
                            id = row[MenuDishesTable.id].value,
                            name = row[MenuDishesTable.name],
                            price = row[MenuDishesTable.price],
                            info = row[MenuDishesTable.info],
                            imageUrl = row[MenuDishesTable.imageUrl],
                            weight = row[MenuDishesTable.weight],
                            calories = row[MenuDishesTable.calories],
                            proteins = row[MenuDishesTable.proteins],
                            fats = row[MenuDishesTable.fats],
                            carbs = row[MenuDishesTable.carbs],
                            category = row[MenuDishesTable.category]
                        )
                    }
            }
        }
}