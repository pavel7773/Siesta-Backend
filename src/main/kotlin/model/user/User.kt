package com.example.catering.model.user

data class User(
    val id: Int,
    val name: String,
    val phone: String,
    val passwordHash: String,
    val address: String
)
