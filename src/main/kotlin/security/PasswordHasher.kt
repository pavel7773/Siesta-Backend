package com.example.catering.security
import com.password4j.Argon2Function
import com.password4j.Password
import com.password4j.types.Argon2

class PasswordHasher {

    fun hash(password: String): String {
        return Password.hash(password)
            .addRandomSalt()
            .with(ARGON2_FUNCTION)
            .result
    }

    fun verify(
        password: String,
        passwordHash: String
    ): Boolean {
        return try {
            val hashFunction =
                Argon2Function.getInstanceFromHash(passwordHash)

            Password.check(password, passwordHash)
                .with(hashFunction)
        } catch (_: RuntimeException) {
            false
        }
    }

    private companion object {

        // OWASP minimum recommendation for Argon2id:
        // 19 MiB memory, 2 iterations, parallelism 1.
        private const val MEMORY_KIB = 19 * 1024
        private const val ITERATIONS = 2
        private const val PARALLELISM = 1

        // 256-bit derived hash.
        private const val HASH_LENGTH_BYTES = 32

        private val ARGON2_FUNCTION =
            Argon2Function.getInstance(
                MEMORY_KIB,
                ITERATIONS,
                PARALLELISM,
                HASH_LENGTH_BYTES,
                Argon2.ID
            )
    }
}