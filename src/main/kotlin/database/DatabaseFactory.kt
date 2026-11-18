package com.example.catering.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private const val JDBC_URL = "jdbc:postgresql://localhost:5432/catering"
private const val USER = "postgres"

private data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String
)

object DatabaseFactory {

    private fun config(): DatabaseConfig {
        val dbUser = System.getenv("DB_USER")
            ?.takeIf { it.isNotBlank() }
            ?: USER

        val dbPassword = System.getenv("DB_PASSWORD")
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("CATERING_DB_PASSWORD")
                ?.takeIf { it.isNotBlank() }
            ?: error("Не задан пароль базы данных")

        val dbUrl = System.getenv("DB_URL")
            ?.takeIf { it.isNotBlank() }
            ?: run {
                val host = System.getenv("DB_HOST")?.takeIf { it.isNotBlank() }
                val port = System.getenv("DB_PORT")?.takeIf { it.isNotBlank() } ?: "5432"
                val database = System.getenv("DB_NAME")?.takeIf { it.isNotBlank() }

                if (host != null && database != null) {
                    "jdbc:postgresql://$host:$port/$database"
                } else {
                    JDBC_URL
                }
            }

        return DatabaseConfig(
            url = dbUrl,
            user = dbUser,
            password = dbPassword
        )
    }

    fun init() {
        val config = config()

        Database.connect(
            url = config.url,
            driver = "org.postgresql.Driver",
            user = config.user,
            password = config.password
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
        val config = config()

        Flyway.configure()
            .dataSource(
                config.url,
                config.user,
                config.password
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
