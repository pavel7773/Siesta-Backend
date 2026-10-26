package com.example.catering.catering_test.fake

import com.example.catering.model.menu.MenuDish
import com.example.catering.repository.MenuDishFinder

class FakeMenuDishFinder(private val dish: MenuDish?) : MenuDishFinder {
    override suspend fun findById(id: Int): MenuDish? {
        return dish
    }
}