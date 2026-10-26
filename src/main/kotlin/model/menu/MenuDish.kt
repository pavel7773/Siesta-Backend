package com.example.catering.model.menu

import kotlinx.serialization.Serializable

@Serializable
data class MenuDish(
    val id: Int,
    val name: String,
    val price: Int,
    val info: String,
    val imageUrl: String,
    val weight: Int,
    val calories: Int,
    val proteins: Double,
    val fats: Double,
    val carbs: Double,
    val category: String
)
