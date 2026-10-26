package com.example.catering.repository

import com.example.catering.model.menu.MenuDish

interface MenuDishFinder {
    suspend fun findById(id: Int): MenuDish?
}