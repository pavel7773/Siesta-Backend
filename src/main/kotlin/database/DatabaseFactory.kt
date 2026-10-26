package com.example.catering.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private const val JDBC_URL = "jdbc:postgresql://localhost:5432/catering"
private const val USER = "postgres"

object DatabaseFactory {

    fun init() {
        val dbUrl = System.getenv("DB_URL")
            ?: JDBC_URL

        val dbUser = System.getenv("DB_USER")
            ?: USER

        val dbPassword = System.getenv("DB_PASSWORD")
            ?: System.getenv("CATERING_DB_PASSWORD")
            ?: error("Не задан пароль базы данных")

        Database.connect(
            url = dbUrl,
            driver = "org.postgresql.Driver",
            user = dbUser,
            password = dbPassword
        )
    }

    fun checkConnection() {
        transaction {
            exec("SELECT 1") { resultSet ->
                if (resultSet.next()) {
                    val result = resultSet.getInt(1)
                    println("PostgreSQL ответил: $result")
                }
            }
        }
    }

    fun migrate() {
        val dbUrl = System.getenv("DB_URL")
            ?: JDBC_URL

        val dbUser = System.getenv("DB_USER")
            ?: USER

        val dbPassword = System.getenv("DB_PASSWORD")
            ?: System.getenv("CATERING_DB_PASSWORD")
            ?: error("Не задан пароль базы данных")

        Flyway.configure()
            .dataSource(
                dbUrl,
                dbUser,
                dbPassword
            )
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    suspend fun isHealthy(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                transaction {
                    exec("SELECT 1") { resultSet ->
                        resultSet.next() && resultSet.getInt(1) == 1
                    } ?: false
                }
            } catch (e: Exception) {
                false
            }
        }
}
