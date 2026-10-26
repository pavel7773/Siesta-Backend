
package com.example.catering.catering_test.database

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

object TestDatabaseFactory {

    // Отдельная локальная база для тестов.
    private const val TEST_DB_BASE_URL =
        "jdbc:postgresql://localhost:5432/catering_test"

    // Изолированная схема внутри тестовой базы.
    private const val TEST_SCHEMA =
        "siesta_integration_tests"

    // Все запросы Exposed направляем в тестовую схему.
    private const val TEST_DB_URL =
        "$TEST_DB_BASE_URL?currentSchema=$TEST_SCHEMA"

    private const val TEST_DB_USER = "postgres"

    private fun getPassword(): String =
        System.getenv("TEST_DB_PASSWORD")
            ?: System.getenv("CATERING_DB_PASSWORD")
            ?: error("Не задан пароль тестовой базы данных")

    fun init() {
        Database.connect(
            url = TEST_DB_URL,
            driver = "org.postgresql.Driver",
            user = TEST_DB_USER,
            password = getPassword()
        )
    }

    fun migrate() {
        Flyway.configure()
            .dataSource(
                TEST_DB_URL,
                TEST_DB_USER,
                getPassword()
            )

            // Flyway работает только с тестовой схемой.
            .schemas(TEST_SCHEMA)
            .defaultSchema(TEST_SCHEMA)
            .createSchemas(true)

            // В тестах должны выполняться все миграции,
            // начиная с V1, без пропуска старых версий.
            .baselineOnMigrate(false)

            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
